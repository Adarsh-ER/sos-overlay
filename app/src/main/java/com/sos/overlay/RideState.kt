package com.sos.overlay

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Xml
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

data class Poi(val id: String, val name: String, val cat: String, val lat: Double, val lon: Double)
data class Rider(val id: String, val name: String, val lat: Double, val lon: Double, val speed: Float, val seen: Long)
data class Sos(val id: String, val name: String, val lat: Double, val lon: Double, val seen: Long)

object RideState {
    val me = MutableStateFlow<Location?>(null)
    val riders = MutableStateFlow<Map<String, Rider>>(emptyMap())
    val pois = MutableStateFlow<List<Poi>>(emptyList())
    val route = MutableStateFlow<Route?>(null)
    val code = MutableStateFlow<String?>(null)
    val links = MutableStateFlow(0)
    val running = MutableStateFlow(false)
    val msg = MutableStateFlow("")
    val sos = MutableStateFlow<Sos?>(null)
    var myId = ""
    var myName = "Me"
    val known = mutableMapOf<String, String>() // rider ID -> tagged name
}

object Store {
    const val DEFAULT_STYLE = "https://demotiles.maplibre.org/style.json"
    private lateinit var sp: SharedPreferences
    private lateinit var dir: File

    fun init(c: Context) {
        sp = c.getSharedPreferences("rp", 0); dir = c.filesDir
        if (sp.getString("id", null) == null) {
            val al = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
            sp.edit().putString("id", (1..4).map { al.random() }.joinToString("")).apply()
        }
        RideState.myId = sp.getString("id", "")!!
        RideState.myName = sp.getString("name", "Me")!!
        try { val k = JSONObject(sp.getString("known", "{}")!!); k.keys().forEach { RideState.known[it] = k.getString(it) } } catch (e: Exception) {}
        try {
            val f = File(dir, "pois.json")
            if (f.exists()) { val a = JSONArray(f.readText()); RideState.pois.value = (0 until a.length()).map { val o = a.getJSONObject(it); Poi(o.getString("id"), o.getString("name"), o.getString("cat"), o.getDouble("lat"), o.getDouble("lon")) } }
        } catch (e: Exception) {}
    }
    var style: String
        get() = sp.getString("style", DEFAULT_STYLE)!!
        set(v) { sp.edit().putString("style", v).apply() }
    fun saveName(n: String) { RideState.myName = n; sp.edit().putString("name", n).apply() }
    fun saveKnown() { sp.edit().putString("known", JSONObject(RideState.known as Map<*, *>).toString()).apply() }
    fun savePois() {
        val a = JSONArray(); RideState.pois.value.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("cat", it.cat).put("lat", it.lat).put("lon", it.lon)) }
        File(dir, "pois.json").writeText(a.toString())
    }
}

object Gpx {
    fun parse(s: InputStream): Pair<List<P>, List<Poi>> {
        val pts = ArrayList<P>(); val wps = ArrayList<Poi>()
        val x = Xml.newPullParser(); x.setInput(s, null)
        var e = x.eventType; var wla = 0.0; var wlo = 0.0; var inW = false; var nm = ""
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG) when (x.name) {
                "trkpt", "rtept" -> pts.add(P(x.getAttributeValue(null, "lat").toDouble(), x.getAttributeValue(null, "lon").toDouble()))
                "wpt" -> { inW = true; nm = ""; wla = x.getAttributeValue(null, "lat").toDouble(); wlo = x.getAttributeValue(null, "lon").toDouble() }
                "name" -> if (inW) nm = x.nextText()
            } else if (e == XmlPullParser.END_TAG && x.name == "wpt") {
                wps.add(Poi("g${wps.size}_$wla", nm.ifEmpty { "Waypoint" }, "view", wla, wlo)); inW = false
            }
            e = x.next()
        }
        return Pair(pts, wps)
    }
}
