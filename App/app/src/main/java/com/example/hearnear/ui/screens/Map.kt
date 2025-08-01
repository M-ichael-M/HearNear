package com.example.hearnear.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hearnear.R
import com.google.android.gms.location.LocationServices
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import androidx.core.graphics.createBitmap

@Composable
fun MapScreen()
{
    MapLibreScreen()
}

@Composable
fun MapLibreScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                map.setStyle(
                    Style.Builder().fromUri("https://api.maptiler.com/maps/streets/style.json?key=zJQ3iHNAru3LOgSIwA5p")
                ) { style ->
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                val userLocation = LatLng(it.latitude, it.longitude)

                                // Przesuń kamerę
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(userLocation)
                                    .zoom(14.0)
                                    .build()

                                // Dodaj warstwę z markerem
                                val symbolManager = SymbolManager(mapView, map, style)
                                symbolManager.iconAllowOverlap = true
                                symbolManager.textAllowOverlap = true

                                // Najpierw dodaj ikonę do stylu

                                val drawable = ContextCompat.getDrawable(context, R.drawable.sharp_location_on_24)
                                val bitmap =
                                    drawable?.let { it1 -> createBitmap(it1.intrinsicWidth, drawable.intrinsicHeight) }
                                val canvas = bitmap?.let { it1 -> Canvas(it1) }
                                if (drawable != null) {
                                    canvas?.let { it1 -> drawable.setBounds(0, 0, it1.width, canvas.height) }
                                }
                                if (canvas != null) {
                                    drawable.draw(canvas)
                                }
                                if (bitmap != null) {
                                    style.addImage("marker-icon", bitmap)
                                }

                                // Następnie utwórz symbol z ikoną
                                symbolManager.create(
                                    SymbolOptions()
                                        .withLatLng(userLocation)
                                        .withIconImage("marker-icon")
                                        .withIconSize(1.3f)
                                )
                            }
                        }
                    }
                }
            }
            mapView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}
