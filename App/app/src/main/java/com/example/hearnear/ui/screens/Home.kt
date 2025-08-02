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
import com.example.hearnear.ui.components.NotificationPermissionChecker

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen() {

    NotificationPermissionChecker()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hear Near", style = MaterialTheme.typography.titleLarge)
    }
}
