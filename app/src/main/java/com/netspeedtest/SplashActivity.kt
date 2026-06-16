package com.netspeedtest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd

class SplashActivity : Activity() {

    companion object {
        private const val TAG = "SplashAd"
        private const val AD_UNIT_ID = "ca-app-pub-1212786513185567/1371799713"
        private const val PREFS_NAME = "netspeed_prefs"
        private const val KEY_PREMIUM = "is_premium"
    }

    private var hasNavigated = false
    private val handler = Handler(Looper.getMainLooper())

    private val timeoutRunnable = Runnable {
        goToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check premium state
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PREMIUM, false)) {
            goToMain()
            return
        }

        handler.postDelayed(timeoutRunnable, 6000)

        MobileAds.initialize(this) {
            loadAd()
        }
    }

    private fun loadAd() {
        try {
            AppOpenAd.load(
                this, AD_UNIT_ID,
                AdRequest.Builder().build(),
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdFailedToLoad(e: com.google.android.gms.ads.LoadAdError) {
                        goToMain()
                    }
                    override fun onAdLoaded(ad: AppOpenAd) {
                        handler.removeCallbacks(timeoutRunnable)
                        showAd(ad)
                    }
                }
            )
        } catch (e: Exception) {
            goToMain()
        }
    }

    private fun showAd(ad: AppOpenAd) {
        try {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { goToMain() }
                override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { goToMain() }
            }
            ad.show(this)
        } catch (e: Exception) {
            goToMain()
        }
    }

    private fun goToMain() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacks(timeoutRunnable)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }
}
