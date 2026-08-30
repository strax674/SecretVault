package com.starapps.secretcalculatorvault

import android.app.Application
import com.google.android.gms.ads.MobileAds

class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
    }
}
