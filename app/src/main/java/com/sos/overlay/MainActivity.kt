@file:OptIn(ExperimentalMaterial3Api::class)
package com.sos.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineRegion
import kotlin.math.roundToInt

val CAT = linkedMapOf("fuel" to "⛽", "food" to "🍴", "view" to "🏞️", "fix" to "🔧", "stay" to "🛏️")

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        MapLibre.getInstance(this); Store.init(this)
        setContent { MaterialTheme { App() } }
    }
}

fun startRide(c: Context) = ContextCompat.startForegroundService(c, Intent(c, RideService::class.java))
fun stopRide(c: Context) { c.stopService(Intent(c, RideService::class.java)); RideState.riders.value = emptyMap(); RideState.sos.value = null }

/** Rider presses the big red 🆘 button: share my GPS position as an SOS with the whole ride group. */
fun triggerSos() {
    val m = RideState.me.value
    if (m == null) { RideState.msg.value = "SOS needs a GPS fix first"; return }
    RideState.sos.value = Sos(RideState.myId, RideState.myName, m.latitude, m.longitude, System.currentTimeMillis())
    RideState.msg.value = "SOS sent to the group"
    MapHolder.focus(m.latitude, m.longitude, 16.0)
}
/** Rider cancels/resolves the active SOS. */
fun resolveSos() { RideState.sos.value = null; RideState.msg.value = "SOS resolved" }

@Composable fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val perms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT) }
            if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.NEARBY_WIFI_DEVICES); add(Manifest.permission.POST_NOTIFICATIONS) }
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) { launcher.launch(perms) }
    val msg by RideState.msg.collectAsState()
    LaunchedEffect(msg) { if (msg.isNotEmpty()) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); RideState.msg.value = "" } }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { MapPane(Modifier.fillMaxSize()); Hud() }
        TabRow(selectedTabIndex = tab) { listOf("Ride", "Riders", "Places", "Offline", "Pair").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) }) } }
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp)) { when (tab) { 0 -> RideTab(); 1 -> RidersTab(); 2 -> PlacesTab(); 3 -> OfflineTab(); else -> PairTab() } }
    }
}

@Composable fun MapPane(modifier: Modifier) {
    val ctx = LocalContext.current; val lo = LocalLifecycleOwner.current
    val mv = remember {
        MapView(ctx).also { v ->
            v.onCreate(null)
            v.getMapAsync { m ->
                MapHolder.map = m; MapHolder.applyStyle(Store.style)
                m.addOnCameraMoveStartedListener { r -> if (r == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) MapHolder.follow.value = false }
            }
        }
    }
    DisposableEffect(lo) {
        val o = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mv.onStart(); Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause(); Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy(); else -> {}
            }
        }
        lo.lifecycle.addObserver(o); onDispose { lo.lifecycle.removeObserver(o) }
    }
    AndroidView(factory = { mv }, modifier = modifier)
    val me by RideState.me.collectAsState(); val rd by RideState.riders.collectAsState()
    val po by RideState.pois.collectAsState(); val rt by RideState.route.collectAsState()
    LaunchedEffect(me, rd, po, rt, RideState.sos.value) { MapHolder.push() }
}

class Nav(val title: String, val sub: String, val arrow: String)
fun navInfo(r: Route?, me: android.location.Location?): Nav {
    if (me == null) return Nav("Waiting for GPS…", "Start a ride", "📡")
    if (r == null || r.pts.size < 2) return Nav("No route loaded", "Import a GPX in the Ride tab", "🧭")
    val (s, off) = r.snap(P(me.latitude, me.longitude)); val left = r.total - s
    if (left < 40) return Nav("You’ve arrived", "", "🏁")
    if (off > 200) return Nav("Off route", "${Geo.fmt(off)} from the route", "⚠️")
    val t = r.turns.firstOrNull { it.s > s + 30 }; val d = (t?.s ?: r.total) - s
    return if (t != null) Nav("Turn ${if (t.left) "left" else "right"} in ${Geo.fmt(d)}", "${Geo.fmt(left)} to finish",
        if (t.left) (if (t.deg > 70) "⬅️" else "↖️") else (if (t.deg > 70) "➡️" else "↗️"))
    else Nav("Continue ${Geo.fmt(d)}", "${Geo.fmt(left)} to finish", "⬆️")
}

