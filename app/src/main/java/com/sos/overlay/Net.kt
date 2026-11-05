package com.sos.overlay

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Net {
    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "RidePack/0.1"); c.connectTimeout = 15000; c.readTimeout = 30000
        return c
    }
    fun geocode(q: String): P? {
        val t = open("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + URLEncoder.encode(q, "UTF-8"))
            .inputStream.bufferedReader().use { it.readText() }
        val a = JSONArray(t); if (a.length() == 0) return null
        return P(a.getJSONObject(0).getString("lat").toDouble(), a.getJSONObject(0).getString("lon").toDouble())
    }
    fun guess(s: String): String {
        val t = s.lowercase()
        return when {
            Regex("fuel|petrol").containsMatchIn(t) -> "fuel"
            Regex("restaurant|cafe|food").containsMatchIn(t) -> "food"
            Regex("repair|bicycle|motorcycle").containsMatchIn(t) -> "fix"
            Regex("hotel|guest|hostel").containsMatchIn(t) -> "stay"
            else -> "view"
        }
    }
    fun overpass(s: Double, w: Double, n: Double, e: Double): List<Poi> {
        val bb = "$s,$w,$n,$e"
        val q = """[out:json][timeout:25];(node["amenity"="fuel"]($bb);node["amenity"~"^(restaurant|cafe|car_repair)${'$'}"]($bb);node["tourism"~"^(viewpoint|hotel|guest_house)${'$'}"]($bb);node["shop"~"^(motorcycle|bicycle)${'$'}"]($bb););out 300;"""
        val c = open("https://overpass-api.de/api/interpreter"); c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
        val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("elements")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it); val tg = o.optJSONObject("tags") ?: JSONObject()
            Poi("o" + o.getLong("id"), tg.optString("name", "Place"), guess(tg.toString()), o.getDouble("lat"), o.getDouble("lon"))
        }
    }
}
