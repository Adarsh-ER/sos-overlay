package com.sos.overlay

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import com.google.android.gms.location.*
import org.json.JSONObject

/** Foreground service: keeps GPS + radio link alive with the screen off. */
class RideService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private var link: NearbyLink? = null
    private val h = Handler(Looper.getMainLooper())
    private val locCb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { RideState.me.value = it } }
    }
    private val tick = object : Runnable { override fun run() { send(); h.postDelayed(this, 2000) } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("ride", "Ride tracking", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ride").setContentTitle("RidePack").setContentText("Tracking your ride")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, n)
        if (!RideState.running.value) {
            RideState.running.value = true
            fused = LocationServices.getFusedLocationProviderClient(this)
            try { fused.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build(), locCb, Looper.getMainLooper()) }
            catch (e: SecurityException) { RideState.msg.value = "Location permission missing" }
            h.postDelayed(tick, 2000)
        }
        initLink()
        return START_STICKY
    }

    private fun initLink() {
        link?.stop(); link = null
        val c = RideState.code.value ?: return
        link = NearbyLink(this, ::onPacket, { RideState.links.value = it }, { RideState.msg.value = "Radio: $it" }).also { it.start(c, RideState.myId) }
    }

    private fun send() {
        val m = RideState.me.value ?: return
        // sos=1 -> this rider raised an SOS; sos=0 -> plain position packet (also clears if the sender was the active SOS rider)
        val sosFlag = RideState.sos.value?.id == RideState.myId
        link?.send(JSONObject().put("id", RideState.myId).put("n", RideState.myName).put("la", m.latitude).put("lo", m.longitude).put("v", m.speed.toDouble()).put("sos", if (sosFlag) 1 else 0).toString())
    }

    private fun onPacket(s: String) {
        try {
            val o = JSONObject(s); val id = o.getString("id"); if (id == RideState.myId) return
            val name = RideState.known[id] ?: o.optString("n", id)
            if (o.optInt("sos", 0) == 1) {
                RideState.sos.value = Sos(id, name, o.getDouble("la"), o.getDouble("lo"), System.currentTimeMillis())
            } else if (RideState.sos.value?.id == id) {
                RideState.sos.value = null // sender cleared/resolved their SOS
            }
            RideState.riders.value = RideState.riders.value + (id to Rider(id, name, o.getDouble("la"), o.getDouble("lo"), o.optDouble("v", 0.0).toFloat(), System.currentTimeMillis()))
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        if (::fused.isInitialized) fused.removeLocationUpdates(locCb)
        link?.stop(); RideState.running.value = false; RideState.links.value = 0; RideState.sos.value = null
        super.onDestroy()
    }
}
