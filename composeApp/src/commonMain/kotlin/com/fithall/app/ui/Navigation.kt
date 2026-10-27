package com.fithall.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where the app can go. The first four are in the bottom bar; Coach and Profile open from Today. */
enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    TRAIN("Train", Icons.Filled.FitnessCenter),
    EAT("Eat", Icons.Filled.Restaurant),
    PROGRESS("Progress", Icons.AutoMirrored.Filled.TrendingUp),
    COACH("Coach", Icons.Filled.AutoAwesome),
    PROFILE("Profile", Icons.Filled.Person),
}

/** Ways to log a meal that the quick-add sheet can start on the Eat screen. */
enum class MealInput { PHOTO, GALLERY, BARCODE, DESCRIBE }

/**
 * The floating bottom bar: Today, Train, a raised + in the middle, Eat, Progress. Coach and
 * Profile highlight nothing, since they're reached from the screens.
 */
@Composable
fun FloatingNavBar(
    current: Destination,
    progressBadge: Boolean,
    onSelect: (Destination) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(72.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Palette.ground, spotColor = Palette.ground)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.surface)
                .border(1.dp, Palette.line, RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem(Destination.TODAY, current, onSelect)
            NavItem(Destination.TRAIN, current, onSelect)
            Spacer(Modifier.width(64.dp))
            NavItem(Destination.EAT, current, onSelect)
            NavItem(Destination.PROGRESS, current, onSelect, badge = progressBadge)
        }
        Surface(
            onClick = onQuickAdd,
            shape = RoundedCornerShape(22.dp),
            color = Palette.lime,
            contentColor = Palette.onLime,
            shadowElevation = 12.dp,
            modifier = Modifier.size(62.dp).offset(y = (-18).dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Log something", modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun NavItem(item: Destination, current: Destination, onSelect: (Destination) -> Unit, badge: Boolean = false) {
    val selected = item == current
    val color = if (selected) Palette.lime else Palette.muted
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab) { onSelect(item) }
            .widthIn(min = 60.dp)
            .heightIn(min = 52.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        BadgedBox(badge = { if (badge) Badge(containerColor = Palette.blue) }) {
            Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Everything the user can log, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    lastWeight: String?,
    onMeal: (MealInput) -> Unit,
    onWorkout: () -> Unit,
    onWeight: () -> Unit,
    onCoach: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.surface,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        QuickAddContent(lastWeight, onMeal, onWorkout, onWeight, onCoach)
    }
}

/** The quick-add sheet's options. */
@Composable
fun QuickAddContent(
    lastWeight: String?,
    onMeal: (MealInput) -> Unit,
    onWorkout: () -> Unit,
    onWeight: () -> Unit,
    onCoach: () -> Unit,
) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("What's happening?", style = MaterialTheme.typography.headlineSmall)
                Text("Everything you can log, in one place", style = MaterialTheme.typography.bodyMedium, color = Palette.muted)
            }
            AccentRow(Icons.Filled.CameraAlt, "Snap a meal", "Photo in, calories and macros out", Palette.lime, { onMeal(MealInput.PHOTO) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTile(Icons.Filled.QrCodeScanner, "Scan barcode", "Packaged food", Modifier.weight(1f)) { onMeal(MealInput.BARCODE) }
                SheetTile(Icons.Filled.Mic, "Say or type it", "\"2 eggs and toast\"", Modifier.weight(1f)) { onMeal(MealInput.DESCRIBE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetTile(Icons.Filled.PhotoLibrary, "From gallery", "A photo you took", Modifier.weight(1f)) { onMeal(MealInput.GALLERY) }
                SheetTile(Icons.Filled.FitnessCenter, "Start workout", "Plan or custom", Modifier.weight(1f), onClick = onWorkout)
            }
            AccentRow(Icons.Filled.MonitorWeight, "Log weight", lastWeight?.let { "Last: $it" } ?: "Track your trend", Palette.raised, onWeight)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(MaterialTheme.shapes.medium).background(Palette.blueDeep)
                    .border(1.dp, Palette.blueLine, MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClick = onCoach).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Palette.blue)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ask your coach", style = MaterialTheme.typography.titleMedium)
                    Text("Knows your meals, plan and goals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
}

@Composable
private fun SheetTile(icon: ImageVector, title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Palette.raised)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = Palette.lime, modifier = Modifier.size(26.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.muted, maxLines = 1)
        }
    }
}
