package com.example.ui.more

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.SnapTaskViewModel
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    viewModel: SnapTaskViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val autoDetect by viewModel.preferences.autoDetectionEnabled.collectAsState()
    val notifsEnabled by viewModel.preferences.notificationsEnabled.collectAsState()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showDataStatsDialog by remember { mutableStateOf(false) }
    var totalScreenshots by remember { mutableStateOf(0) }
    var totalActions by remember { mutableStateOf(0) }

    // Permission launcher for auto detection
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.preferences.setAutoDetectionEnabled(true)
            Toast.makeText(context, "Automatic screenshot detection enabled", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.preferences.setAutoDetectionEnabled(false)
            Toast.makeText(context, "Storage permission is needed to detect screenshots automatically", Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("more_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 80.dp)
        ) {
            // Screen Title
            Text(
                text = "More",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: General Settings
            SectionHeader(title = "General")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggleRow(
                        title = "Automatic screenshot detection",
                        subtitle = "Analyze new screenshots immediately when captured",
                        isChecked = autoDetect,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val requiredPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                if (viewModel.hasStoragePermission()) {
                                    viewModel.preferences.setAutoDetectionEnabled(true)
                                    Toast.makeText(context, "Automatic screenshot detection enabled", Toast.LENGTH_SHORT).show()
                                } else {
                                    permissionLauncher.launch(requiredPerm)
                                }
                            } else {
                                viewModel.preferences.setAutoDetectionEnabled(false)
                            }
                        },
                        testTag = "toggle_auto_detection"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingToggleRow(
                        title = "Action notifications",
                        subtitle = "Receive alert reminders for detected events and dates",
                        isChecked = notifsEnabled,
                        onCheckedChange = { viewModel.preferences.setNotificationsEnabled(it) },
                        testTag = "toggle_notifications"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Privacy
            SectionHeader(title = "Privacy & Local-First")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your screenshots belong to you.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    PrivacyFeatureItem(text = "100% on-device OCR & entity extraction")
                    PrivacyFeatureItem(text = "No account required, no passwords")
                    PrivacyFeatureItem(text = "Zero cloud uploads or external server sync")
                    PrivacyFeatureItem(text = "No advertising tracking reading your images")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    val stats = viewModel.getStats()
                                    totalScreenshots = stats.first
                                    totalActions = stats.second
                                    showDataStatsDialog = true
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "View processed local data stats",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDataDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Delete all data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: About
            SectionHeader(title = "About SnapTask")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SnapTask v1.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Turn forgotten screenshots into organized, actionable information.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Local-first on-device processing • ML Kit OCR • Room SQLite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // View Processed Data Stats Dialog
    if (showDataStatsDialog) {
        AlertDialog(
            onDismissRequest = { showDataStatsDialog = false },
            title = {
                Text("Local Data Storage", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text("Screenshots stored locally: $totalScreenshots")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Actions created: $totalActions")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Local database: snaptask.db (Room SQLite)")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "All images and extracted entities remain strictly on your local device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusSuccess
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDataStatsDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = {
                Text("Delete all data?", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text("This will permanently remove all processed screenshots, entities, and actions from your local phone storage.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                        Toast.makeText(context, "All local data deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun PrivacyFeatureItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = StatusSuccess,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
