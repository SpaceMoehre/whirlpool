package com.whirlpool.app.ui

import android.graphics.Color.parseColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whirlpool.app.data.SourceServerConfig

private data class SettingsPalette(
    val section: Color,
    val row: Color,
    val title: Color,
    val subtitle: Color,
)

private fun settingsPalette(dark: Boolean): SettingsPalette {
    return if (dark) {
        SettingsPalette(
            section = Color(0xFF1C1D22),
            row = Color(0xFF2A2B31),
            title = Color(0xFFF3F4F7),
            subtitle = Color(0xFFB4B7C0),
        )
    } else {
        SettingsPalette(
            section = Color.White,
            row = Color(0xFFF3F4F7),
            title = Color(0xFF121418),
            subtitle = Color(0xFF717681),
        )
    }
}

private sealed interface SettingsDestination {
    data object Menu : SettingsDestination
    data object General : SettingsDestination
    data object Playback : SettingsDestination
    data object Privacy : SettingsDestination
    data object Following : SettingsDestination
    data object Data : SettingsDestination
    data object Sources : SettingsDestination
    data class Placeholder(val title: String) : SettingsDestination
}

private data class SettingsMenuEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val destination: SettingsDestination,
)

@Composable
fun SettingsRootSheet(
    darkModeEnabled: Boolean,
    settings: AppSettings,
    sources: List<SourceServerConfig>,
    logs: List<String>,
    onBooleanSettingChange: (String, Boolean) -> Unit,
    onTextSettingChange: (String, String) -> Unit,
    onThemeSelected: (String) -> Unit,
    onAddSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onActivateSource: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddUploader: (String) -> Unit,
    onRemoveUploader: (String) -> Unit,
    onClearCache: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearWatchHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onClearAchievements: () -> Unit,
    onResetAllData: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    var destination by remember { mutableStateOf<SettingsDestination>(SettingsDestination.Menu) }

    BackHandler(enabled = destination != SettingsDestination.Menu) {
        destination = SettingsDestination.Menu
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (destination == SettingsDestination.Menu) onClose() else destination = SettingsDestination.Menu
                },
            ) {
                Icon(
                    imageVector = if (destination == SettingsDestination.Menu) Icons.Default.Close else Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.title,
                )
            }
            Text(
                text = when (val page = destination) {
                    SettingsDestination.Menu -> "Settings"
                    SettingsDestination.General -> "General"
                    SettingsDestination.Playback -> "Playback"
                    SettingsDestination.Privacy -> "Privacy"
                    SettingsDestination.Following -> "Following"
                    SettingsDestination.Data -> "Data"
                    SettingsDestination.Sources -> "Sources"
                    is SettingsDestination.Placeholder -> page.title
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Spacer(modifier = Modifier.width(44.dp))
        }

        when (val page = destination) {
            SettingsDestination.Menu -> SettingsMenuPage(
                darkModeEnabled = darkModeEnabled,
                onOpen = { destination = it },
            )
            SettingsDestination.General -> GeneralSettingsPage(
                darkModeEnabled = darkModeEnabled,
                settings = settings,
                onBooleanSettingChange = onBooleanSettingChange,
                onTextSettingChange = onTextSettingChange,
                onThemeSelected = onThemeSelected,
                onOpenPlaceholder = { destination = SettingsDestination.Placeholder(it) },
            )
            SettingsDestination.Playback -> PlaybackSettingsPage(
                darkModeEnabled = darkModeEnabled,
                settings = settings,
                onBooleanSettingChange = onBooleanSettingChange,
                onTextSettingChange = onTextSettingChange,
            )
            SettingsDestination.Privacy -> PrivacySettingsPage(
                darkModeEnabled = darkModeEnabled,
                settings = settings,
                onBooleanSettingChange = onBooleanSettingChange,
            )
            SettingsDestination.Following -> FollowingSettingsPage(
                darkModeEnabled = darkModeEnabled,
                settings = settings,
                onAddTag = onAddTag,
                onRemoveTag = onRemoveTag,
                onAddUploader = onAddUploader,
                onRemoveUploader = onRemoveUploader,
            )
            SettingsDestination.Data -> DataSettingsPage(
                darkModeEnabled = darkModeEnabled,
                onClearCache = onClearCache,
                onExport = onExport,
                onImport = onImport,
                onClearWatchHistory = onClearWatchHistory,
                onClearFavorites = onClearFavorites,
                onClearAchievements = onClearAchievements,
                onResetAllData = onResetAllData,
            )
            SettingsDestination.Sources -> SourcesSettingsPage(
                darkModeEnabled = darkModeEnabled,
                sources = sources,
                onAddSource = onAddSource,
                onRemoveSource = onRemoveSource,
                onActivateSource = onActivateSource,
            )
            is SettingsDestination.Placeholder -> PlaceholderSettingsPage(
                darkModeEnabled = darkModeEnabled,
                title = page.title,
                logs = if (page.title == "Advanced Settings") logs else emptyList(),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsMenuPage(
    darkModeEnabled: Boolean,
    onOpen: (SettingsDestination) -> Unit,
) {
    val menuGroups = listOf(
        "General" to listOf(
            SettingsMenuEntry("General", "Manage app preferences for Hot Tub", Icons.Default.Settings, Color(0xFF9FA4AC), SettingsDestination.General),
            SettingsMenuEntry("Playback", "Adjust audio and video preferences", Icons.Default.PlayArrow, Color(0xFF2196F3), SettingsDestination.Playback),
            SettingsMenuEntry("Privacy", "Secure Hot Tub and manage your data", Icons.Default.Lock, Color(0xFFFF5252), SettingsDestination.Privacy),
            SettingsMenuEntry("Filters & Blocks", "Uploaders, keywords, and more", Icons.Default.Policy, Color(0xFF22C55E), SettingsDestination.Placeholder("Filters & Blocks")),
            SettingsMenuEntry("Following", "Tags and uploaders you want to see", Icons.Default.Favorite, Color(0xFF6D7DFE), SettingsDestination.Following),
            SettingsMenuEntry("Data & Storage", "Manage data, caches, and backups", Icons.Default.Storage, Color(0xFFF39C12), SettingsDestination.Data),
        ),
        "Sources" to listOf(
            SettingsMenuEntry("Sources", "Configure additional content networks & servers", Icons.Default.Language, Color(0xFF27C2C7), SettingsDestination.Sources),
        ),
        "Feedback & Support" to listOf(
            SettingsMenuEntry("Help & Feedback", "Report bugs, get support, request features", Icons.Default.Help, Color(0xFFE15CF5), SettingsDestination.Placeholder("Help & Feedback")),
            SettingsMenuEntry("Rate Us", "Submit a review to help others find our app", Icons.Default.Star, Color(0xFFFACC15), SettingsDestination.Placeholder("Rate Us")),
            SettingsMenuEntry("Contact Us", "Report bugs, request features, or ask questions", Icons.Default.Forum, Color(0xFF2EA7FF), SettingsDestination.Placeholder("Contact Us")),
            SettingsMenuEntry("Discord", "Join the conversation and ask questions", Icons.Default.Notifications, Color(0xFF7A87FF), SettingsDestination.Placeholder("Discord")),
        ),
        "Info" to listOf(
            SettingsMenuEntry("What's New?", "See all changes in this version", Icons.Default.Info, Color(0xFF4ADE80), SettingsDestination.Placeholder("What's New?")),
            SettingsMenuEntry("Website", "Browse and share hottubapp.io", Icons.Default.Public, Color(0xFF0EA5E9), SettingsDestination.Placeholder("Website")),
            SettingsMenuEntry("FAQ", "Frequently Asked Questions", Icons.Default.Help, Color(0xFFF59E0B), SettingsDestination.Placeholder("FAQ")),
            SettingsMenuEntry("Legal", "Privacy Policy, TOS, and more", Icons.Default.Gavel, Color(0xFFA1A1AA), SettingsDestination.Placeholder("Legal")),
        ),
        "Extra" to listOf(
            SettingsMenuEntry("Health & Safety Resources", "Explore resources to support your well-being", Icons.Default.Security, Color(0xFF0EA5E9), SettingsDestination.Placeholder("Health & Safety Resources")),
            SettingsMenuEntry("Donate", "Consider showing your support by donating", Icons.Default.VolunteerActivism, Color(0xFFF43F5E), SettingsDestination.Placeholder("Donate")),
        ),
    )

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        menuGroups.forEach { (sectionTitle, rows) ->
            SettingsSection(
                darkModeEnabled = darkModeEnabled,
                title = sectionTitle,
            ) {
                rows.forEach { row ->
                    SettingsNavRow(
                        darkModeEnabled = darkModeEnabled,
                        title = row.title,
                        subtitle = row.subtitle,
                        icon = row.icon,
                        iconColor = row.iconColor,
                        onClick = { onOpen(row.destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    darkModeEnabled: Boolean,
    settings: AppSettings,
    onBooleanSettingChange: (String, Boolean) -> Unit,
    onTextSettingChange: (String, String) -> Unit,
    onThemeSelected: (String) -> Unit,
    onOpenPlaceholder: (String) -> Unit,
) {
    var showHandPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection(darkModeEnabled, "App Preferences") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Dominant Hand",
                subtitle = "Choose your preferred hand mode.",
                value = settings.dominantHand,
                icon = Icons.Default.Tune,
                iconColor = Color(0xFF2196F3),
                onClick = { showHandPicker = true },
            )
            SettingsToggleRow(darkModeEnabled, "Enable Haptics", "Provide tactile feedback for interactions.", settings.enableHaptics, Icons.Default.Notifications, Color(0xFFFF9800)) {
                onBooleanSettingChange(SettingKeys.ENABLE_HAPTICS, it)
            }
            SettingsToggleRow(darkModeEnabled, "Detailed Alerts", "Present alerts on important events.", settings.detailedAlerts, Icons.Default.Notifications, Color(0xFF2196F3)) {
                onBooleanSettingChange(SettingKeys.DETAILED_ALERTS, it)
            }
            SettingsToggleRow(darkModeEnabled, "Show Extraction Toast", "Show toast while extracting video details.", settings.showExtractionToast, Icons.Default.Info, Color(0xFF22D3EE)) {
                onBooleanSettingChange(SettingKeys.SHOW_EXTRACTION_TOAST, it)
            }
            SettingsToggleRow(darkModeEnabled, "Auto Preview", "Automatically preview videos while scrolling.", settings.autoPreview, Icons.Default.PlayArrow, Color(0xFFD946EF)) {
                onBooleanSettingChange(SettingKeys.AUTO_PREVIEW, it)
            }
            SettingsToggleRow(darkModeEnabled, "Stats & Achievements", "Display your stats and unlocked badges.", settings.statsAndAchievements, Icons.Default.Star, Color(0xFFFACC15)) {
                onBooleanSettingChange(SettingKeys.STATS_AND_ACHIEVEMENTS, it)
            }
            SettingsToggleRow(darkModeEnabled, "Crash Recovery", "Automatically restore your last session.", settings.crashRecovery, Icons.Default.Security, Color(0xFFEF4444)) {
                onBooleanSettingChange(SettingKeys.CRASH_RECOVERY, it)
            }
        }

        SettingsSection(darkModeEnabled, "Viewing Preferences") {
            SettingsToggleRow(darkModeEnabled, "Categories Section", "Show categories on home page.", settings.categoriesSection, Icons.Default.Tune, Color(0xFF22C55E)) {
                onBooleanSettingChange(SettingKeys.CATEGORIES_SECTION, it)
            }
            SettingsToggleRow(darkModeEnabled, "Favorites Section", "Show favorites section on home page.", settings.favoritesSection, Icons.Default.Favorite, Color(0xFFF43F5E)) {
                onBooleanSettingChange(SettingKeys.FAVORITES_SECTION, it)
            }
            SettingsToggleRow(darkModeEnabled, "Viewing History", "Enable watch history display and storage.", settings.viewingHistory, Icons.Default.Public, Color(0xFF6D7DFE)) {
                onBooleanSettingChange(SettingKeys.VIEWING_HISTORY, it)
            }
            SettingsToggleRow(darkModeEnabled, "Search History", "Save searches and use them for suggestions.", settings.searchHistory, Icons.Default.Search, Color(0xFFF59E0B)) {
                onBooleanSettingChange(SettingKeys.SEARCH_HISTORY, it)
            }
            SettingsToggleRow(darkModeEnabled, "Video Row Details", "Show author and views below thumbnails.", settings.videoRowDetails, Icons.Default.Info, Color(0xFF2196F3)) {
                onBooleanSettingChange(SettingKeys.VIDEO_ROW_DETAILS, it)
            }
        }

        SettingsSection(darkModeEnabled, "Appearance") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Theme",
                subtitle = "Select app theme mode.",
                value = settings.theme,
                icon = Icons.Default.Policy,
                iconColor = Color(0xFF6D7DFE),
                onClick = { showThemePicker = true },
            )
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Palette",
                subtitle = "Current accent palette.",
                value = settings.palette,
                icon = Icons.Default.Tune,
                iconColor = Color(0xFF0EA5E9),
                onClick = {
                    val next = when (settings.palette) {
                        "Blue" -> "Ocean"
                        "Ocean" -> "Amber"
                        else -> "Blue"
                    }
                    onTextSettingChange(SettingKeys.PALETTE, next)
                },
            )
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Alternate Icons",
                subtitle = "Choose an alternate app icon.",
                value = "",
                icon = Icons.Default.Star,
                iconColor = Color(0xFFA1A1AA),
                onClick = { onOpenPlaceholder("Alternate Icons") },
            )
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Preferred Language",
                subtitle = "Choose preferred app language.",
                value = "",
                icon = Icons.Default.Language,
                iconColor = Color(0xFF0EA5E9),
                onClick = { onOpenPlaceholder("Preferred Language") },
            )
        }

        SettingsSection(darkModeEnabled, "More") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Advanced Settings",
                subtitle = "Configure more technical settings.",
                value = "",
                icon = Icons.Default.Settings,
                iconColor = Color(0xFFA1A1AA),
                onClick = { onOpenPlaceholder("Advanced Settings") },
            )
        }
    }

    if (showHandPicker) {
        ChoiceDialog(
            title = "Dominant Hand",
            options = listOf("Righty", "Lefty"),
            selected = settings.dominantHand,
            onSelect = {
                onTextSettingChange(SettingKeys.DOMINANT_HAND, it)
                showHandPicker = false
            },
            onDismiss = { showHandPicker = false },
        )
    }

    if (showThemePicker) {
        ChoiceDialog(
            title = "Theme",
            options = listOf("Dark", "Light"),
            selected = settings.theme,
            onSelect = {
                onThemeSelected(it)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun PlaybackSettingsPage(
    darkModeEnabled: Boolean,
    settings: AppSettings,
    onBooleanSettingChange: (String, Boolean) -> Unit,
    onTextSettingChange: (String, String) -> Unit,
) {
    var showResolutionPicker by remember { mutableStateOf(false) }
    var showFormatPicker by remember { mutableStateOf(false) }
    var showSkipPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection(darkModeEnabled, "Video Quality") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Preferred Resolution",
                subtitle = "Select the preferred quality to load videos.",
                value = settings.preferredResolution,
                icon = Icons.Default.PlayArrow,
                iconColor = Color(0xFFF97316),
                onClick = { showResolutionPicker = true },
            )
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Preferred Format",
                subtitle = "Select preferred video format.",
                value = settings.preferredFormat,
                icon = Icons.Default.Policy,
                iconColor = Color(0xFF22D3EE),
                onClick = { showFormatPicker = true },
            )
        }

        SettingsSection(darkModeEnabled, "Player Behavior") {
            SettingsToggleRow(darkModeEnabled, "Loop Playback", "Automatically restart a video when it ends.", settings.loopPlayback, Icons.Default.PlayArrow, Color(0xFFF43F5E)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_LOOP, it)
            }
            SettingsToggleRow(darkModeEnabled, "Picture in Picture", "Enable PiP in app and when exiting.", settings.pictureInPicture, Icons.Default.Public, Color(0xFF0EA5E9)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_PIP, it)
            }
            SettingsToggleRow(darkModeEnabled, "Use System Player", "Use external player instead of custom player.", settings.useSystemPlayer, Icons.Default.PlayArrow, Color(0xFF6D7DFE)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_SYSTEM_PLAYER, it)
            }
        }

        SettingsSection(darkModeEnabled, "Controls & Gestures") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Skip Duration",
                subtitle = "Duration used for skip buttons.",
                value = settings.skipDuration,
                icon = Icons.Default.Tune,
                iconColor = Color(0xFFD946EF),
                onClick = { showSkipPicker = true },
            )
        }

        SettingsSection(darkModeEnabled, "Audio") {
            SettingsToggleRow(darkModeEnabled, "Audio Output Notification", "Notify when output device changes.", settings.audioOutputNotification, Icons.Default.Notifications, Color(0xFF0EA5E9)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_AUDIO_OUTPUT_NOTIFICATION, it)
            }
            SettingsToggleRow(darkModeEnabled, "Block Audio Output Changes", "Prevent accidental output switches.", settings.blockAudioOutputChanges, Icons.Default.Block, Color(0xFFEF4444)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_BLOCK_AUDIO_OUTPUT_CHANGES, it)
            }
            SettingsToggleRow(darkModeEnabled, "Audio Normalization", "Reduce loudness spikes.", settings.audioNormalization, Icons.Default.GraphicEq, Color(0xFF22C55E)) {
                onBooleanSettingChange(SettingKeys.PLAYBACK_AUDIO_NORMALIZATION, it)
            }
        }
    }

    if (showResolutionPicker) {
        ChoiceDialog(
            title = "Preferred Resolution",
            options = listOf("2160p", "1440p", "1080p", "720p", "480p"),
            selected = settings.preferredResolution,
            onSelect = {
                onTextSettingChange(SettingKeys.PLAYBACK_PREFERRED_RESOLUTION, it)
                showResolutionPicker = false
            },
            onDismiss = { showResolutionPicker = false },
        )
    }
    if (showFormatPicker) {
        ChoiceDialog(
            title = "Preferred Format",
            options = listOf("HEVC", "AVC", "WebM"),
            selected = settings.preferredFormat,
            onSelect = {
                onTextSettingChange(SettingKeys.PLAYBACK_PREFERRED_FORMAT, it)
                showFormatPicker = false
            },
            onDismiss = { showFormatPicker = false },
        )
    }
    if (showSkipPicker) {
        ChoiceDialog(
            title = "Skip Duration",
            options = listOf("Automatic", "5s", "10s", "15s", "30s"),
            selected = settings.skipDuration,
            onSelect = {
                onTextSettingChange(SettingKeys.PLAYBACK_SKIP_DURATION, it)
                showSkipPicker = false
            },
            onDismiss = { showSkipPicker = false },
        )
    }
}

