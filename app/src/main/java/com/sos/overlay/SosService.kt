package com.sos.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs

/**
 * Floating SOS button above other apps (including Google Maps).
 * Tap -> SOS (shares your location), Fuel / Food (reminder to the group, no location).
 * Shared over Bluetooth/Wi-Fi (offline) and the internet (online). Popups raise a notification
 * and auto-close after 5 seconds.
 */
class SosService : Service() {
    companion object {
        @Volatile var running = false
        private const val POPUP_MS = 5000L
    }

    private lateinit var wm: WindowManager
    private lateinit var fused: FusedLocationProviderClient
    private var bubble: View? = null
    private var card: View? = null
    private var link: RiderLink? = null
    private var startedGroup: String? = null
    private var autoClose: Runnable? = null
    @Volatile private var lastLoc: Location? = null

    private val locCb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            r.lastLocation?.let { lastLoc = it }
        }
    }
    private val main = android.os.Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        RiderBook.init(this)
        wm = getSystemService(WindowManager::class.java)
        fused = LocationServices.getFusedLocationProviderClient(this)
        try {
            fused.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build(),
                locCb, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sos", "SOS overlay", NotificationManager.IMPORTANCE_LOW))
        val note: Notification = Notification.Builder(this, "sos")
            .setContentTitle("SOS Overlay")
            .setContentText("Floating button on · group ${Prefs.group}")
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

        if (link == null || startedGroup != Prefs.group) {
            link?.stop()
            link = RiderLink(this, ::onPacket, { }, onLinked = { sendHello() })
            link?.start(Prefs.group)
            startedGroup = Prefs.group
            main.removeCallbacks(beat)
            main.post(beat)
        }
        return START_STICKY
    }

    /** Presence heartbeat: lets every rider in the group see every other rider on both phones. */
    private val beat = object : Runnable {
        override fun run() {
            sendHello()
            main.postDelayed(this, 30000)
        }
    }

    private fun sendHello() {
        link?.send(SosPacket(Prefs.id, Prefs.name, "hello", 0.0, 0.0, System.currentTimeMillis()))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------- floating bubble ----------------

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
                        if (!moved) showPicker()
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(tv, lp)
        bubble = tv
    }

    // ---------------- sending ----------------

    private fun showPicker() {
        val box = cardBox()
        box.addView(label("Share with your riders", 18f, true))
        box.addView(label("Group ${Prefs.group} · ${Prefs.name}", 12f, false))
        box.addView(action("🆘  SOS — share my location") { captureAndSend("sos") })
        box.addView(action("⛽  Fuel — remind the group") { sendReminder("fuel") })
        box.addView(action("🍴  Food — remind the group") { sendReminder("food") })
        box.addView(action("Cancel") { removeCard() })
        show(box)
    }

    private fun captureAndSend(kind: String) {
        removeCard()
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> lastLoc = loc; send(kind, loc) }
                .addOnFailureListener { send(kind, lastLoc) }
        } catch (e: SecurityException) {
            send(kind, lastLoc)
        }
    }

    /** Fuel / Food are reminders only — no location is captured or shown. */
    private fun sendReminder(kind: String) {
        val p = SosPacket(Prefs.id, Prefs.name, kind, 0.0, 0.0, System.currentTimeMillis())
        link?.send(p)
        val box = cardBox()
        box.addView(label("${emoji(kind)} ${title(kind)} sent", 20f, true))
        box.addView(label(reminderText(kind), 14f, false))
        box.addView(label("Shared with group ${Prefs.group}", 13f, false))
        box.addView(action("Close") { removeCard() })
        show(box)
    }

    private fun send(kind: String, loc: Location?) {
        if (loc == null) {
            val box = cardBox()
            box.addView(label("${emoji(kind)} ${title(kind)}", 20f, true))
            box.addView(label("No GPS fix yet — wait a moment and try again.", 14f, false))
            box.addView(action("OK") { removeCard() })
            show(box)
            return
        }
        val p = SosPacket(Prefs.id, Prefs.name, kind, loc.latitude, loc.longitude, System.currentTimeMillis())
        link?.send(p)

        val box = cardBox()
        box.addView(label("${emoji(kind)} ${title(kind)} sent", 20f, true))
        box.addView(label("Shared with group ${Prefs.group}", 13f, false))
        box.addView(label("%.5f, %.5f".format(loc.latitude, loc.longitude), 13f, false))
        box.addView(action("Open in Google Maps") { openMaps(loc) })
        box.addView(action("Share location") { share(loc) })
        box.addView(action("Close") { removeCard() })
        show(box)
    }

    // ---------------- receiving ----------------

    private fun onPacket(p: SosPacket) {
        if (p.id == Prefs.id) return
        if (p.kind == "hello") {
            // A rider joined the group: show them on this phone too.
            RiderBook.see(p.id, p.name)
            return
        }
        RiderBook.see(p.id, p.name)
        // Nearby/online callbacks can arrive on a background thread; overlays must be touched on the main thread.
        main.post { showIncoming(p) }
    }

    private fun showIncoming(p: SosPacket) {
        if (p.kind == "sos") showSosIncoming(p) else showReminderIncoming(p)
    }

    /** Fuel / Food are reminders: notification + popup, no location. */
    private fun showReminderIncoming(p: SosPacket) {
        val name = RiderBook.nameFor(p.id) ?: p.name
        notify("${emoji(p.kind)} ${title(p.kind)}", "$name · group ${Prefs.group}")
        val box = cardBox()
        box.addView(label("${emoji(p.kind)} ${title(p.kind)} — $name", 20f, true))
        box.addView(label(reminderText(p.kind), 14f, false))
        box.addView(label("Rider ${p.id} · group ${Prefs.group}", 12f, false))
        box.addView(action("Close") { removeCard() })
        show(box)
    }

    private fun showSosIncoming(p: SosPacket) {
        val name = RiderBook.nameFor(p.id) ?: p.name
        val target = Location("gps").apply { latitude = p.lat; longitude = p.lon }
        val dist = lastLoc?.distanceTo(target)
        val away = dist?.let { " · " + "%.1f".format(it / 1000.0) + " km away" } ?: ""
        notify("🆘 SOS", "$name needs help$away")
        val box = cardBox()
        box.addView(label("🆘 SOS — $name", 20f, true))
        box.addView(
            label(
                if (dist != null) "%.1f km away · rider ${p.id} · %.5f, %.5f".format(dist / 1000.0, p.lat, p.lon)
                else "rider ${p.id} · %.5f, %.5f".format(p.lat, p.lon),
                13f, false
            )
        )
        box.addView(action("Open in Google Maps") { openMaps(target) })
        box.addView(action("Share location") { share(target) })
        box.addView(action("Close") { removeCard() })
        show(box)
    }

    // ---------------- overlay card helpers ----------------

    private fun cardBox(): LinearLayout {
        val pad = dp(20)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#B42318"))
            }
        }
    }

    private fun show(box: View) {
        removeCard()
        // Cap the popup width so it stays a card in both portrait and landscape (not full-screen).
        val dm = resources.displayMetrics
        val width = minOf(dm.widthPixels - dp(32), dp(380)).coerceAtLeast(dp(260))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        try {
            wm.addView(box, lp)
            card = box
            // Every popup is visible for 5 seconds and then closes on its own.
            val r = Runnable { removeCard() }
            autoClose = r
            main.postDelayed(r, POPUP_MS)
        } catch (e: Exception) {
            // overlay permission revoked
        }
    }

    private fun removeCard() {
        autoClose?.let { main.removeCallbacks(it) }
        autoClose = null
        card?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        card = null
    }

    /** Shows a system notification for an incoming rider alert / popup. */
    private fun notify(title: String, text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("alerts", "Rider alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, "alerts")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: Exception) {
            // notifications not allowed — the popup still shows
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

    private fun emoji(kind: String) = when (kind) {
        "fuel" -> "⛽"
        "food" -> "🍴"
        else -> "🆘"
    }

    private fun title(kind: String) = when (kind) {
        "fuel" -> "Fuel break"
        "food" -> "Food break"
        else -> "SOS"
    }

    private fun reminderText(kind: String) = when (kind) {
        "fuel" -> "Time for a fuel break — top up and regroup."
        "food" -> "Time for a food break — stop and eat together."
        else -> ""
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
            putExtra(Intent.EXTRA_TEXT, "My location: ${mapUri(loc)}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(send, "Share my location").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Nothing to share with", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        removeCard()
        main.removeCallbacks(beat)
        bubble?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        bubble = null
        try { fused.removeLocationUpdates(locCb) } catch (e: Exception) {}
        link?.stop()
        link = null
        startedGroup = null
        running = false
        super.onDestroy()
    }
}
