package com.starapps.secretcalculatorvault

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var display: TextView
    private val expr = StringBuilder()
    private lateinit var prefs: android.content.SharedPreferences

    private var wrongAttempts = 0
    private var interstitialAd: InterstitialAd? = null
    private var pendingOperator: Char? = null
    private var firstValue: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            this,
            "vault_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        if (!prefs.contains("secret_pin")) {
            prefs.edit().putString("secret_pin", "1234").apply()
        }

        display = findViewById(R.id.display)

        val digitIds = intArrayOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        digitIds.forEachIndexed { index, id ->
            findViewById<Button>(id).setOnClickListener { appendChar(index.toString()) }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { setOperator('+') }
        findViewById<Button>(R.id.btnSubtract).setOnClickListener { setOperator('-') }
        findViewById<Button>(R.id.btnMultiply).setOnClickListener { setOperator('*') }
        findViewById<Button>(R.id.btnDivide).setOnClickListener { setOperator('/') }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { onEquals() }

        loadBanner()
        loadInterstitial()
    }

    private fun appendChar(c: String) {
        expr.append(c)
        display.text = expr.toString()
    }

    private fun clearAll() {
        expr.clear()
        firstValue = null
        pendingOperator = null
        display.text = "0"
    }

    private fun setOperator(op: Char) {
        if (expr.isEmpty()) return
        firstValue = expr.toString().toDoubleOrNull()
        pendingOperator = op
        expr.clear()
    }

    private fun onEquals() {
        val currentInput = expr.toString()
        val savedPin = prefs.getString("secret_pin", "1234")

        if (pendingOperator == null && currentInput.isNotEmpty() && currentInput.all { it.isDigit() }) {
            if (currentInput == savedPin) {
                wrongAttempts = 0
                clearAll()
                showInterstitialThenOpenVault()
                return
            } else {
                wrongAttempts++
                if (wrongAttempts >= 3) {
                    captureIntruderSelfie()
                    wrongAttempts = 0
                }
            }
        }

        if (pendingOperator != null && firstValue != null && currentInput.isNotEmpty()) {
            val second = currentInput.toDoubleOrNull() ?: 0.0
            val result = when (pendingOperator) {
                '+' -> firstValue!! + second
                '-' -> firstValue!! - second
                '*' -> firstValue!! * second
                '/' -> if (second != 0.0) firstValue!! / second else Double.NaN
                else -> second
            }
            val resultText = if (result == result.toLong().toDouble())
                result.toLong().toString() else result.toString()
            display.text = resultText
            expr.clear()
            expr.append(resultText)
            pendingOperator = null
            firstValue = null
        } else {
            display.text = currentInput
        }
    }

    private fun showInterstitialThenOpenVault() {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial()
                    openVault()
                }
                override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                    openVault()
                }
            }
            ad.show(this)
        } else {
            openVault()
        }
    }

    private fun openVault() {
        startActivity(Intent(this, VaultActivity::class.java))
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun loadBanner() {
        val adView = findViewById<AdView>(R.id.adViewMain)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun captureIntruderSelfie() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.CAMERA), 101
            )
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)

                val dir = File(filesDir, "intruder_logs").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val file = File(dir, "intruder_$stamp.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            provider.unbindAll()
                        }
                        override fun onError(exc: ImageCaptureException) {
                            provider.unbindAll()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
