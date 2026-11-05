package com.sos.overlay

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.Collections

/** One SOS / Fuel / Food / presence message shared with the rider group. */
data class SosPacket(
    val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val ts: Long,
    val hasLoc: Boolean = true
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("n", name).put("k", kind)
        .put("la", lat).put("lo", lon).put("t", ts)
        .put("nl", if (hasLoc) 0 else 1).toString()

    companion object {
        fun parse(s: String): SosPacket? = try {
            val o = JSONObject(s)
            SosPacket(
                o.getString("id"),
                o.optString("n", o.getString("id")),
                o.optString("k", "sos"),
                o.optDouble("la", 0.0),
                o.optDouble("lo", 0.0),
                o.optLong("t", System.currentTimeMillis()),
                o.optInt("nl", 0) == 0
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
 *  - Online:  Firebase Realtime Database (push-based event stream, low latency).
 */
class RiderLink(
    private val ctx: Context,
    private val onPacket: (SosPacket) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onLinked: () -> Unit = {}
) {
    private var nearby: ConnectionsClient? = null
    private val connected: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private var firebase: FirebaseLink? = null

    private fun status(s: String) { LinkStatus.text = s; onStatus(s) }

    fun start(group: String) {
        stop()
        if (Prefs.offline) startNearby(group)
        if (Prefs.online) {
            firebase = FirebaseLink(onPacket, ::status)
            firebase?.start(group)
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
                if (res.status.isSuccess) {
                    connected.add(ep)
                    onLinked()
                }
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
            .addOnFailureListener { status("Nearby: advertise failed (${it.message})") }
        client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnFailureListener { status("Nearby: discovery failed (${it.message})") }
        status("Nearby: searching for riders…")
    }

    // ---------------- send ----------------

    fun send(p: SosPacket) {
        val json = p.toJson()
        val targets = synchronized(connected) { connected.toList() }
        if (targets.isNotEmpty()) {
            try { nearby?.sendPayload(targets, Payload.fromBytes(json.toByteArray())) } catch (e: Exception) {}
        }
        firebase?.send(p)
    }

    fun stop() {
        firebase?.stop()
        firebase = null
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
