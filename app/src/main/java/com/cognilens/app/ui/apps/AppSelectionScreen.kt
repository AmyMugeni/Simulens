package com.cognilens.app.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognilens.app.data.apps.InstalledApp
import com.cognilens.app.data.apps.Suggestion
import com.cognilens.app.ui.components.AppIcon

@Composable
fun AppSelectionScreen(
    viewModel: AppSelectionViewModel = viewModel(),
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    val filtered = remember(state.apps, state.query) {
        state.apps.filter { it.label.contains(state.query.trim(), ignoreCase = true) }
    }
    val feeds = remember(filtered) { filtered.filter { it.suggestion == Suggestion.FEED } }
    val others = remember(filtered) { filtered.filter { it.suggestion == Suggestion.OTHER_SOCIAL } }
    val rest = remember(filtered) { filtered.filter { it.suggestion == Suggestion.NONE } }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = colors.surfaceContainerLow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    if (state.selected.isEmpty() && !state.isLoading) {
                        Text(
                            text = "No apps selected, so nothing will be tracked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = { viewModel.save(onDone) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save (${state.selected.size} selected)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = "Which apps should CogniLens track?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pick the apps you feel pull you in. This stays on your device and you can change it any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    section("Social feeds", feeds, state.selected, viewModel::toggle)
                    section("Messaging and video (your call)", others, state.selected, viewModel::toggle)
                    section("All other apps", rest, state.selected, viewModel::toggle)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    if (apps.isEmpty()) return
    item(key = "header_$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
    }
    items(apps, key = { it.packageName }) { app ->
        AppRow(app = app, checked = app.packageName in selected, onToggle = { onToggle(app.packageName) })
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}