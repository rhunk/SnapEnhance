package me.rhunk.snapenhance.features.impl.ui.menus

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import me.rhunk.snapenhance.R
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint


//TODO: Implement correctly
class MapActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, null)

        setContentView(R.layout.map)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        val startPoint = GeoPoint(50.0, 0.1)
        mapView.controller.setZoom(10.0)
        mapView.controller.setCenter(startPoint)

        val marker = Marker(mapView)
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}
