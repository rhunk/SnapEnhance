package me.rhunk.snapenhance.features.impl.ui.menus

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import me.rhunk.snapenhance.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay


//TODO: Implement correctly
class MapActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contextBundle = intent.extras?.getBundle("location") ?: return
        val latitude = contextBundle.getDouble("latitude")
        val longitude = contextBundle.getDouble("longitude")

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setContentView(R.layout.map)

        mapView = findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true);
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        val startPoint = GeoPoint(latitude, longitude)
        mapView.controller.setZoom(10.0)
        mapView.controller.setCenter(startPoint)

        val marker = Marker(mapView)
        marker.isDraggable = true
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        mapView.overlays.add(object: Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                val proj: Projection = mapView!!.projection
                val loc = proj.fromPixels(e!!.x.toInt(), e.y.toInt()) as GeoPoint
                marker.position = loc
                mapView.invalidate()
                return true
            }
        })

        mapView.overlays.add(marker)

        val applyButton = findViewById<Button>(R.id.apply_location_button)
        applyButton.setOnClickListener {
            val bundle = Bundle()
            bundle.putFloat("latitude", marker.position.latitude.toFloat())
            bundle.putFloat("longitude", marker.position.longitude.toFloat())
            setResult(RESULT_OK, intent.putExtra("location", bundle))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}
