package com.sos.overlay

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online link over the existing Firebase Realtime Database (project ridepack-3d413).
 *
 * Uses anonymous auth + REST with an event stream, and the path rides/<group>/sos which the
 * project's existing rules allow for any signed-in user. Push updates arrive in well under a second.
 */
class FirebaseLink(
    private val onPacket: (SosPacket) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val API_KEY = "AIzaSyCk-ZOSf54DbbV3nZBmSGnK868--MyScQQ"
        private const val DB = "https://ridepack-3d413-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    @Volatile private var running = false
    @Volatile private var stream: HttpURLConnection? = null
    @Volatile private var token: String? = null
    private var group = ""
    private var thread: Thread? = null

    fun start(group: String) {
        stop()
        this.group = group
        running = true
        thread = Thread { loop() }.also { it.start() }
    }

    private fun loop() {
        while (running) {
            try {
                val t = token ?: fetchToken().also { token = it }
                val c = URL("$DB/rides/$group/sos.json?auth=$t").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "text/event-stream")
                c.connectTimeout = 15000
                c.readTimeout = 0
                stream = c
                onStatus("Online: listening")
                var event = ""
                c.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        when {
                            line.startsWith("event:") -> event = line.substring(6).trim()
                            line.startsWith("data:") -> {
                                val data = line.substring(5).trim()
                                if (event == "put" || event == "patch") handle(data)
                            }
                        }
                    }
                }
                onStatus("Online: reconnecting")
            } catch (e: Exception) {
                if (running) onStatus("Online: waiting for internet")
                token = null
            } finally {
                try { stream?.disconnect() } catch (e: Exception) {}
                stream = null
            }
            if (running) try { Thread.sleep(2000) } catch (e: InterruptedException) { return }
        }
    }

    /** The first event is the full history snapshot (path "/") — ignore it, only live children matter. */
    private fun handle(data: String) {
        try {
            val o = JSONObject(data)
            val path = o.optString("path", "")
            if (path.isEmpty() || path == "/") return
            val v = o.opt("data")
            val p = when (v) {
                is String -> SosPacket.parse(v)
                is JSONObject -> SosPacket.parse(v.toString())
                else -> null
            }
            if (p != null) {
                Log.d("SosOverlay", "rx ${p.kind} from ${p.id} latency=${System.currentTimeMillis() - p.ts}ms")
                onPacket(p)
            }
        } catch (e: Exception) {}
    }

    fun send(p: SosPacket) {
        val t = token ?: return
        Thread {
            try {
                val c = URL("$DB/rides/$group/sos.json?auth=$t").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(p.toJson().toByteArray()) }
                Log.d("SosOverlay", "tx ${p.kind} code=${c.responseCode}")
            } catch (e: Exception) {
                Log.d("SosOverlay", "tx failed: ${e.message}")
            }
        }.start()
    }

    private fun fetchToken(): String {
        val c = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write("{\"returnSecureToken\":true}".toByteArray()) }
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        return o.getString("idToken")
    }

    fun stop() {
        running = false
        try { stream?.disconnect() } catch (e: Exception) {}
        stream = null
        thread?.interrupt()
        thread = null
        token = null
    }
}