@Composable fun BoxScope.Hud() {
    val me by RideState.me.collectAsState(); val route by RideState.route.collectAsState(); val riders by RideState.riders.collectAsState(); val sos by RideState.sos.collectAsState()
    val nav = remember(me, route) { navInfo(route, me) }
    val far = me?.let { m -> riders.values.filter { Geo.dist(P(m.latitude, m.longitude), P(it.lat, it.lon)) > 1500 }.map { it.name } } ?: emptyList()
    Column(Modifier.align(Alignment.TopCenter).padding(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(nav.arrow, fontSize = 28.sp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(nav.title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(nav.sub, fontSize = 12.sp) }
                Text("${((me?.speed ?: 0f) * 3.6f).roundToInt()} km/h", fontWeight = FontWeight.Bold)
            }
        }
        if (far.isNotEmpty()) Surface(color = Color(0xFFB42318), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(top = 6.dp).fillMaxWidth()) {
            Text("⚠️ Regroup: ${far.joinToString()} more than 1.5 km away", color = Color.White, modifier = Modifier.padding(8.dp), fontSize = 13.sp)
        }
    }
    val sosNow = sos
    if (sosNow != null) {
        LaunchedEffect(sosNow) { MapHolder.focus(sosNow.lat, sosNow.lon, 16.0) }
        Surface(color = Color(0xFFB42318), shape = MaterialTheme.shapes.small, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).fillMaxWidth()) {
            val m = me
            Column(Modifier.padding(12.dp)) {
                Text(if (sosNow.id == RideState.myId) "🚨 SOS — help is on the way" else "🚨 SOS — ${sosNow.name}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(if (m == null) "Waiting for GPS…" else "Location shared · ${Geo.fmt(Geo.dist(P(m.latitude, m.longitude), P(sosNow.lat, sosNow.lon)))} away", fontSize = 12.sp, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { MapHolder.focus(sosNow.lat, sosNow.lon, 16.0) }) { Text("Locate") }
                    TextButton(onClick = { resolveSos() }) { Text("Resolve") }
                }
            }
        }
    }
    val following by MapHolder.follow
    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!following) FilledTonalButton(onClick = { MapHolder.follow.value = true }, modifier = Modifier.padding(end = 10.dp)) { Text("🎯 Follow me") }
            Button(onClick = { triggerSos() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318)), modifier = Modifier.height(52.dp)) { Text("🆘 SOS") }
        }
    }
}

@Composable fun RideTab() {
    val ctx = LocalContext.current; val running by RideState.running.collectAsState(); val route by RideState.route.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                val (pts, wps) = Gpx.parse(s)
                if (pts.size > 1) {
                    RideState.route.value = Route(pts)
                    RideState.pois.value = RideState.pois.value.filter { p -> wps.none { it.id == p.id } } + wps
                    Store.savePois(); MapHolder.fit(pts)
                } else RideState.msg.value = "No track found in file"
            }
        } catch (e: Exception) { RideState.msg.value = "Could not read GPX" }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (running) stopRide(ctx) else startRide(ctx) }) { Text(if (running) "■ Stop ride" else "▶ Start ride") }
            OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Import GPX") }
        }
        Text(route?.let { "Route: ${Geo.fmt(it.total)} · ${it.turns.size} turns" } ?: "No route loaded.")
        Text("While a ride is active, GPS and the rider link keep running with the screen off.", fontSize = 12.sp)
    }
}

@Composable fun RidersTab() {
    val me by RideState.me.collectAsState(); val riders by RideState.riders.collectAsState()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    var id by remember { mutableStateOf("") }; var nm by remember { mutableStateOf("") }
    val mp = me?.let { P(it.latitude, it.longitude) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("You: ${RideState.myName} · ID ${RideState.myId}", fontWeight = FontWeight.Bold)
        riders.values.forEach { r ->
            val d = mp?.let { Geo.dist(it, P(r.lat, r.lon)) }; val age = (now - r.seen) / 1000; val ds = d?.let { Geo.fmt(it) } ?: "?"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.Bold)
                    Text(if (age > 30) "No signal for ${age}s · last seen $ds away" else "$ds away · ${(r.speed * 3.6f).roundToInt()} km/h",
                        color = if (age > 30 || (d ?: 0.0) > 1500) Color(0xFFB42318) else Color.Unspecified, fontSize = 13.sp)
                }
                TextButton(onClick = { MapHolder.focus(r.lat, r.lon) }) { Text("Locate") }
            }
        }
        if (riders.isEmpty()) Text("No riders yet. Join a ride in the Pair tab.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(id, { id = it }, label = { Text("Rider ID") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(nm, { nm = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            if (id.isNotBlank() && nm.isNotBlank()) {
                RideState.known[id.trim().uppercase()] = nm.trim(); Store.saveKnown()
                RideState.msg.value = "${nm.trim()} will be tagged when ${id.trim().uppercase()} connects"; id = ""; nm = ""
            }
        }) { Text("Add rider by ID") }
    }
}

@Composable fun PlacesTab() {
    val me by RideState.me.collectAsState(); val pois by RideState.pois.collectAsState()
    var sel by remember { mutableStateOf("all") }; var name by remember { mutableStateOf("") }; var cat by remember { mutableStateOf("fuel") }
    val mp = me?.let { P(it.latitude, it.longitude) }
    val list = pois.filter { sel == "all" || it.cat == sel }.map { it to (mp?.let { m -> Geo.dist(m, P(it.lat, it.lon)) } ?: 0.0) }.sortedBy { it.second }.take(100)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("all") + CAT.keys).forEach { k -> FilterChip(selected = sel == k, onClick = { sel = k }, label = { Text(if (k == "all") "All" else CAT[k]!!) }) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list) { (p, d) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(CAT[p.cat] ?: "📍", fontSize = 22.sp); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold); if (mp != null) Text("${Geo.fmt(d)} away", fontSize = 12.sp) }
                    TextButton(onClick = { MapHolder.focus(p.lat, p.lon, 14.0) }) { Text("Show") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("New place") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { val k = CAT.keys.toList(); cat = k[(k.indexOf(cat) + 1) % k.size] }) { Text(CAT[cat]!!, fontSize = 22.sp) }
            Button(onClick = {
                val t = MapHolder.map?.cameraPosition?.target
                if (t != null && name.isNotBlank()) {
                    RideState.pois.value = RideState.pois.value + Poi("u${System.currentTimeMillis()}", name.trim(), cat, t.latitude, t.longitude)
                    Store.savePois(); name = ""; RideState.msg.value = "Place saved at map center"
                }
            }) { Text("Drop") }
        }
    }
}

