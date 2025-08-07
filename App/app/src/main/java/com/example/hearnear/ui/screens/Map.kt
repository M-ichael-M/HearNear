package com.example.hearnear.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import com.google.android.gms.location.LocationServices
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import com.google.gson.JsonPrimitive

@Composable
fun MapScreen(nearbyListenersViewModel: NearbyListenersViewModel) {
    val state by nearbyListenersViewModel.state.collectAsState()
    MapLibreScreen(listeners = state.listeners)
}

@Composable
fun MapLibreScreen(listeners: List<NearbyListener>) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val symbolManagerState = remember { mutableStateOf<SymbolManager?>(null) }
    val listenerSymbols = remember { mutableStateOf<List<Symbol>>(emptyList()) }
    val selectedListener = remember { mutableStateOf<NearbyListener?>(null) }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                map.setStyle(
                    Style.Builder().fromUri("https://api.maptiler.com/maps/streets/style.json?key=zJQ3iHNAru3LOgSIwA5p")
                ) { style ->
                    val symbolManager = SymbolManager(mapView, map, style).apply {
                        iconAllowOverlap = true
                        textAllowOverlap = true
                    }
                    symbolManagerState.value = symbolManager

                    // Generate and add images: user dot and colored pins
                    val dotBitmap = createCircleBitmap(20, 0xFF2196F3.toInt(), 0xFFFFFFFF.toInt(), 4)
                    style.addImage("user-dot", dotBitmap)

                    // Prepare a palette of colors for listener pins
                    val colors = listOf(
                        0xFFE53935.toInt(), // red
                        0xFF43A047.toInt(), // green
                        0xFFFDD835.toInt(), // yellow
                        0xFF1E88E5.toInt(), // blue
                        0xFF8E24AA.toInt()  // purple
                    )
                    listeners.forEachIndexed { index, _ ->
                        val color = colors[index % colors.size]
                        val pinBitmap = createPinBitmap(40, color, 0xFF000000.toInt(), 4)
                        style.addImage("pin-$index", pinBitmap)
                    }

                    // Show user location
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                val userLocation = LatLng(it.latitude, it.longitude)
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(userLocation)
                                    .zoom(14.0)
                                    .build()
                                symbolManager.create(
                                    SymbolOptions()
                                        .withLatLng(userLocation)
                                        .withIconImage("user-dot")
                                        .withIconSize(2f)
                                )
                            }
                        }
                    }

                    // Add listener pins
                    val symbols = listeners.mapIndexed { index, listener ->
                        symbolManager.create(
                            SymbolOptions()
                                .withLatLng(LatLng(listener.latitude, listener.longitude))
                                .withIconImage("pin-$index")
                                .withIconSize(1.5f)
                                .withData(JsonPrimitive(index))
                        )
                    }
                    listenerSymbols.value = symbols

                    symbolManager.addClickListener { symbol ->
                        val data = symbol.data
                        if (data is JsonPrimitive && data.isNumber) {
                            val idx = data.asInt
                            if (idx in listeners.indices) selectedListener.value = listeners[idx]
                        }
                        true
                    }
                }
            }
            mapView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Update pins on listener change
    LaunchedEffect(listeners) {
        symbolManagerState.value?.let { manager ->
            listenerSymbols.value.forEach { manager.delete(it) }
            val newSymbols = listeners.mapIndexed { index, listener ->
                manager.create(
                    SymbolOptions()
                        .withLatLng(LatLng(listener.latitude, listener.longitude))
                        .withIconImage("pin-$index")
                        .withIconSize(1.5f)
                        .withData(JsonPrimitive(index))
                )
            }
            listenerSymbols.value = newSymbols
        }
    }

    // Listener info dialog
    selectedListener.value?.let { listener ->
        AlertDialog(
            onDismissRequest = { selectedListener.value = null },
            title = { Text("Słuchacz: ${listener.nick}") },
            text = {
                Column {
                    Text("Utwór: ${listener.track_name}")
                    Text("Artysta: ${listener.artist_name}")
                    if (listener.album_name?.isNotEmpty() == true) {
                        Text("Album: ${listener.album_name}")
                    }
                    Text("Odległość: ${String.format("%.1f", listener.distance_km)} km")
                    Text("Ostatnia aktualizacja: ${if (listener.minutes_ago == 0) "Teraz" else "${listener.minutes_ago} min temu"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedListener.value = null }) {
                    Text("Zamknij") }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/**
 * Creates a simple filled circle bitmap with stroke.
 * @param diameterPx size of the bitmap and circle diameter in px
 * @param fillColor ARGB fill color
 * @param strokeColor ARGB stroke color
 * @param strokeWidthPx stroke width in px
 */
fun createCircleBitmap(diameterPx: Int, fillColor: Int, strokeColor: Int, strokeWidthPx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
    val radius = (diameterPx - strokeWidthPx) / 2f
    canvas.drawCircle(diameterPx / 2f, diameterPx / 2f, radius, paint)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = strokeWidthPx.toFloat() }
    canvas.drawCircle(diameterPx / 2f, diameterPx / 2f, radius, stroke)
    return bmp
}

/**
 * Creates a pin-shaped bitmap (rounded rectangle and triangle) with fill and stroke.
 * @param heightPx total height of pin (including triangle)
 * @param fillColor ARGB fill color
 * @param strokeColor ARGB stroke color
 * @param strokeWidthPx stroke width in px
 */
fun createPinBitmap(heightPx: Int, fillColor: Int, strokeColor: Int, strokeWidthPx: Int): Bitmap {
    val widthPx = heightPx / 2
    val rectHeight = (heightPx * 0.6).toInt()
    val triangleHeight = heightPx - rectHeight
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
    // draw rounded rectangle
    val rect = RectF(strokeWidthPx/2f, strokeWidthPx/2f, widthPx - strokeWidthPx/2f, rectHeight.toFloat())
    canvas.drawRoundRect(rect, widthPx/4f, widthPx/4f, paint)
    // draw triangle
    val path = android.graphics.Path().apply {
        moveTo(widthPx/2f, heightPx.toFloat() - strokeWidthPx/2f)
        lineTo((widthPx - widthPx/4).toFloat(), rectHeight.toFloat())
        lineTo((widthPx/4).toFloat(), rectHeight.toFloat())
        close()
    }
    canvas.drawPath(path, paint)
    // stroke
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = strokeWidthPx.toFloat() }
    canvas.drawRoundRect(rect, widthPx/4f, widthPx/4f, stroke)
    canvas.drawPath(path, stroke)
    return bmp
}
