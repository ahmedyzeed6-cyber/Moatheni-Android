package com.moatheni.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val locationRequest = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setGeolocationEnabled(true)
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, true, false)
            }
        }
        web.addJavascriptInterface(AndroidBridge(this, web), "Android")
        web.loadUrl("file:///android_asset/index.html")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequest && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            if (::web.isInitialized) web.postDelayed({ web.evaluateJavascript("if(window.Android){Android.getLastKnownLocation()}", null) }, 500)
        }
    }

    class AndroidBridge(private val activity: Activity, private val web: WebView) {
        @JavascriptInterface fun openDndSettings() {
            activity.runOnUiThread { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }
        @JavascriptInterface fun hasDndAccess(): Boolean {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.isNotificationPolicyAccessGranted
        }
        @JavascriptInterface fun setSilentMode(enable: Boolean): Boolean {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return false
            nm.setInterruptionFilter(if (enable) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }

        // Keep the old JS bridge name, but request a fresh fix instead of returning cached coordinates.
        @JavascriptInterface fun getLastKnownLocation() {
            activity.runOnUiThread {
                try {
                    val fine = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fine && !coarse) {
                        web.evaluateJavascript("window.onNativeLocation(null)", null)
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 41)
                        return@runOnUiThread
                    }
                    val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
                    if (providers.isEmpty()) {
                        web.evaluateJavascript("window.onNativeLocation(null)", null)
                        return@runOnUiThread
                    }
                    var delivered = false
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (delivered) return
                            val fresh = System.currentTimeMillis() - location.time <= 120_000L
                            val accurate = location.hasAccuracy() && location.accuracy <= 1500f
                            if (fresh && accurate) {
                                delivered = true
                                try { manager.removeUpdates(this) } catch (_: Exception) {}
                                web.evaluateJavascript("window.onNativeLocation({lat:${location.latitude},lon:${location.longitude},accuracy:${location.accuracy}})", null)
                            }
                        }
                        @Deprecated("Deprecated in Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    providers.forEach { provider ->
                        try { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) } catch (_: Exception) {}
                    }
                    activity.window.decorView.postDelayed({
                        if (!delivered) {
                            try { manager.removeUpdates(listener) } catch (_: Exception) {}
                            web.evaluateJavascript("window.onNativeLocation(null)", null)
                        }
                    }, 25_000)
                } catch (_: Exception) {
                    web.evaluateJavascript("window.onNativeLocation(null)", null)
                }
            }
        }
    }
}