@Composable
private fun PrivacySettingsPage(
    darkModeEnabled: Boolean,
    settings: AppSettings,
    onBooleanSettingChange: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection(darkModeEnabled, "Access & Protection") {
            SettingsToggleRow(darkModeEnabled, "Show Lock Screen", "Automatically hide content when app is idle.", settings.showLockScreen, Icons.Default.Lock, Color(0xFF6D7DFE)) {
                onBooleanSettingChange(SettingKeys.PRIVACY_SHOW_LOCK_SCREEN, it)
            }
            SettingsToggleRow(darkModeEnabled, "Unlock with FaceID", "Require biometric unlock.", settings.unlockWithFaceId, Icons.Default.Security, Color(0xFF22C55E)) {
                onBooleanSettingChange(SettingKeys.PRIVACY_UNLOCK_FACE_ID, it)
            }
            SettingsToggleRow(darkModeEnabled, "Blur on Screen Capture", "Blur content for screenshots and recordings.", settings.blurOnScreenCapture, Icons.Default.VisibilityOff, Color(0xFFF97316)) {
                onBooleanSettingChange(SettingKeys.PRIVACY_BLUR_SCREEN_CAPTURE, it)
            }
        }

        SettingsSection(darkModeEnabled, "Telemetry") {
            SettingsToggleRow(darkModeEnabled, "Enable Analytics", "Share anonymous app usage metrics.", settings.enableAnalytics, Icons.Default.ShowChart, Color(0xFFF59E0B)) {
                onBooleanSettingChange(SettingKeys.PRIVACY_ENABLE_ANALYTICS, it)
            }
            SettingsToggleRow(darkModeEnabled, "Enable Crash Reporting", "Send anonymous crash reports.", settings.enableCrashReporting, Icons.Default.BugReport, Color(0xFFEF4444)) {
                onBooleanSettingChange(SettingKeys.PRIVACY_ENABLE_CRASH_REPORTING, it)
            }
        }
    }
}

