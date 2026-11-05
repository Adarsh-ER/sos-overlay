package com.sos.overlay

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** One SOS / Fuel / Food message shared with the rider group. */
data class SosPacket(
    val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val ts: Long
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("n", name).put("k", kind)
        .put("la", lat).put("lo", lon).put("t", ts).toString()

    companion object {
        fun parse(s: String): SosPacket? = try {
            val o = JSONObject(s)
            SosPacket(
                o.getString("id"),
                o.optString("n", o.getString("id")),
                o.optString("k", "sos"),
                o.getDouble("la"),
                o.getDouble("lo"),
                o.optLong("t", System.currentTimeMillis())
            )
        } catch (e: Exception) { null }
    }
}

object LinkStatus {
    @Volatile var text: String = "Idle"
}

/**
 * Shares SOS packets with the riders in the same group.
 *  - Offline: Google Nearby Connections over Bluetooth/Wi-Fi (works with no data).
 *  - Online:  a public ntfy.sh topic derived from the group code (works over the internet).
 */
class RiderLink(
    private val ctx: Context,
    private val onPacket: (SosPacket) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var nearby: ConnectionsClient? = null
    private val connected: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Volatile private var online = false
    @Volatile private var topic = ""
    @Volatile private var stream: HttpURLConnection? = null
    private var onlineThread: Thread? = null

    private fun status(s: String) { LinkStatus.text = s; onStatus(s) }

    fun start(group: String) {
        stop()
        topic = topicName(group)
        if (Prefs.offline) startNearby(group)
        if (Prefs.online) {
            online = true
            onlineThread = Thread { onlineLoop() }.also { it.start() }
        }
    }

    // ---------------- offline (Bluetooth / Wi-Fi, no internet) ----------------

    private fun startNearby(group: String) {
        val client = Nearby.getConnectionsClient(ctx)
        nearby = client

        val payload = object : PayloadCallback() {
            override fun onPayloadReceived(ep: String, p: Payload) {
                p.asBytes()?.let { bytes -> SosPacket.parse(String(bytes))?.let(onPacket) }
            }
            override fun onPayloadTransferUpdate(ep: String, u: PayloadTransferUpdate) {}
        }
        val life = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(ep: String, info: ConnectionInfo) { client.acceptConnection(ep, payload) }
            override fun onConnectionResult(ep: String, res: ConnectionResolution) {
                if (res.status.isSuccess) connected.add(ep)
                status("Nearby: ${connected.size} rider(s) linked")
            }
            override fun onDisconnected(ep: String) {
                connected.remove(ep)
                status("Nearby: ${connected.size} rider(s) linked")
            }
        }
        val discovery = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(ep: String, info: DiscoveredEndpointInfo) {
                // the smaller rider ID initiates, so both phones never connect at once
                if (Prefs.id < info.endpointName && !connected.contains(ep)) client.requestConnection(Prefs.id, ep, life)
            }
            override fun onEndpointLost(ep: String) {}
        }

        val serviceId = "com.sos.overlay.$group"
        client.startAdvertising(Prefs.id, serviceId, life, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
        client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
        status("Nearby: searching for riders…")
    }

    // ---------------- online (internet) ----------------

    private fun topicName(group: String): String {
        val clean = group.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "sos1" }
        return "sosoverlay-$clean"
    }

    private fun onlineLoop() {
        while (online) {
            try {
                val c = URL("https://ntfy.sh/$topic/json").openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 0
                c.setRequestProperty("Accept", "application/json")
                stream = c
                status("Online: listening")
                c.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!online) break
                        val msg = try { JSONObject(line).optString("message", "") } catch (e: Exception) { "" }
                        if (msg.isNotBlank()) SosPacket.parse(msg)?.let(onPacket)
                    }
                }
                status("Online: reconnecting…")
            } catch (e: Exception) {
                if (online) status("Online: waiting for internet")
            } finally {
                try { stream?.disconnect() } catch (e: Exception) {}
                stream = null
            }
            if (online) try { Thread.sleep(3000) } catch (e: InterruptedException) { return }
        }
    }

    private fun publish(p: SosPacket) {
        try {
            val c = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(p.toJson().toByteArray()) }
            c.responseCode
            status("Online: sent")
        } catch (e: Exception) {
            status("Online: could not send")
        }
    }

    // ---------------- send ----------------

    fun send(p: SosPacket) {
        val json = p.toJson()
        val targets = synchronized(connected) { connected.toList() }
        if (targets.isNotEmpty()) {
            try { nearby?.sendPayload(targets, Payload.fromBytes(json.toByteArray())) } catch (e: Exception) {}
        }
        if (online) Thread { publish(p) }.start()
    }

    fun stop() {
        online = false
        try { stream?.disconnect() } catch (e: Exception) {}
        stream = null
        onlineThread?.interrupt()
        onlineThread = null
        try {
            nearby?.stopAdvertising()
            nearby?.stopDiscovery()
            nearby?.stopAllEndpoints()
        } catch (e: Exception) {}
        nearby = null
        connected.clear()
        status("Offline")
    }
}
