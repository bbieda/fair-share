<?php
// Copy to ~/fairshare-config.php on the server (NOT into the web folder), chmod 600, and fill it in.
// Never commit the filled-in copy: it holds the database password and the Gemini key.
return [
    'db_host' => 'mysql.agh.edu.pl',
    'db_port' => 3306,
    'db_name' => 'NAZWA_BAZY',
    'db_user' => 'UZYTKOWNIK_BAZY',
    'db_pass' => 'HASLO_BAZY',
    // Receipt scanner AI step (Google AI Studio key). Empty = the scanner uses ML Kit + rules only.
    'gemini_api_key' => '',
    // optional: 'gemini_models' => ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite'],
    // optional: 'gemini_daily_limit' => 60,          // per account
    // optional: 'gemini_global_daily_limit' => 300,  // whole server
];