@Composable
private fun FollowingSettingsPage(
    darkModeEnabled: Boolean,
    settings: AppSettings,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddUploader: (String) -> Unit,
    onRemoveUploader: (String) -> Unit,
) {
    var showTagDialog by remember { mutableStateOf(false) }
    var showUploaderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection(darkModeEnabled, "Followed Tags") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Add Tag",
                subtitle = "",
                value = "",
                icon = Icons.Default.Add,
                iconColor = Color(0xFF22C55E),
                onClick = { showTagDialog = true },
            )
            settings.followedTags.forEach { tag ->
                SettingsValueRow(
                    darkModeEnabled = darkModeEnabled,
                    title = tag,
                    subtitle = "",
                    value = "Remove",
                    icon = Icons.Default.Star,
                    iconColor = Color(0xFF6D7DFE),
                    onClick = { onRemoveTag(tag) },
                )
            }
        }

        SettingsSection(darkModeEnabled, "Followed Uploaders") {
            SettingsValueRow(
                darkModeEnabled = darkModeEnabled,
                title = "Add Uploader",
                subtitle = "",
                value = "",
                icon = Icons.Default.Add,
                iconColor = Color(0xFF0EA5E9),
                onClick = { showUploaderDialog = true },
            )
            settings.followedUploaders.forEach { uploader ->
                SettingsValueRow(
                    darkModeEnabled = darkModeEnabled,
                    title = uploader,
                    subtitle = "",
                    value = "Remove",
                    icon = Icons.Default.Notifications,
                    iconColor = Color(0xFF6D7DFE),
                    onClick = { onRemoveUploader(uploader) },
                )
            }
        }
    }

    if (showTagDialog) {
        InputDialog(
            title = "Add Tag",
            hint = "Tag",
            onSubmit = {
                onAddTag(it)
                showTagDialog = false
            },
            onDismiss = { showTagDialog = false },
        )
    }

    if (showUploaderDialog) {
        InputDialog(
            title = "Add Uploader",
            hint = "Uploader",
            onSubmit = {
                onAddUploader(it)
                showUploaderDialog = false
            },
            onDismiss = { showUploaderDialog = false },
        )
    }
}

