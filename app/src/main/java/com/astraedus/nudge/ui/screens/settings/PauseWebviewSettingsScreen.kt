package com.astraedus.nudge.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraedus.nudge.data.preferences.NudgePreferences
import com.astraedus.nudge.domain.webview.PauseWebviewConfig
import com.astraedus.nudge.domain.webview.PauseWebviewResetStrategy
import com.astraedus.nudge.domain.webview.PauseWebviewSelectionMode
import com.astraedus.nudge.domain.webview.PauseWebviewSite
import com.astraedus.nudge.domain.webview.PauseWebviewTag
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PauseWebviewSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { NudgePreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val config by preferences.pauseWebviewConfig.collectAsStateWithLifecycle(initialValue = PauseWebviewConfig())
    val appIncrements by preferences.pauseWebviewAppIncrements.collectAsStateWithLifecycle(initialValue = emptyMap())

    // Level 1 Site Dialog State
    var isAddingSite by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<PauseWebviewSite?>(null) }

    // Level 2 Tag Dialog State
    var addingTagForSite by remember { mutableStateOf<PauseWebviewSite?>(null) }
    var editingTagInfo by remember { mutableStateOf<Pair<PauseWebviewSite, PauseWebviewTag>?>(null) }

    // Custom Time Dialog State (fallback or direct)
    var showCustomTimeDialog by remember { mutableStateOf(false) }

    fun updateConfig(block: (PauseWebviewConfig) -> PauseWebviewConfig) {
        scope.launch {
            val updated = block(config)
            preferences.savePauseWebviewConfig(updated)
        }
    }

    fun openNativeTimePicker() {
        try {
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            android.app.TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    updateConfig { it.copy(resetHourOfDay = hourOfDay, resetMinuteOfDay = minute) }
                },
                config.resetHourOfDay,
                config.resetMinuteOfDay,
                is24Hour
            ).show()
        } catch (_: Exception) {
            showCustomTimeDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pause Webviews") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Section 1: Selection Strategy (Level 1 Website) ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Website Selection (Level 1)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (config.selectionMode == PauseWebviewSelectionMode.ROUND_ROBIN) {
                        "Round Robin: Cycles to the next configured website on each pause overlay."
                    } else {
                        "Last Selected: Defaults to the website last chosen in the overlay or settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val modes = PauseWebviewSelectionMode.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = config.selectionMode == mode,
                            onClick = { updateConfig { it.copy(selectionMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            icon = {}
                        ) {
                            Text(if (mode == PauseWebviewSelectionMode.ROUND_ROBIN) "Round Robin" else "Last Selected")
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- Section 2: Configured Websites & Tags (Level 1 & Level 2) ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Websites & Custom Tags",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${config.sites.size} websites configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(onClick = { isAddingSite = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Website")
                    }
                }

                if (config.sites.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No websites configured.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    updateConfig { it.copy(sites = PauseWebviewConfig.DEFAULT_SITES) }
                                }
                            ) {
                                Text("Restore Defaults (Wikipedia & Daily Word)")
                            }
                        }
                    }
                } else {
                    config.sites.forEach { site ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Level 1 Website Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = site.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${site.tags.size} tag(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { addingTagForSite = site }) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(2.dp))
                                            Text("Tag", fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { editingSite = site }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Site")
                                        }
                                        IconButton(
                                            onClick = {
                                                updateConfig { current ->
                                                    current.copy(sites = current.sites.filter { it.id != site.id })
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Site")
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Level 2 Custom Tags under this site
                                Text(
                                    text = "Custom Tags (saved per device):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (site.tags.isEmpty()) {
                                    Text(
                                        text = "No custom tags. Tap '+ Tag' above to add one.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        site.tags.forEach { tag ->
                                            val isSelected = site.selectedTagId == tag.id
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        scope.launch {
                                                            preferences.updatePauseWebviewTagSelection(site.id, tag.id)
                                                        }
                                                    },
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = {
                                                                scope.launch {
                                                                    preferences.updatePauseWebviewTagSelection(site.id, tag.id)
                                                                }
                                                            }
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                                    shape = MaterialTheme.shapes.extraSmall
                                                                ) {
                                                                    Text(
                                                                        text = tag.tag,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                                if (tag.label.isNotBlank()) {
                                                                    Spacer(Modifier.width(6.dp))
                                                                    Text(
                                                                        text = tag.label,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = tag.url,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    Row {
                                                        IconButton(onClick = { editingTagInfo = Pair(site, tag) }) {
                                                            Icon(
                                                                Icons.Outlined.Edit,
                                                                contentDescription = "Edit Tag",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                updateConfig { current ->
                                                                    val updatedSites = current.sites.map { s ->
                                                                        if (s.id == site.id) {
                                                                            val newTags = s.tags.filter { it.id != tag.id }
                                                                            val newSelected = if (s.selectedTagId == tag.id) {
                                                                                newTags.firstOrNull()?.id
                                                                            } else s.selectedTagId
                                                                            s.copy(tags = newTags, selectedTagId = newSelected)
                                                                        } else s
                                                                    }
                                                                    current.copy(sites = updatedSites)
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                Icons.Outlined.Delete,
                                                                contentDescription = "Delete Tag",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- Section 3: Incremental Friction & Custom Reset Time ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Incremental Friction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Adds seconds to an app's pause each time you wait it out and enter, making frequent opens harder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Increment Picker
                Text(
                    text = "Increment per full open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                val incrementPresets = listOf(0, 1, 2, 5, 10, 15, 30)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    incrementPresets.forEach { inc ->
                        FilterChip(
                            selected = config.incrementalSeconds == inc,
                            onClick = { updateConfig { it.copy(incrementalSeconds = inc) } },
                            label = { Text(if (inc == 0) "Disabled (0s)" else "+${inc}s") }
                        )
                    }
                }

                if (config.incrementalSeconds > 0) {
                    // Maximum cap picker
                    Text(
                        text = "Maximum accumulated delay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val capPresets = listOf(30, 60, 120, 180, 300)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        capPresets.forEach { cap ->
                            FilterChip(
                                selected = config.maxIncrementalSeconds == cap,
                                onClick = { updateConfig { it.copy(maxIncrementalSeconds = cap) } },
                                label = { Text("${cap}s") }
                            )
                        }
                    }

                    // Reset Condition Strategy
                    Text(
                        text = "Reset condition",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val resetStrategies = PauseWebviewResetStrategy.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        resetStrategies.forEachIndexed { index, strategy ->
                            SegmentedButton(
                                selected = config.resetStrategy == strategy,
                                onClick = { updateConfig { it.copy(resetStrategy = strategy) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = resetStrategies.size),
                                icon = {}
                            ) {
                                Text(
                                    if (strategy == PauseWebviewResetStrategy.TIME_OF_DAY) "Hour of Day" else "Inactivity"
                                )
                            }
                        }
                    }

                    if (config.resetStrategy == PauseWebviewResetStrategy.TIME_OF_DAY) {
                        Text(
                            text = "Reset hour (daily)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val hourPresets = listOf(
                            0 to "Midnight (00:00)",
                            4 to "04:00 AM",
                            6 to "06:00 AM",
                            8 to "08:00 AM",
                            12 to "12:00 PM"
                        )
                        val isPresetSelected = hourPresets.any { it.first == config.resetHourOfDay && config.resetMinuteOfDay == 0 }
                        val customChipText = if (!isPresetSelected) {
                            String.format(Locale.US, "Custom (%02d:%02d)", config.resetHourOfDay, config.resetMinuteOfDay)
                        } else {
                            "Custom..."
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            hourPresets.forEach { (hour, label) ->
                                val selected = (config.resetHourOfDay == hour && config.resetMinuteOfDay == 0)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        updateConfig { it.copy(resetHourOfDay = hour, resetMinuteOfDay = 0) }
                                    },
                                    label = { Text(label) }
                                )
                            }

                            // Custom Hour/Minute Picker Chip
                            FilterChip(
                                selected = !isPresetSelected,
                                onClick = { openNativeTimePicker() },
                                label = { Text(customChipText) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Inactivity cooldown duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val cooldownPresets = listOf(
                            30 to "30m",
                            60 to "1h",
                            120 to "2h",
                            240 to "4h",
                            480 to "8h",
                            1440 to "24h"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            cooldownPresets.forEach { (mins, label) ->
                                FilterChip(
                                    selected = config.inactivityCooldownMinutes == mins,
                                    onClick = { updateConfig { it.copy(inactivityCooldownMinutes = mins) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // Current accumulated apps
                    if (appIncrements.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active App Increments",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        preferences.resetAppIncrementalSeconds(null)
                                        Toast.makeText(context, "All app increments reset", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Reset All")
                            }
                        }

                        appIncrements.forEach { (pkg, state) ->
                            if (state.accumulatedSeconds > 0) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pkg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                "+${state.accumulatedSeconds}s accumulated",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    preferences.resetAppIncrementalSeconds(pkg)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset this app")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- Section 4: Completion Behavior ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Auto-proceed at 0s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "When the pause timer finishes, automatically enter the blocked app without requiring a tap on Continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.autoProceedOnZero,
                    onCheckedChange = { auto -> updateConfig { it.copy(autoProceedOnZero = auto) } }
                )
            }
        }
    }

    // --- Dialog: Add / Edit Website (Level 1) ---
    if (isAddingSite || editingSite != null) {
        val isEditing = editingSite != null
        var nameInput by rememberSaveable(editingSite) {
            mutableStateOf(editingSite?.name ?: "")
        }

        AlertDialog(
            onDismissRequest = {
                isAddingSite = false
                editingSite = null
            },
            title = { Text(if (isEditing) "Edit Website" else "Add Website") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Website Name (e.g. Wikipedia)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = nameInput.trim()
                        if (cleanName.isBlank()) {
                            Toast.makeText(context, "Website name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        updateConfig { current ->
                            if (isEditing) {
                                val updated = current.sites.map {
                                    if (it.id == editingSite!!.id) it.copy(name = cleanName) else it
                                }
                                current.copy(sites = updated)
                            } else {
                                val newSite = PauseWebviewSite(
                                    id = "site_${UUID.randomUUID()}",
                                    name = cleanName
                                )
                                current.copy(sites = current.sites + newSite)
                            }
                        }
                        isAddingSite = false
                        editingSite = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isAddingSite = false
                        editingSite = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Dialog: Add / Edit Tag (Level 2) ---
    if (addingTagForSite != null || editingTagInfo != null) {
        val isEditing = editingTagInfo != null
        val targetSite = editingTagInfo?.first ?: addingTagForSite!!
        val targetTag = editingTagInfo?.second

        var tagInput by rememberSaveable(targetTag) {
            mutableStateOf(targetTag?.tag ?: "")
        }
        var labelInput by rememberSaveable(targetTag) {
            mutableStateOf(targetTag?.label ?: "")
        }
        var urlInput by rememberSaveable(targetTag) {
            mutableStateOf(targetTag?.url ?: "")
        }

        AlertDialog(
            onDismissRequest = {
                addingTagForSite = null
                editingTagInfo = null
            },
            title = {
                Text(
                    if (isEditing) "Edit Tag for ${targetSite.name}" else "Add Tag for ${targetSite.name}"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it.uppercase().take(12) },
                        label = { Text("Tag Code (e.g. RANDOM, TODAY)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Display Label (optional)") },
                        placeholder = { Text("e.g. Wikipedia Random") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanTag = tagInput.trim()
                        val cleanLabel = labelInput.trim()
                        var cleanUrl = urlInput.trim()
                        if (cleanTag.isBlank() || cleanUrl.isBlank()) {
                            Toast.makeText(context, "Tag code and URL are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                            cleanUrl = "https://$cleanUrl"
                        }

                        updateConfig { current ->
                            val updatedSites = current.sites.map { site ->
                                if (site.id == targetSite.id) {
                                    if (isEditing) {
                                        val updatedTags = site.tags.map {
                                            if (it.id == targetTag!!.id) {
                                                it.copy(tag = cleanTag, label = cleanLabel, url = cleanUrl)
                                            } else it
                                        }
                                        site.copy(tags = updatedTags)
                                    } else {
                                        val newTag = PauseWebviewTag(
                                            id = "tag_${UUID.randomUUID()}",
                                            tag = cleanTag,
                                            label = cleanLabel,
                                            url = cleanUrl
                                        )
                                        val newSelectedId = site.selectedTagId ?: newTag.id
                                        site.copy(tags = site.tags + newTag, selectedTagId = newSelectedId)
                                    }
                                } else site
                            }
                            current.copy(sites = updatedSites)
                        }

                        addingTagForSite = null
                        editingTagInfo = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        addingTagForSite = null
                        editingTagInfo = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Dialog: Fallback Custom Time Picker (Hour & Minute) ---
    if (showCustomTimeDialog) {
        var hourText by rememberSaveable { mutableStateOf(config.resetHourOfDay.toString()) }
        var minText by rememberSaveable { mutableStateOf(config.resetMinuteOfDay.toString()) }

        val parsedHour = hourText.toIntOrNull()
        val parsedMin = minText.toIntOrNull()
        val isValid = parsedHour != null && parsedHour in 0..23 && parsedMin != null && parsedMin in 0..59

        AlertDialog(
            onDismissRequest = { showCustomTimeDialog = false },
            title = { Text("Select Daily Reset Time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the time when the accumulated friction delay resets every day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) hourText = it.take(2) },
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) minText = it.take(2) },
                            label = { Text("Min (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isValid) {
                            updateConfig {
                                it.copy(resetHourOfDay = parsedHour!!, resetMinuteOfDay = parsedMin!!)
                            }
                            showCustomTimeDialog = false
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Set Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
