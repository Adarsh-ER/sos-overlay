package com.sos.overlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Small persistent config for the rider group and sharing channels. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.getSharedPreferences("sos_overlay", 0)
        if (!sp.contains("id")) {
            val al = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
            sp.edit().putString("id", (1..6).map { al.random() }.joinToString("")).apply()
        }
    }

    val id: String get() = sp.getString("id", "RIDER") ?: "RIDER"

    var name: String
        get() = sp.getString("name", "Rider") ?: "Rider"
        set(v) { sp.edit().putString("name", v).apply() }

    var group: String
        get() = sp.getString("group", "SOS1") ?: "SOS1"
        set(v) { sp.edit().putString("group", v.trim().uppercase().ifBlank { "SOS1" }).apply() }

    var offline: Boolean
        get() = sp.getBoolean("offline", true)
        set(v) { sp.edit().putBoolean("offline", v).apply() }

    var online: Boolean
        get() = sp.getBoolean("online", true)
        set(v) { sp.edit().putBoolean("online", v).apply() }

    fun riders(): List<String> = try {
        val a = JSONArray(sp.getString("riders", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun saveRiders(list: List<String>) {
        val a = JSONArray(); list.forEach { a.put(it) }
        sp.edit().putString("riders", a.toString()).apply()
    }
}
