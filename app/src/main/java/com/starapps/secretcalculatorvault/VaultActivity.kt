package com.starapps.secretcalculatorvault

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

class VaultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        val adView = findViewById<AdView>(R.id.adViewVaultBanner)
        adView.loadAd(AdRequest.Builder().build())
    }
}
