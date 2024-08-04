package com.example.mytaxi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mytaxi.ui.theme.MyTaxiTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style

class MainActivity : ComponentActivity(), PermissionsListener {

    private lateinit var mapView: MapView
    private var mapboxMap: MapboxMap? = null
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_token))
// Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Initialize MapView and request permissions if needed
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            startLocationUpdates()
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
        setContent {
            MyTaxiTheme {
                MapScreen()
            }
        }

    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    @Composable
    fun MapScreen() {
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            val mapView = remember { MapView(context) }

            AndroidView(
                factory = {
                    mapView.apply {
                        getMapAsync { mapboxMap ->
                            this@MainActivity.mapboxMap = mapboxMap
                            mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
                                enableLocationComponent(style)
                                startLocationUpdates()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                ZoomInButton { zoomIn() }
                Spacer(modifier = Modifier.height(8.dp))
                ZoomOutButton { zoomOut() }
                Spacer(modifier = Modifier.height(8.dp))
                ResetButton { reset() }
            }

            DisposableEffect(mapView) {
                onDispose {
                    mapView.onStop()
                    mapView.onDestroy()
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MapScreen()
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(this, "Permission needed", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            val locationComponentOptions = LocationComponentOptions.builder(this)
                .foregroundDrawable(R.drawable.ic_car) // Update drawable resource
                .build()
            val locationComponent = mapboxMap?.locationComponent
            locationComponent?.apply {
                activateLocationComponent(
                    LocationComponentActivationOptions.builder(this@MainActivity, loadedMapStyle)
                        .locationComponentOptions(locationComponentOptions)
                        .build()
                )
                isLocationComponentEnabled = true
                cameraMode = com.mapbox.mapboxsdk.location.modes.CameraMode.TRACKING
                renderMode = com.mapbox.mapboxsdk.location.modes.RenderMode.COMPASS
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // Update interval in milliseconds
            fastestInterval = 5000 // Fastest update interval in milliseconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                val location = locationResult.lastLocation
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    val cameraPosition = CameraPosition.Builder()
                        .target(latLng)
                        .zoom(15.0)
                        .tilt(20.0)
                        .build()
                    mapboxMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun zoomIn() {
        mapboxMap?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    private fun zoomOut() {
        mapboxMap?.animateCamera(CameraUpdateFactory.zoomOut())
    }

    private fun reset() {
        mapboxMap?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.DEFAULT))
    }

    @Composable
    fun ZoomInButton(onClick: () -> Unit) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.size(48.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_zoom_in),
                contentDescription = "Zoom Out",
            )
        }
    }

    @Composable
    fun ZoomOutButton(onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_zoom_out),
                contentDescription = "Localized description",
                tint = Color.Black,
            )
        }
    }
    @Composable
    fun ResetButton(onClick: () -> Unit) {
        Button(onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.navigation),
                contentDescription = "Localized description",
                tint = Color.Black,
            )
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
