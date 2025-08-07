package com.example.hearnear.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.example.hearnear.R
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
import androidx.core.graphics.createBitmap
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
        MapView(context).apply {
            onCreate(null)
        }
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
                    val symbolManager = SymbolManager(mapView, map, style)
                    symbolManager.iconAllowOverlap = true
                    symbolManager.textAllowOverlap = true
                    symbolManagerState.value = symbolManager

                    // Dodaj ikonę do stylu
                    val drawable = ContextCompat.getDrawable(context, R.drawable.sharp_location_on_24)
                    val bitmap = drawable?.let { createBitmap(it.intrinsicWidth, it.intrinsicHeight) }
                    val canvas = bitmap?.let { Canvas(it) }
                    if (drawable != null) {
                        canvas?.let { it -> drawable.setBounds(0, 0, it.width, it.height) }
                    }
                    if (canvas != null) {
                        drawable.draw(canvas)
                    }
                    if (bitmap != null) {
                        style.addImage("marker-icon", bitmap)
                    }

                    // Lokalizacja użytkownika
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
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
                                        .withIconImage("marker-icon")
                                        .withIconSize(1.3f)
                                )
                            }
                        }
                    }

                    // Dodaj pinezki dla pobliskich użytkowników
                    val symbols = listeners.mapIndexed { index, listener ->
                        val listenerLocation = LatLng(listener.latitude, listener.longitude)
                        symbolManager.create(
                            SymbolOptions()
                                .withLatLng(listenerLocation)
                                .withIconImage("marker-icon")
                                .withIconSize(1.3f)
                                .withData(JsonPrimitive(index)) // Poprawka: Int -> JsonElement
                        )
                    }
                    listenerSymbols.value = symbols

                    // Obsługa kliknięcia na pinezkę
                    symbolManager.addClickListener { symbol ->
                        Log.d("Map", "Symbol clicked: $symbol, data: ${symbol.data}")
                        val data = symbol.data
                        if (data is JsonPrimitive && data.isNumber) {
                            val index = data.getAsInt()
                            if (index >= 0 && index < listeners.size) {
                                selectedListener.value = listeners[index]
                            } else {
                                Log.e("Map", "Index out of bounds: $index, list size: ${listeners.size}")
                            }
                        } else {
                            Log.e("Map", "Invalid symbol data: $data")
                        }
                        true
                    }
                }
            }
            mapView
        },
        modifier = Modifier.fillMaxSize()
    )

    // Aktualizacja pinezek przy zmianie listy użytkowników
    LaunchedEffect(listeners) {
        symbolManagerState.value?.let { manager ->
            listenerSymbols.value.forEach { manager.delete(it) }
            val newSymbols = listeners.mapIndexed { index, listener ->
                val listenerLocation = LatLng(listener.latitude, listener.longitude)
                manager.create(
                    SymbolOptions()
                        .withLatLng(listenerLocation)
                        .withIconImage("marker-icon")
                        .withIconSize(1.3f)
                        .withData(JsonPrimitive(index)) // Poprawka: Int -> JsonElement
                )
            }
            listenerSymbols.value = newSymbols
        }
    }

    // Wyświetlanie informacji o użytkowniku po kliknięciu
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
                    Text("Zamknij")
                }
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