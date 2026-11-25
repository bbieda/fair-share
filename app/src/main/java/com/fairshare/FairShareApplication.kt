package com.fairshare

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.fairshare.data.AppSettings
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.FairShareDatabase
import com.fairshare.data.sync.FairShareApi
import com.fairshare.data.sync.Session
import com.fairshare.data.sync.SyncManager
import com.fairshare.ui.ensureNotificationChannel

/** Holds the single database, repository and sync instances for the whole app. */
class FairShareApplication : Application() {
    val database: FairShareDatabase by lazy { FairShareDatabase.build(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val session: Session by lazy { Session(this) }
    /** The FairShare server: sync, and the receipt scanner's AI step (the Gemini key lives on the server). */
    val api: FairShareApi by lazy { FairShareApi(BuildConfig.API_BASE_URL) { session.token } }
    val sync: SyncManager by lazy { SyncManager(this, database, session, api, settings) }
    val repository: FairShareRepository by lazy { FairShareRepository(database, onLocalChange = sync::requestSync) }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
        // Changes saved offline go out as soon as the phone is online again
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = sync.requestSync()
            }
        )
    }
}
