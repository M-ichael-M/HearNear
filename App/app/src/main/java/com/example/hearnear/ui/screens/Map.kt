package com.example.hearnear.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.example.hearnear.network.NearbyListener
import com.example.hearnear.ui.HearNearScreen
import com.example.hearnear.viewmodel.MapFilter
import com.example.hearnear.viewmodel.NearbyListenersViewModel
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonPrimitive
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

@Composable
fun MapScreen(
    nearbyListenersViewModel: NearbyListenersViewModel,
    navController: NavController
) {
    val state by nearbyListenersViewModel.state.collectAsState()
    MapLibreScreen(
        listeners = state.listeners,
        activeFilter = state.mapFilter,
        nearbyListenersViewModel = nearbyListenersViewModel,
        navController = navController
    )
}

@Composable
fun MapLibreScreen(
    listeners: List<NearbyListener>,
    activeFilter: MapFilter,
    nearbyListenersViewModel: NearbyListenersViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val symbolManagerState = remember { mutableStateOf<SymbolManager?>(null) }
    val listenerSymbols = remember { mutableStateOf<List<Symbol>>(emptyList()) }
    val selectedListener = remember { mutableStateOf<NearbyListener?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Mapa ---
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

                        val dotBitmap = createCircleBitmap(20, 0xFF90CAF9.toInt(), 0xFF2196F3.toInt(), 2)
                        style.addImage("user-dot", dotBitmap)

                        val listenerDotBitmap = createCircleBitmap(20, 0xFF8E24AA.toInt(), 0xFFFFFFFF.toInt(), 0)
                        style.addImage("listener-dot", listenerDotBitmap)

                        val friendDotBitmap = createCircleBitmap(20, 0xFF2E7D32.toInt(), 0xFFFFFFFF.toInt(), 0)
                        style.addImage("friend-dot", friendDotBitmap)

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

                        val iconName = if (activeFilter == MapFilter.FRIENDS) "friend-dot" else "listener-dot"
                        val symbols = listeners.mapIndexed { index, listener ->
                            symbolManager.create(
                                SymbolOptions()
                                    .withLatLng(LatLng(listener.latitude, listener.longitude))
                                    .withIconImage(iconName)
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

        // Aktualizacja pinów przy zmianie danych lub filtra
        LaunchedEffect(listeners, activeFilter) {
            symbolManagerState.value?.let { manager ->
                listenerSymbols.value.forEach { manager.delete(it) }
                val iconName = if (activeFilter == MapFilter.FRIENDS) "friend-dot" else "listener-dot"
                val newSymbols = listeners.mapIndexed { index, listener ->
                    manager.create(
                        SymbolOptions()
                            .withLatLng(LatLng(listener.latitude, listener.longitude))
                            .withIconImage(iconName)
                            .withIconSize(1.5f)
                            .withData(JsonPrimitive(index))
                    )
                }
                listenerSymbols.value = newSymbols
            }
        }

        // --- Pływające przyciski filtrowania ---
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapFilterChip(
                label = "All",
                selected = activeFilter == MapFilter.ALL,
                onClick = { nearbyListenersViewModel.setMapFilter(MapFilter.ALL) }
            )
            MapFilterChip(
                label = "Friends",
                selected = activeFilter == MapFilter.FRIENDS,
                selectedColor = Color(0xFF2E7D32),
                onClick = { nearbyListenersViewModel.setMapFilter(MapFilter.FRIENDS) }
            )
        }

        // Dialog po kliknięciu w pin
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
                        if (listener.distance_km >= 0) {
                            Text("Odległość: ${String.format("%.1f", listener.distance_km)} km")
                        }
                        Text("Ostatnia aktualizacja: ${if (listener.minutes_ago == 0) "Teraz" else "${listener.minutes_ago} min temu"}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedListener.value = null }) {
                        Text("Zamknij")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        nearbyListenersViewModel._selectedListener.value = listener
                        selectedListener.value = null
                        navController.navigate(HearNearScreen.OtherProfile.name)
                    }) {
                        Text("Zobacz profil")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

@Composable
private fun MapFilterChip(
    label: String,
    selected: Boolean,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedColor else Color.White,
        animationSpec = tween(200),
        label = "chip_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFF333333),
        animationSpec = tween(200),
        label = "chip_text"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        shadowElevation = 4.dp,
        modifier = Modifier.shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(20.dp)
        )
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        )
    }
}

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

fun createPinBitmap(heightPx: Int, fillColor: Int, strokeColor: Int, strokeWidthPx: Int): Bitmap {
    val widthPx = heightPx / 2
    val rectHeight = (heightPx * 0.6).toInt()
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
    val rect = RectF(strokeWidthPx / 2f, strokeWidthPx / 2f, widthPx - strokeWidthPx / 2f, rectHeight.toFloat())
    canvas.drawRoundRect(rect, widthPx / 4f, widthPx / 4f, paint)
    val path = android.graphics.Path().apply {
        moveTo(widthPx / 2f, heightPx.toFloat() - strokeWidthPx / 2f)
        lineTo((widthPx - widthPx / 4).toFloat(), rectHeight.toFloat())
        lineTo((widthPx / 4).toFloat(), rectHeight.toFloat())
        close()
    }
    canvas.drawPath(path, paint)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor; style = Paint.Style.STROKE; strokeWidth = strokeWidthPx.toFloat() }
    canvas.drawRoundRect(rect, widthPx / 4f, widthPx / 4f, stroke)
    canvas.drawPath(path, stroke)
    return bmp
}