@Composable fun OfflineTab() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }; var style by remember { mutableStateOf(Store.style) }
    var zoom by remember { mutableStateOf(14f) }; var poi by remember { mutableStateOf(true) }
    var prog by remember { mutableStateOf(-1) }; var est by remember { mutableStateOf("") }
    var regs by remember { mutableStateOf(listOf<Pair<OfflineRegion, String>>()) }
    fun refresh() = Offline.list(ctx) { regs = it }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(zoom) { while (true) { est = Offline.estimate(zoom.roundToInt()); delay(800) } }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, label = { Text("Search a town or area") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = {
                scope.launch {
                    val p = withContext(Dispatchers.IO) { try { Net.geocode(q) } catch (e: Exception) { null } }
                    if (p == null) RideState.msg.value = "Not found (needs internet)" else MapHolder.focus(p.lat, p.lon, 12.0)
                }
            }) { Text("Go") }
        }
        Text("Pan and zoom the map to cover your ride area, choose the detail, then download.", fontSize = 12.sp)
        Text("Detail: zoom ${zoom.roundToInt()}"); Slider(zoom, { zoom = it }, valueRange = 11f..16f, steps = 4)
        Text(est, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(poi, { poi = it }); Text("Also fetch fuel, food, viewpoints, repair, stays") }
        Button(enabled = prog < 0, onClick = {
            val b = Offline.bounds()
            Offline.download(ctx, style, q.ifBlank { "Area ${regs.size + 1}" }, zoom.roundToInt(), { prog = it }, { RideState.msg.value = it; refresh() })
            if (poi && b != null) scope.launch {
                try {
                    val l = withContext(Dispatchers.IO) { Net.overpass(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast) }
                    RideState.pois.value = RideState.pois.value.filter { p -> l.none { it.id == p.id } } + l; Store.savePois()
                } catch (e: Exception) { RideState.msg.value = "Places could not be fetched" }
            }
        }) { Text(if (prog >= 0) "Downloading… $prog%" else "⬇ Download this area") }
        if (prog >= 0) LinearProgressIndicator(progress = { prog / 100f }, modifier = Modifier.fillMaxWidth())
        regs.forEach { (r, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { Offline.view(r) }) { Text("View") }
                TextButton(onClick = { Offline.delete(r) { refresh() } }) { Text("🗑") }
            }
        }
        OutlinedTextField(style, { style = it }, label = { Text("Map style URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { Store.style = style; MapHolder.applyStyle(style); RideState.msg.value = "Map style applied" }) { Text("Apply style") }
        Text("The default style is a low-detail demo. For real roads, use a vector style from a provider (or your own server) whose terms allow offline download.", fontSize = 12.sp)
    }
}

@Composable fun PairTab() {
    val ctx = LocalContext.current; val code by RideState.code.collectAsState(); val links by RideState.links.collectAsState(); val riders by RideState.riders.collectAsState()
    var c by remember { mutableStateOf("") }; var n by remember { mutableStateOf(RideState.myName) }
    fun join(code: String) {
        val k = code.uppercase().filter { it.isLetterOrDigit() }.removePrefix("RP")
        if (k.length < 4) { RideState.msg.value = "Enter a valid ride code"; return }
        Store.saveName(n.ifBlank { "Me" }); RideState.riders.value = emptyMap(); RideState.sos.value = null; RideState.code.value = k; c = "RP-$k"; startRide(ctx)
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (code != null) "Ride RP-$code · $links radio link(s) · ${riders.size} rider(s) on map" else "Not in a ride", fontWeight = FontWeight.Bold)
        Text("Your rider ID: ${RideState.myId} (others can add you with it)", fontSize = 13.sp)
        OutlinedTextField(n, { n = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(c, { c = it }, label = { Text("Ride code, e.g. RP-7K2Q") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { join(c) }) { Text("Join") }
            Button(onClick = { val al = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; join((1..4).map { al.random() }.joinToString("")) }) { Text("Create ride") }
            OutlinedButton(onClick = { RideState.code.value = null; RideState.riders.value = emptyMap(); RideState.sos.value = null; if (RideState.running.value) startRide(ctx) }) { Text("Leave") }
        }
        Text("Riders with the same code find each other over Bluetooth/Wi-Fi, with no mobile data. Keep phones within a few hundred metres.", fontSize = 12.sp)
    }
}
