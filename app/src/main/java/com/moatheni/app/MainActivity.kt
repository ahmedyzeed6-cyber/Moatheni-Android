package com.moatheni.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }
        web.addJavascriptInterface(AndroidBridge(this, web), "Android")
        web.loadUrl("file:///android_asset/index.html")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION), locationRequest
            )
        }
    }

    class AndroidBridge(private val activity: Activity, private val web: WebView) {
        @JavascriptInterface fun openDndSettings() {
            activity.runOnUiThread {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        @JavascriptInterface fun hasDndAccess(): Boolean {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.isNotificationPolicyAccessGranted
        }
        @JavascriptInterface fun setSilentMode(enable: Boolean): Boolean {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return false
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            return true
        }
        @JavascriptInterface fun getLastKnownLocation() {
            activity.runOnUiThread {
                try {
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                        web.evaluateJavascript("window.onNativeLocation(null)", null)
                        return@runOnUiThread
                    }
                    val lm = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val loc: Location? = lm.getProviders(true).asSequence()
                        .mapNotNull { provider -> try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null } }
                        .maxByOrNull { it.time }
                    if (loc == null) web.evaluateJavascript("window.onNativeLocation(null)", null)
                    else web.evaluateJavascript("window.onNativeLocation({lat:${loc.latitude},lon:${loc.longitude}})", null)
                } catch (_: Exception) {
                    web.evaluateJavascript("window.onNativeLocation(null)", null)
                }
            }
        }
    }
}
