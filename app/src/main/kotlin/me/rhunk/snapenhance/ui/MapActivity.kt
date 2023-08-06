package me.rhunk.snapenhance.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import me.rhunk.snapenhance.core.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay


class MapActivity : Activity() {

    private lateinit var mapView: MapView

    @SuppressLint("MissingInflatedId", "ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contextBundle = intent.extras?.getBundle("location") ?: return
        val locationLatitude = contextBundle.getDouble("latitude")
        val locationLongitude = contextBundle.getDouble("longitude")

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setContentView(R.layout.map)

        mapView = findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true);
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        val startPoint = GeoPoint(locationLatitude, locationLongitude)
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

        val setPreciseLocationButton = findViewById<Button>(R.id.set_precise_location_button)

        setPreciseLocationButton.setOnClickListener {
            val locationDialog = layoutInflater.inflate(R.layout.precise_location_dialog, null)
            val dialogLatitude = locationDialog.findViewById<EditText>(R.id.dialog_latitude).also { it.setText(marker.position.latitude.toString()) }
            val dialogLongitude = locationDialog.findViewById<EditText>(R.id.dialog_longitude).also { it.setText(marker.position.longitude.toString()) }

            AlertDialog.Builder(this)
                .setView(locationDialog)
                .setTitle("Set a precise location")
                .setPositiveButton("Set") { _, _ ->
                    val latitude = dialogLatitude.text.toString().toDoubleOrNull()
                    val longitude = dialogLongitude.text.toString().toDoubleOrNull()
                    if (latitude != null && longitude != null) {
                        val preciseLocation = GeoPoint(latitude, longitude)
                        mapView.controller.setCenter(preciseLocation)
                        marker.position = preciseLocation
                        mapView.invalidate()
                    }
                }.setNegativeButton("Cancel") { _, _ -> }.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}
