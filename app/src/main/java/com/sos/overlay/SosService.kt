package com.sos.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs

/**
 * Draws a floating SOS button above other apps (including Google Maps) and turns a tap into
 * a location capture with "Open in Google Maps" / "Share location" actions.
 */
class SosService : Service() {
    companion object {
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private lateinit var fused: FusedLocationProviderClient
    private var bubble: View? = null
    private var card: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sos", "SOS overlay", NotificationManager.IMPORTANCE_LOW))
        val note: Notification = Notification.Builder(this, "sos")
            .setContentTitle("SOS Overlay")
            .setContentText("Floating SOS button is on")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, note)
            }
        } catch (e: Exception) {
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addBubble() {
        val size = dp(64)
        val tv = TextView(this).apply {
            text = "SOS"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DC2626"))
                setStroke(dp(3), Color.WHITE)
            }
        }

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.x = dp(18)
        lp.y = dp(180)

        tv.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = lp.x; startY = lp.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (abs(dx) > dp(10) || abs(dy) > dp(10)) moved = true
                        lp.x = startX - dx.toInt()
                        lp.y = startY - dy.toInt()
                        wm.updateViewLayout(v, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) sos()
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(tv, lp)
        bubble = tv
    }

    private fun sos() {
        removeCard()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> showCard(loc) }
                .addOnFailureListener { showCard(null) }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission missing", Toast.LENGTH_SHORT).show()
            showCard(null)
        }
    }

    private fun showCard(loc: Location?) {
        removeCard()
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#B42318"))
            }
        }
        box.addView(label("🆘 SOS", 24f, true))
        box.addView(
            label(
                if (loc != null) "%.5f, %.5f".format(loc.latitude, loc.longitude) else "Location not available",
                14f, false
            )
        )
        if (loc != null) {
            box.addView(action("Open in Google Maps") { openMaps(loc) })
            box.addView(action("Share location") { share(loc) })
        }
        box.addView(action("Close") { removeCard() })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        try {
            wm.addView(box, lp)
            card = box
        } catch (e: Exception) {
            // overlay permission was revoked while running
        }
    }

    private fun label(text: String, size: Float, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun action(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#B42318"))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.WHITE)
        }
        setOnClickListener { onClick() }
    }

    private fun mapUri(loc: Location) =
        Uri.parse("https://www.google.com/maps/search/?api=1&query=${loc.latitude},${loc.longitude}")

    private fun openMaps(loc: Location) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, mapUri(loc)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open the map", Toast.LENGTH_SHORT).show()
        }
        removeCard()
    }

    private fun share(loc: Location) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "SOS — my location: ${mapUri(loc)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(send, "Share my location").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Nothing to share with", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeCard() {
        card?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        card = null
    }

    override fun onDestroy() {
        removeCard()
        bubble?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        bubble = null
        running = false
        super.onDestroy()
    }
}