@Composable
private fun DataSettingsPage(
    darkModeEnabled: Boolean,
    onClearCache: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearWatchHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    onClearAchievements: () -> Unit,
    onResetAllData: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection(darkModeEnabled, "Cache") {
            SettingsActionRow(darkModeEnabled, "Clear cache", "Clear all cached data", Icons.Default.Delete, Color(0xFFEF4444), onClearCache)
        }

        SettingsSection(darkModeEnabled, "Data") {
            SettingsActionRow(darkModeEnabled, "Export Database", "Export a backup of your data", Icons.Default.FileUpload, Color(0xFF6D7DFE), onExport)
            SettingsActionRow(darkModeEnabled, "Import Database", "Import a previously exported backup", Icons.Default.FileDownload, Color(0xFF0EA5E9), onImport)
        }

        SettingsSection(darkModeEnabled, "Data Control") {
            SettingsActionRow(darkModeEnabled, "Clear Watch History", "Clear videos marked as watched", Icons.Default.History, Color(0xFF6D7DFE), onClearWatchHistory)
            SettingsActionRow(darkModeEnabled, "Clear Favorites", "Clear all favorited videos", Icons.Default.Favorite, Color(0xFFF43F5E), onClearFavorites)
            SettingsActionRow(darkModeEnabled, "Delete Achievements", "Delete stats and achievements", Icons.Default.Star, Color(0xFFFACC15), onClearAchievements)
        }

        SettingsSection(darkModeEnabled, "Danger Zone") {
            SettingsActionRow(darkModeEnabled, "Delete & Reset All Data", "Remove all favorites, history, and settings", Icons.Default.Warning, Color(0xFFEF4444)) {
                showResetConfirm = true
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset All Data") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetAllData()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SourcesSettingsPage(
    darkModeEnabled: Boolean,
    sources: List<SourceServerConfig>,
    onAddSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onActivateSource: (String) -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    var sourceInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Enter the URL of a Hot Tub-compatible source. If no protocol is set, HTTPS is tested first.",
            color = palette.subtitle,
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = sourceInput,
                onValueChange = { sourceInput = it },
                placeholder = { Text("Source URI") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val input = sourceInput.trim()
                        if (input.isNotEmpty()) {
                            onAddSource(input)
                            sourceInput = ""
                        }
                    },
                ),
            )
            IconButton(onClick = {
                val input = sourceInput.trim()
                if (input.isNotEmpty()) {
                    onAddSource(input)
                    sourceInput = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add source", tint = palette.title)
            }
        }

        Text(
            text = "Custom Sources",
            color = palette.subtitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        sources.forEach { source ->
            val rowColor = parseSourceColor(source.color, darkModeEnabled)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowColor, RoundedCornerShape(14.dp))
                    .clickable { onActivateSource(source.baseUrl) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    if (!source.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = source.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = source.baseUrl,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (source.isActive) {
                    Text(
                        text = "Active",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = { onRemoveSource(source.baseUrl) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove source", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderSettingsPage(
    darkModeEnabled: Boolean,
    title: String,
    logs: List<String>,
) {
    val palette = settingsPalette(darkModeEnabled)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = palette.section),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.title,
                    fontWeight = FontWeight.SemiBold,
                )
                if (logs.isEmpty()) {
                    Text(
                        text = "This submenu is intentionally empty for now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.subtitle,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    logs.takeLast(40).forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    darkModeEnabled: Boolean,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    Text(
        text = title,
        color = palette.subtitle,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.section),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsNavRow(
    darkModeEnabled: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.row, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon, background = iconColor)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = palette.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = palette.subtitle)
    }
}

@Composable
private fun SettingsToggleRow(
    darkModeEnabled: Boolean,
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    iconColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.row, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon, background = iconColor)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = palette.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsValueRow(
    darkModeEnabled: Boolean,
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    val palette = settingsPalette(darkModeEnabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.row, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon, background = iconColor)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = palette.title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = palette.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = palette.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = palette.subtitle)
    }
}

@Composable
private fun SettingsActionRow(
    darkModeEnabled: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    SettingsValueRow(
        darkModeEnabled = darkModeEnabled,
        title = title,
        subtitle = subtitle,
        value = "",
        icon = icon,
        iconColor = iconColor,
        onClick = onClick,
    )
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    background: Color,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(background, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(option) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun InputDialog(
    title: String,
    hint: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(hint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) {
                        onSubmit(trimmed)
                    }
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    onSubmit(trimmed)
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun parseSourceColor(colorValue: String?, darkModeEnabled: Boolean): Color {
    if (colorValue.isNullOrBlank()) {
        return if (darkModeEnabled) Color(0xFF4F46E5) else Color(0xFF6366F1)
    }

    val normalized = colorValue.trim()
    if (normalized.startsWith("#")) {
        return runCatching { Color(parseColor(normalized)) }
            .getOrElse { if (darkModeEnabled) Color(0xFF4F46E5) else Color(0xFF6366F1) }
    }

    return when (normalized.lowercase()) {
        "indigo" -> Color(0xFF6366F1)
        "blue" -> Color(0xFF0EA5E9)
        "green" -> Color(0xFF22C55E)
        "orange" -> Color(0xFFF97316)
        "red" -> Color(0xFFEF4444)
        else -> if (darkModeEnabled) Color(0xFF4F46E5) else Color(0xFF6366F1)
    }
}
