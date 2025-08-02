package com.example.hearnear.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.glance.text.Text

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // stan przechowujący aktualne info o utworze
    var track by remember { mutableStateOf("—") }
    var artist by remember { mutableStateOf("—") }

    // zarejestruj BroadcastReceiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                track  = intent.getStringExtra("track")  ?: "—"
                artist = intent.getStringExtra("artist") ?: "—"
            }
        }
        val filter = IntentFilter("com.example.hearnear.NOW_PLAYING")
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Teraz gra:", style = MaterialTheme.typography.titleLarge)
        Text(text = track,  fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
        Text(text = artist, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
