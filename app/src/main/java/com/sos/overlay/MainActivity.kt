package com.sos.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        RiderBook.init(this)
        setContent { MaterialTheme { Screen() } }
    }
}

private fun hasLocation(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasNotifications(c: Context) =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

/** Android 12+ needs Bluetooth permissions for Nearby; Android 13+ also Nearby Wi-Fi. */
private fun hasNearby(c: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val bt = granted(c, Manifest.permission.BLUETOOTH_SCAN) &&
        granted(c, Manifest.permission.BLUETOOTH_ADVERTISE) &&
        granted(c, Manifest.permission.BLUETOOTH_CONNECT)
    val wifi = Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.NEARBY_WIFI_DEVICES)
    return bt && wifi
}

private fun neededPerms(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun copyCode(ctx: Context) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("rider code", Prefs.id))
    Toast.makeText(ctx, "Rider code copied: ${Prefs.id}", Toast.LENGTH_SHORT).show()
}

private fun shareCode(ctx: Context) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Add me on SOS Overlay — my rider code is ${Prefs.id} (group ${Prefs.group}).")
    }
    ctx.startActivity(Intent.createChooser(i, "Share rider code"))
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var location by remember { mutableStateOf(hasLocation(ctx)) }
    var nearby by remember { mutableStateOf(hasNearby(ctx)) }
    var notifications by remember { mutableStateOf(hasNotifications(ctx)) }
    var running by remember { mutableStateOf(SosService.running) }
    var name by remember { mutableStateOf(Prefs.name) }
    var group by remember { mutableStateOf(Prefs.group) }
    var offline by remember { mutableStateOf(Prefs.offline) }
    var online by remember { mutableStateOf(Prefs.online) }
    val contacts by RiderBook.contacts.collectAsState()
    var newCode by remember { mutableStateOf("") }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location = hasLocation(ctx)
        nearby = hasNearby(ctx)
        notifications = hasNotifications(ctx)
    }
    val link by produceState(LinkStatus.text) {
        while (true) {
            value = LinkStatus.text
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        overlay = Settings.canDrawOverlays(ctx)
        location = hasLocation(ctx)
        nearby = hasNearby(ctx)
        notifications = hasNotifications(ctx)
        running = SosService.running
        // Ask for everything the first time so Nearby sharing actually works on Android 12+.
        if (!location || !nearby) perms.launch(neededPerms())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🆘 SOS Overlay", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Floating SOS / Fuel / Food button above Google Maps, shared with riders in your group — " +
                "offline over Bluetooth/Wi-Fi and online over the internet.",
            fontSize = 14.sp
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Rider group", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        OutlinedTextField(
            name, { name = it; Prefs.name = it },
            label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            group, { group = it.uppercase(); Prefs.group = it },
            label = { Text("Group code (same on every rider's phone)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Offline sharing (Bluetooth/Wi-Fi)", Modifier.weight(1f), fontSize = 14.sp)
            Switch(offline, { offline = it; Prefs.offline = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Online sharing (internet)", Modifier.weight(1f), fontSize = 14.sp)
            Switch(online, { online = it; Prefs.online = it })
        }

        Text("Riders", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your rider code", fontSize = 12.sp, color = Color.Gray)
                Text(Prefs.id, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { copyCode(ctx) }) { Text("Copy") }
            TextButton(onClick = { shareCode(ctx) }) { Text("Share") }
        }
        Text("Riders in your group appear here automatically on both phones. You can also add someone by their code.", fontSize = 12.sp, color = Color.Gray)

        if (contacts.isEmpty()) {
            Text("No riders added yet.", fontSize = 12.sp, color = Color.Gray)
        }
        contacts.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name.ifBlank { "Rider" }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(c.code, fontSize = 12.sp, color = Color.Gray)
                }
                TextButton(onClick = { RiderBook.remove(c.code) }) { Text("Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                newCode, { newCode = it.uppercase() },
                label = { Text("Add rider by code") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val c = RiderBook.clean(newCode)
                when {
                    c.isBlank() -> Toast.makeText(ctx, "Enter a rider code", Toast.LENGTH_SHORT).show()
                    c == Prefs.id -> Toast.makeText(ctx, "That's your own code", Toast.LENGTH_SHORT).show()
                    else -> {
                        RiderBook.add(c)
                        newCode = ""
                        Toast.makeText(ctx, "Rider $c added", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("Add") }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Overlay", overlay)
            StatusChip("Location", location)
            StatusChip("Nearby", nearby)
            StatusChip("Alerts", notifications)
            StatusChip("Active", running)
        }

        if (!overlay) {
            Button(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow floating overlay") }
            Text("Choose SOS Overlay → \"Allow display over other apps\".", fontSize = 12.sp, color = Color.Gray)
        }

        if (!location || !nearby) {
            Button(
                onClick = { perms.launch(neededPerms()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow location & nearby permissions") }
            if (!nearby) {
                Text("Offline sharing needs Bluetooth + Nearby devices permission.", fontSize = 12.sp, color = Color.Gray)
            }
        }

        if (!notifications) {
            Button(
                onClick = { perms.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow notifications (rider alerts)") }
        }

        Button(
            enabled = overlay && location && !running,
            onClick = {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SosService::class.java))
                running = true
                Toast.makeText(ctx, "Floating SOS is on", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Turn on floating SOS") }

        OutlinedButton(
            enabled = running,
            onClick = {
                ctx.stopService(Intent(ctx, SosService::class.java))
                running = false
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Turn off") }

        Text("Link: $link", fontSize = 12.sp, color = Color.Gray)

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("How to use", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "1. Share your rider code with the others, and add their codes below.\n" +
                "2. Put the same group code on every rider's phone.\n" +
                "3. Turn on the floating SOS (restart it after changing group/channels).\n" +
                "4. Open Google Maps — the red button floats above the map.\n" +
                "5. Tap it → SOS shares your location; Fuel / Food just remind the group.\n" +
                "6. Popups raise a notification and close after 5 seconds.",
            fontSize = 13.sp
        )
        Text(
            "Offline uses Bluetooth/Wi-Fi nearby (a few hundred metres). Online uses the internet (group code = shared topic).",
            fontSize = 12.sp, color = Color.Gray
        )
        Text(
            "Offline needs Bluetooth and Wi-Fi switched ON — but no internet connection is required.",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Surface(
        color = if (ok) Color(0xFF16A34A) else Color(0xFF6B7280),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            if (ok) "✓ $label" else "✗ $label",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
