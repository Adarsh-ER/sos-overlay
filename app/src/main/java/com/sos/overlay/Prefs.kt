package com.sos.overlay

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

    /** This phone's rider code (share it so others can add you). */
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
}

data class RiderContact(val code: String, val name: String)

/** Riders added by their rider code; names fill in when they send something. */
object RiderBook {
    private lateinit var sp: SharedPreferences
    val contacts = MutableStateFlow<List<RiderContact>>(emptyList())

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.getSharedPreferences("sos_overlay", 0)
        contacts.value = load()
    }

    fun clean(s: String) = s.uppercase().filter { it.isLetterOrDigit() }.take(8)

    private fun blocked(): MutableSet<String> = try {
        val a = JSONArray(sp.getString("blocked", "[]"))
        (0 until a.length()).map { a.getString(it) }.toMutableSet()
    } catch (e: Exception) { mutableSetOf() }

    private fun saveBlocked(set: MutableSet<String>) {
        val a = JSONArray(); set.forEach { a.put(it) }
        sp.edit().putString("blocked", a.toString()).apply()
    }

    /** Manual add by code: unblocks the rider and adds them. */
    fun add(code: String) {
        val c = clean(code)
        if (c.isBlank() || c == Prefs.id) return
        val b = blocked(); b.remove(c); saveBlocked(b)
        if (contacts.value.any { it.code == c }) return
        contacts.value = contacts.value + RiderContact(c, "")
        save()
    }

    fun remove(code: String) {
        contacts.value = contacts.value.filterNot { it.code == code }
        val b = blocked(); b.add(code); saveBlocked(b)
        save()
    }

    /** Called when a rider from the group is seen (hello / SOS / reminder): list them on both phones. */
    fun see(code: String, name: String) {
        val c = clean(code)
        if (c.isBlank() || c == Prefs.id) return
        if (blocked().contains(c)) return
        val list = contacts.value
        val i = list.indexOfFirst { it.code == c }
        if (i < 0) {
            contacts.value = list + RiderContact(c, name)
            save()
        } else if (name.isNotBlank() && list[i].name != name) {
            contacts.value = list.toMutableList().also { it[i] = list[i].copy(name = name) }
            save()
        }
    }

    fun nameFor(code: String): String? =
        contacts.value.firstOrNull { it.code == code }?.name?.ifBlank { null }

    private fun load(): List<RiderContact> = try {
        val a = JSONArray(sp.getString("contacts", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            RiderContact(o.getString("c"), o.optString("n", ""))
        }
    } catch (e: Exception) { emptyList() }

    private fun save() {
        val a = JSONArray()
        contacts.value.forEach { a.put(JSONObject().put("c", it.code).put("n", it.name)) }
        sp.edit().putString("contacts", a.toString()).apply()
    }
}
