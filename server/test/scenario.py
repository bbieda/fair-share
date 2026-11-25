"""Two people sharing a group through server/api/api.php, end to end, on an empty database.

How to run it locally with Docker: docs/chmura.md, section "Test serwera".
Usage: python scenario.py http://localhost:8089/fairshare/api.php
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

URL = sys.argv[1]
failures = []


def call(action, body=None, token=None, method="POST"):
    data = None if method == "GET" else json.dumps(body or {}).encode()
    req = urllib.request.Request(f"{URL}?action={action}", data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("X-FairShare-Token", token)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        text = e.read()
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, {"raw": text.decode(errors="replace")}


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    if not cond:
        failures.append(name)


def uid():
    return str(uuid.uuid4())


op_counter = [0]


def op(type_, data):
    op_counter[0] += 1
    return {"op_id": op_counter[0], "type": type_, "data": data}


s, r = call("ping", method="GET")
check("ping", s == 200 and r.get("tables") == 13, r)
check("ping does not reveal versions", "php" not in r, r)

s, a = call("register", {"email": "Ala@Example.com", "password": "haslo1234", "name": "Ala"})
check("register A", s == 200 and a["ok"] and a["email"] == "ala@example.com", a)
s, b = call("register", {"email": "kasia@example.com", "password": "haslo1234", "name": "Kasia K"})
check("register B", s == 200, b)
s, r = call("register", {"email": "ala@example.com", "password": "haslo1234", "name": "X"})
check("duplicate email -> 409", s == 409, (s, r))
s, r = call("register", {"email": "x@example.com", "password": "short", "name": "X"})
check("short password -> 400", s == 400, (s, r))
s, r = call("login", {"email": "ala@example.com", "password": "wrong-pass"})
check("wrong password -> 401", s == 401, (s, r))
s, r = call("login", {"email": "ALA@example.com ", "password": "haslo1234"})
check("login A (case/space-insensitive email)", s == 200 and r["person"]["id"] == a["person"]["id"], r)
s, r = call("pull", {"since": 0}, token="0" * 64)
check("bad token -> 401", s == 401, (s, r))

A, B = a["token"], b["token"]
me_a, me_b = a["person"]["id"], b["person"]["id"]

# Device B2: Kasia logged in on a second phone before joining anything
s, b2pull = call("pull", {"since": 0}, B)
check("B2 initial pull: only herself", s == 200 and len(b2pull["persons"]) == 1 and not b2pull["groups"], b2pull)
b2_cursor = b2pull["cursor"]

g, k, e1, e2, e3 = uid(), uid(), uid(), uid(), uid()
ops_a = [
    op("create_group", {"id": g, "name": "Mieszkanie", "invite_code": "ABCD2345", "created_at": 1}),
    op("add_person", {"group_id": g, "person_id": k, "name": "Kasia"}),
    op("add_expense", {
        "id": e1, "group_id": g, "payer_id": me_a, "amount": 10000, "description": "Zakupy", "category": "GROCERIES",
        "day": 20000, "is_settlement": False, "created_at": 2,
        "shares": [{"person_id": me_a, "amount": 5000}, {"person_id": k, "amount": 5000}],
        "items": [
            {"name": "Mleko", "price": 4000, "position": 0, "shares": [{"person_id": me_a, "amount": 2000}, {"person_id": k, "amount": 2000}]},
            {"name": "Ser", "price": 6000, "position": 1, "shares": [{"person_id": me_a, "amount": 3000}, {"person_id": k, "amount": 3000}]},
        ],
    }),
    op("add_activity", {"id": uid(), "group_id": g, "type": "ADD_EXPENSE", "description": "Dodano wydatek",
                        "involves": [me_a, k], "created_at": 3}),
]
s, r = call("push", {"ops": ops_a}, A)
check("A push group+placeholder+expense", s == 200 and all(x["ok"] for x in r["results"]), r)
s, r = call("push", {"ops": ops_a}, A)
check("A push again is idempotent", s == 200 and all(x["ok"] for x in r["results"]), r)

s, pa = call("pull", {"since": 0}, A)
check("A full pull", s == 200 and len(pa["groups"]) == 1 and len(pa["expenses"]) == 1 and len(pa["members"]) == 2, pa)
check("invite code kept", pa["groups"][0]["invite_code"] == "ABCD2345", pa["groups"])
check("expense has items", len(pa["expenses"][0]["items"]) == 2, pa["expenses"])
check("activity once", len(pa["activity"]) == 1, pa["activity"])
a_cursor = pa["cursor"]

s, r = call("pull", {"since": a_cursor}, A)
check("A pull with nothing new", s == 200 and not r["full"] and not r["expenses"] and not r["groups"], r)

# Kasia's rules: she is not in the group yet
s, r = call("push", {"ops": [op("add_expense", {
    "id": uid(), "group_id": g, "payer_id": me_b, "amount": 100, "description": "x", "category": None, "day": 1,
    "is_settlement": False, "created_at": 1, "shares": [{"person_id": me_b, "amount": 100}], "items": []})]}, B)
check("non-member expense rejected", s == 200 and r["results"][0]["ok"] is False, r)

s, r = call("join_preview", {"code": "abcd-2345"}, B)
check("join preview with dash/lowercase", s == 200 and r["placeholders"] == [{"id": k, "name": "Kasia"}] and not r["already_member"], r)
s, r = call("join_preview", {"code": "ZZZZ9999"}, B)
check("unknown code -> 404", s == 404, (s, r))
s, r = call("join", {"code": "ABCD2345", "claim_person_id": k}, B)
check("B joins as placeholder Kasia", s == 200 and r["group_id"] == g, r)
s, r = call("join", {"code": "ABCD2345", "claim_person_id": k}, B)
check("join again is harmless", s == 200, r)

s, pa2 = call("pull", {"since": a_cursor}, A)
exp = pa2["expenses"][0] if pa2["expenses"] else {}
check("A sees shares moved to B", sorted(x["person_id"] for x in exp.get("shares", [])) == sorted([me_a, me_b]), pa2)
check("A sees item shares moved to B", all(
    sorted(x["person_id"] for x in it["shares"]) == sorted([me_a, me_b]) for it in exp.get("items", [])), exp)
mem = {(m["person_id"], m["deleted"]) for m in pa2["members"]}
check("A sees placeholder left, B joined", (k, True) in mem and (me_b, False) in mem, pa2["members"])
check("A gets B's person row", any(p["id"] == me_b and p["has_account"] for p in pa2["persons"]), pa2["persons"])
check("A gets join activity", any(x["type"] == "JOIN_GROUP" for x in pa2["activity"]), pa2["activity"])
a_cursor = pa2["cursor"]

s, pb = call("pull", {"since": 0}, B)
check("B full pull sees group and expense", len(pb["groups"]) == 1 and len(pb["expenses"]) == 1, pb)
b_cursor = pb["cursor"]

s, r = call("pull", {"since": b2_cursor}, B)
check("B2 (old cursor, joined on another phone) gets a full pull", r.get("full") is True and len(r["groups"]) == 1, r)

# Ala was offline: she still uses the placeholder id; the server maps it to Kasia's account
s, r = call("push", {"ops": [op("add_expense", {
    "id": e2, "group_id": g, "payer_id": k, "amount": 3001, "description": "Pizza", "category": "FOOD", "day": 20001,
    "is_settlement": False, "created_at": 5,
    "shares": [{"person_id": me_a, "amount": 1500}, {"person_id": k, "amount": 1501}], "items": []})]}, A)
check("offline expense naming the placeholder accepted", r["results"][0]["ok"] is True, r)

bad = [
    ("shares do not sum", {"amount": 100, "shares": [{"person_id": me_a, "amount": 99}]}),
    ("zero share", {"amount": 100, "shares": [{"person_id": me_a, "amount": 100}, {"person_id": me_b, "amount": 0}]}),
    ("stranger in shares", {"amount": 100, "shares": [{"person_id": uid(), "amount": 100}]}),
    ("items do not match shares", {"amount": 100, "shares": [{"person_id": me_a, "amount": 100}],
                                   "items": [{"name": "a", "price": 100, "position": 0, "shares": [{"person_id": me_b, "amount": 100}]}]}),
]
for name, patch in bad:
    data = {"id": uid(), "group_id": g, "payer_id": me_a, "description": "x", "category": None, "day": 1,
            "is_settlement": False, "created_at": 1, "items": []}
    data.update(patch)
    s, r = call("push", {"ops": [op("add_expense", data)]}, A)
    check("rejected: " + name, r["results"][0]["ok"] is False, r)

s, r = call("push", {"ops": [op("delete_expense", {"id": e1}), op("delete_expense", {"id": e1})]}, B)
check("B deletes expense (twice, idempotent)", all(x["ok"] for x in r["results"]), r)
s, r = call("push", {"ops": [op("add_expense", {
    "id": e1, "group_id": g, "payer_id": me_a, "amount": 1, "description": "x", "category": None, "day": 1,
    "is_settlement": False, "created_at": 1, "shares": [{"person_id": me_a, "amount": 1}], "items": []})]}, A)
check("re-sent add of a deleted expense does not revive it", r["results"][0]["ok"] is True, r)

s, pa3 = call("pull", {"since": a_cursor}, A)
by_id = {x["id"]: x for x in pa3["expenses"]}
check("A sees tombstone for deleted expense", by_id.get(e1, {}).get("deleted") is True, pa3["expenses"])
check("A sees pizza with Kasia's account", sorted(x["person_id"] for x in by_id.get(e2, {}).get("shares", [])) == sorted([me_a, me_b])
      and by_id[e2]["payer_id"] == me_b, by_id.get(e2))

s, r = call("push", {"ops": [
    op("update_profile", {"name": "Kasia Kowalska", "payment_details": "BLIK 500", "default_currency": "PLN"}),
    op("update_group", {"id": g, "name": "Mieszkanie 2", "archived": False}),
    op("update_person", {"person_id": me_a, "payment_details": "hack"}),
    op("unknown_op", {}),
]}, B)
res = [x["ok"] for x in r["results"]]
check("profile+rename ok, editing an account's details and unknown op rejected", res == [True, True, False, False], r)

s, pa4 = call("pull", {"since": pa3["cursor"]}, A)
check("A sees B's new name and group rename",
      any(p["name"] == "Kasia Kowalska" and p["payment_details"] == "BLIK 500" for p in pa4["persons"])
      and pa4["groups"] and pa4["groups"][0]["name"] == "Mieszkanie 2", pa4)

# Personal entries stay private
s, r = call("push", {"ops": [op("add_activity", {"id": uid(), "group_id": None, "type": "PROFILE_UPDATE",
                                                  "description": "secret", "involves": [], "created_at": 9})]}, B)
s, pa5 = call("pull", {"since": 0}, A)
check("B's personal activity not visible to A", all(x["description"] != "secret" for x in pa5["activity"]), pa5["activity"])

s, r = call("gemini", {"model": "gemini-3.5-flash-lite", "request_json": "{}"})
check("gemini needs login", s == 401, (s, r))
s, r = call("gemini", {"model": "some-other-model", "request_json": "{}"}, A)
check("gemini: unknown model refused (or AI off)", s in (400, 503), (s, r))

s, r = call("logout", {}, B)
s, r = call("pull", {"since": 0}, B)
check("token dead after logout", s == 401, (s, r))

# Guessing a password: after 10 wrong tries in 15 minutes even the right one is refused for a while
call("register", {"email": "lock@example.com", "password": "haslo1234", "name": "Lock"})
for _ in range(10):
    call("login", {"email": "lock@example.com", "password": "wrong-pass"})
s, r = call("login", {"email": "lock@example.com", "password": "haslo1234"})
check("login locked after 10 wrong passwords", s == 429, (s, r))

# New accounts: at most 5 per hour from one address (A, B and lock@ are already 3)
codes = [call("register", {"email": f"spam{i}@example.com", "password": "haslo1234", "name": "S"})[0] for i in range(4)]
check("6th new account from one address refused", codes[:2] == [200, 200] and codes[2:] == [429, 429], codes)

print()
print("ALL PASSED" if not failures else f"{len(failures)} FAILED: {failures}")
sys.exit(1 if failures else 0)
