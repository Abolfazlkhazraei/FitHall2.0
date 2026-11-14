package com.fithall.app.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
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
import androidx.compose.material3.Switch
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
fun ConsentScreen(privacyPolicyUrl: String, termsUrl: String, onAccept: (crashReports: Boolean, analytics: Boolean) -> Unit) {
    var adult by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    // Optional and unticked: consent has to be an active choice.
    var crashReports by rememberSaveable { mutableStateOf(false) }
    var analytics by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(Icons.Filled.Shield, Palette.lime, size = 52.dp)
            Text("Your data and FitHall", style = MaterialTheme.typography.headlineMedium)
            Text(
                "FitHall uses AI to estimate your meals, build your plans and coach you. Here's what that means for your data.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.muted,
            )

            ContentCard {
                Point(
                    Icons.Filled.AutoAwesome, "Sent to the AI",
                    "When you use an AI feature, what it needs is sent over an encrypted connection to FitHall's " +
                        "server and processed by Anthropic's Claude AI: your age, sex, height, weight, goal, " +
                        "activity, injuries and diet notes; meal photos and descriptions; workouts, weigh-ins and " +
                        "coach messages. Your name is never sent.",
                )
                Point(
                    Icons.Filled.CloudOff, "Not kept on our server",
                    "FitHall's server uses your data only to answer the request and doesn't store it. " +
                        "Anthropic processes it under its commercial terms; see the privacy policy for details.",
                )
                Point(Icons.Filled.QrCodeScanner, "Barcodes", "Barcodes you scan are looked up in Open Food Facts, a public food database. Only the barcode is sent.")
                Point(
                    Icons.Filled.PhoneAndroid, "Stored on your phone",
                    "Everything you log stays on this device, separately for each account. You can export it or delete it at any time in Profile → Your data.",
                )
                Point(
                    Icons.Filled.Person, "Your account",
                    "Your sign-in (email and password, or your Google or Facebook account) is managed by Google Firebase " +
                        "Authentication. You can delete your account at any time in Profile → Account.",
                )
                Point(Icons.Filled.HealthAndSafety, "Not medical advice", "FitHall gives general fitness and nutrition guidance. It isn't a substitute for a doctor or dietitian.")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (privacyPolicyUrl.isNotBlank()) {
                    TextButton(onClick = { uriHandler.openUri(privacyPolicyUrl) }) {
                        Icon(Icons.Filled.Policy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Privacy policy")
                    }
                }
                if (termsUrl.isNotBlank()) {
                    TextButton(onClick = { uriHandler.openUri(termsUrl) }) {
                        Icon(Icons.Filled.Gavel, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terms of use")
                    }
                }
            }

            GroupLabel("Required")
            ConsentOption(adult, { adult = it }, "I'm ${Safety.MIN_AGE} or older")
            ConsentOption(
                agreed, { agreed = it },
                "I agree to FitHall processing my health and fitness data",
                "As described above and in the privacy policy.",
            )

            GroupLabel("Optional · off unless you turn them on")
            ConsentOption(
                crashReports, { crashReports = it },
                "Send crash reports",
                "Helps fix bugs. Includes your phone model, Android version and an app installation ID, never your health data.",
            )
            ConsentOption(
                analytics, { analytics = it },
                "Share usage analytics",
                "Which features you use, to help improve FitHall. Never your health data, meals, name or email.",
            )

            Button(
                onClick = { onAccept(crashReports, analytics) },
                enabled = adult && agreed,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(56.dp),
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (adult && agreed) "You can change the optional choices, or withdraw your consent by deleting your data, at any time in Profile."
                else "Tick both required boxes to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.muted,
            )
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, Palette.blue, size = 32.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.muted,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

/** A whole-row checkbox: lime outline once ticked, so the choices read at a glance. */
@Composable
private fun ConsentOption(checked: Boolean, onChange: (Boolean) -> Unit, title: String, detail: String? = null) {
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.surface)
            .border(1.dp, if (checked) Palette.lime else Palette.line, shape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the tap; the box only shows the state.
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.muted) }
        }
    }
}

/** Profile → Your data: policy link, export (data portability) and delete (erasure / withdraw consent). */
@Composable
fun YourDataSection(privacyPolicyUrl: String, termsUrl: String, crashReportsOn: Boolean, analyticsOn: Boolean, viewModel: AppViewModel) {
    val exporter = rememberFileExporter()
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your logs are stored only on this phone. FitHall's server doesn't keep them, so deleting them here removes them from FitHall completely. Your account stays; delete it under Account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Send crash reports", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Helps fix bugs. Includes phone model, Android version and an installation ID; never health data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = crashReportsOn, onCheckedChange = viewModel::setCrashReports)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share usage analytics", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Which features you use, so we can improve them. Never health data, meals, name or email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = analyticsOn, onCheckedChange = viewModel::setAnalytics)
        }
        if (privacyPolicyUrl.isNotBlank()) {
            OutlinedButton(onClick = { uriHandler.openUri(privacyPolicyUrl) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Policy, null)
                Spacer(Modifier.width(8.dp))
                Text("Privacy policy")
            }
        }
        if (termsUrl.isNotBlank()) {
            OutlinedButton(onClick = { uriHandler.openUri(termsUrl) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Gavel, null)
                Spacer(Modifier.width(8.dp))
                Text("Terms of use")
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
