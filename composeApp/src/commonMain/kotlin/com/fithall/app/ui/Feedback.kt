package com.fithall.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fithall.app.AiFailure
import kotlinx.coroutines.delay

/** A spinner with [text]; after [slowAfterSeconds] it adds a note so a long wait doesn't look stuck. */
@Composable
fun LoadingCard(text: String, slowAfterSeconds: Int = 20) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(slowAfterSeconds * 1000L)
        slow = true
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (slow) {
                    Text(
                        "Still working. This can take up to a minute; you can keep using the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A failed request, shown where it happened, with Retry when retrying could help. */
@Composable
fun ErrorCard(failure: AiFailure, onRetry: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    failure.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Dismiss") }
                if (failure.canRetry) {
                    FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/** Across the top of the app while there's no connection. */
@Composable
fun OfflineBanner() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "You're offline. Logging still works; AI features will be back when you reconnect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
    }
}
