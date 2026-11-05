package com.fairshare

import android.app.Application
import com.fairshare.data.AppSettings
import com.fairshare.data.FairShareRepository
import com.fairshare.data.db.FairShareDatabase
import com.fairshare.ui.ensureNotificationChannel

/** Holds the single database and repository instances for the whole app. */
class FairShareApplication : Application() {
    val database: FairShareDatabase by lazy { FairShareDatabase.build(this) }
    val repository: FairShareRepository by lazy { FairShareRepository(database) }
    val settings: AppSettings by lazy { AppSettings(this) }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }
}
