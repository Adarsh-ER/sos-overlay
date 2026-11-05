package com.sos.overlay

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.*
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.*

object MapHolder {
    var map: MapLibreMap? = null
    private var style: Style? = null
    val follow = mutableStateOf(true)
    private var centered = false
    private val palette = listOf("#e8590c", "#16a34a", "#9333ea", "#db2777", "#0891b2")

    fun applyStyle(url: String) {
        map?.setStyle(Style.Builder().fromUri(url)) { s ->
            style = s
            s.addSource(GeoJsonSource("route")); s.addSource(GeoJsonSource("pois")); s.addSource(GeoJsonSource("riders"))
            s.addLayer(LineLayer("route-l", "route").withProperties(lineColor("#2563eb"), lineWidth(5f)))
            s.addLayer(CircleLayer("pois-l", "pois").withProperties(circleRadius(7f), circleColor("#e8590c"), circleStrokeColor("#ffffff"), circleStrokeWidth(2f)))
            s.addLayer(CircleLayer("riders-l", "riders").withProperties(circleRadius(9f), circleColor(Expression.get("color")), circleStrokeColor("#ffffff"), circleStrokeWidth(3f)))
            s.addLayer(SymbolLayer("riders-t", "riders").withProperties(textField(Expression.get("name")), textSize(12f),
                textOffset(arrayOf(0f, 1.6f)), textFont(arrayOf("Open Sans Semibold", "Arial Unicode MS Bold")), textHaloColor("#ffffff"), textHaloWidth(1.5f)))
            push()
        }
    }

    private fun fc(f: List<JSONObject>) = JSONObject().put("type", "FeatureCollection").put("features", JSONArray(f)).toString()
    private fun pt(lat: Double, lon: Double, name: String, color: String) = JSONObject().put("type", "Feature")
        .put("properties", JSONObject().put("name", name).put("color", color))
        .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat)))

    fun push() {
        val s = style ?: return
        val r = RideState.route.value
        val line = r?.let { JSONObject().put("type", "Feature").put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray(it.pts.map { p -> JSONArray().put(p.lon).put(p.lat) }))) }
        s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(fc(listOfNotNull(line)))
        s.getSourceAs<GeoJsonSource>("pois")?.setGeoJson(fc(RideState.pois.value.map { pt(it.lat, it.lon, it.name, "#e8590c") }))
        val me = RideState.me.value
        val sos = RideState.sos.value
        val feats = ArrayList<JSONObject>()
        me?.let { feats.add(pt(it.latitude, it.longitude, "You", "#2563eb")) }
        sos?.let { s -> feats.add(pt(s.lat, s.lon, if (s.id == RideState.myId) "🚨 SOS" else "🚨 SOS · ${s.name}", "#dc2626")) }
        RideState.riders.value.values.forEach { feats.add(pt(it.lat, it.lon, it.name, palette[abs(it.id.hashCode()) % palette.size])) }
        s.getSourceAs<GeoJsonSource>("riders")?.setGeoJson(fc(feats))
        if (me != null) {
            val ll = LatLng(me.latitude, me.longitude)
            if (!centered) { centered = true; map?.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 14.0)) }
            else if (follow.value) map?.animateCamera(CameraUpdateFactory.newLatLng(ll))
        }
    }

    fun focus(lat: Double, lon: Double, zoom: Double? = null) {
        follow.value = false
        val ll = LatLng(lat, lon)
        map?.animateCamera(if (zoom != null) CameraUpdateFactory.newLatLngZoom(ll, zoom) else CameraUpdateFactory.newLatLng(ll))
    }
    fun fit(pts: List<P>) {
        follow.value = false
        val b = LatLngBounds.Builder(); pts.forEach { b.include(LatLng(it.lat, it.lon)) }
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80))
    }
}

/** Offline map areas (like Google Maps "download offline map"), using MapLibre's built-in offline manager. */
object Offline {
    fun bounds(): LatLngBounds? = MapHolder.map?.projection?.visibleRegion?.latLngBounds
    private fun lx(lo: Double, z: Int) = floor((lo + 180) / 360 * 2.0.pow(z)).toLong()
    private fun ly(la: Double, z: Int): Long { val r = Math.toRadians(la); return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 2.0.pow(z)).toLong() }
    fun minZ(z: Int) = max(5, z - 5)
    fun tiles(b: LatLngBounds, z0: Int, z1: Int): Long {
        var n = 0L
        for (z in z0..z1) n += (lx(b.longitudeEast, z) - lx(b.longitudeWest, z) + 1) * (ly(b.latitudeSouth, z) - ly(b.latitudeNorth, z) + 1)
        return n
    }
    fun estimate(z: Int): String {
        val b = bounds() ?: return "Map not ready"; val n = tiles(b, minZ(z), z)
        return "About $n tiles · ~${n * 15 / 1024} MB (zoom ${minZ(z)}–$z)" + if (n > 6000) " — too large, zoom in or lower the detail" else ""
    }
    fun download(ctx: Context, styleUrl: String, name: String, z: Int, onProg: (Int) -> Unit, onMsg: (String) -> Unit) {
        val b = bounds() ?: return onMsg("Map not ready")
        if (tiles(b, minZ(z), z) > 6000) return onMsg("Area too large – zoom in or lower the detail")
        val def = OfflineTilePyramidRegionDefinition(styleUrl, b, minZ(z).toDouble(), z.toDouble(), ctx.resources.displayMetrics.density)
        OfflineManager.getInstance(ctx).createOfflineRegion(def, JSONObject().put("name", name).toString().toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(s: OfflineRegionStatus) {
                            onProg(if (s.requiredResourceCount > 0) (100 * s.completedResourceCount / s.requiredResourceCount).toInt() else 0)
                            if (s.isComplete) { onProg(-1); onMsg("Saved offline: $name") }
                        }
                        override fun onError(e: OfflineRegionError) { onMsg("Download error: ${e.message}") }
                        override fun mapboxTileCountLimitExceeded(limit: Long) { onMsg("Tile limit reached ($limit)") }
                    })
                }
                override fun onError(error: String) { onMsg("Could not start: $error") }
            })
    }
    fun list(ctx: Context, cb: (List<Pair<OfflineRegion, String>>) -> Unit) {
        OfflineManager.getInstance(ctx).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(r: Array<OfflineRegion>?) {
                cb((r?.toList() ?: emptyList()).map { it to (try { JSONObject(String(it.metadata)).optString("name", "Area") } catch (e: Exception) { "Area" }) })
            }
            override fun onError(error: String) {}
        })
    }
    fun delete(r: OfflineRegion, done: () -> Unit) {
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback { override fun onDelete() = done(); override fun onError(error: String) {} })
    }
    fun view(r: OfflineRegion) {
        val b = (r.definition as? OfflineTilePyramidRegionDefinition)?.bounds ?: return
        MapHolder.follow.value = false
        MapHolder.map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 40))
    }
}
