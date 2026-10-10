package com.fithall.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.fithall.app.AppViewModel
import com.fithall.app.model.Safety
import com.fithall.app.platform.rememberFileExporter
import com.fithall.app.platform.todayKey

/**
 * Shown before anything else, and again whenever PrivacyPolicy.VERSION changes. Health data is
 * special-category data under GDPR, so processing it needs explicit, separate agreement.
 */
@Composable
fun ConsentScreen(privacyPolicyUrl: String, onAccept: () -> Unit) {
    var adult by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your data and FitHall", style = MaterialTheme.typography.headlineMedium)
        Text(
            "FitHall uses AI to estimate your meals, build your plans and coach you. Here's what that means for your data.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Point(
                    "Sent to the AI",
                    "When you use an AI feature, what it needs is sent over an encrypted connection to FitHall's " +
                        "server and processed by Anthropic's Claude AI: your age, sex, height, weight, goal, " +
                        "activity, injuries and diet notes; meal photos and descriptions; workouts, weigh-ins and " +
                        "coach messages. Your name is never sent.",
                )
                Point(
                    "Not kept on our server",
                    "FitHall's server uses your data only to answer the request and doesn't store it. " +
                        "Anthropic processes it under its commercial terms; see the privacy policy for details.",
                )
                Point("Barcodes", "Barcodes you scan are looked up in Open Food Facts, a public food database. Only the barcode is sent.")
                Point(
                    "Stored on your phone",
                    "Everything you log stays on this device. You can export it or delete it at any time in Profile → Your data.",
                )
                Point("Not medical advice", "FitHall gives general fitness and nutrition guidance. It isn't a substitute for a doctor or dietitian.")
            }
        }

        if (privacyPolicyUrl.isNotBlank()) {
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { uriHandler.openUri(privacyPolicyUrl) }) {
                Icon(Icons.Filled.Policy, null)
                Spacer(Modifier.width(8.dp))
                Text("Read the full privacy policy")
            }
        }

        ConsentCheckbox(adult, { adult = it }, "I'm ${Safety.MIN_AGE} or older.")
        ConsentCheckbox(
            agreed, { agreed = it },
            "I agree to FitHall processing my health and fitness data as described above and in the privacy policy.",
        )

        Button(onClick = onAccept, enabled = adult && agreed, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Continue")
        }
        Text(
            "You can withdraw your consent at any time by deleting your data in Profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Point(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConsentCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Profile → Your data: policy link, export (data portability) and delete (erasure / withdraw consent). */
@Composable
fun YourDataSection(privacyPolicyUrl: String, viewModel: AppViewModel) {
    val exporter = rememberFileExporter()
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your logs are stored only on this phone. FitHall's server doesn't keep your data, so deleting it here removes it from FitHall completely.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (privacyPolicyUrl.isNotBlank()) {
            OutlinedButton(onClick = { uriHandler.openUri(privacyPolicyUrl) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Policy, null)
                Spacer(Modifier.width(8.dp))
                Text("Privacy policy")
            }
        }
        if (exporter.isAvailable) {
            OutlinedButton(
                onClick = { exporter.export("fithall-data-${todayKey()}.json", "application/json", viewModel.exportData()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Export my data")
            }
        }
        Button(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text("Delete all my data")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all your data?") },
            text = {
                Text(
                    "This permanently deletes your profile, meals, workouts, weigh-ins, plans, check-ins and coach chat " +
                        "from this phone. It can't be undone. Export your data first if you want a copy.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAllData() }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
