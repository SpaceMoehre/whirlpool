package com.whirlpool.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.whirlpool.app.R
import com.whirlpool.app.player.VideoPlayerControls
import com.whirlpool.app.player.VideoPlayerSurface
import com.whirlpool.engine.VideoItem
import kotlinx.coroutines.delay

private val CategoryGradients = listOf(
    listOf(Color(0xFF0A83E8), Color(0xFF32A9D4)),
    listOf(Color(0xFF22C55E), Color(0xFF14B8A6)),
    listOf(Color(0xFFF59E0B), Color(0xFFF43F5E)),
    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
)

private data class MenuPalette(
    val sheet: Color,
    val section: Color,
    val row: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val actionText: Color,
)

private fun menuPalette(darkModeEnabled: Boolean): MenuPalette {
    return if (darkModeEnabled) {
        MenuPalette(
            sheet = Color(0xFF101012),
            section = Color(0xFF1A1A1F),
            row = Color(0xFF26262C),
            divider = Color(0xFF34343C),
            primaryText = Color(0xFFF3F4F7),
            secondaryText = Color(0xFFB4B7C0),
            actionText = Color(0xFF8AB4FF),
        )
    } else {
        MenuPalette(
            sheet = Color(0xFFF2F2F7),
            section = Color.White,
            row = Color.White,
            divider = Color(0xFFE5E5EA),
            primaryText = Color(0xFF111111),
            secondaryText = Color(0xFF8E8E93),
            actionText = Color(0xFF0A84FF),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhirlpoolScreen(
    viewModel: WhirlpoolViewModel,
    darkModeEnabled: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (state.streamUrl != null) {
        PlayerMode(
            title = state.selectedVideo?.title ?: "Now Playing",
            streamUrl = state.streamUrl,
            requestHeaders = state.streamHeaders,
            onPlaybackError = viewModel::onPlayerError,
            onPlaybackEvent = viewModel::onPlayerEvent,
            onClose = viewModel::dismissPlayer,
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    HeaderRow(
                        onSettings = { showSettings = true },
                        onFilters = { showFilters = true },
                    )
                }

                item {
                    SearchBar(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = viewModel::search,
                    )
                }

                item {
                    SectionTitle("Categories")
                    CategoriesRow(state.categories)
                }

                item {
                    SectionTitle("Favorites")
                    if (state.favorites.isNotEmpty()) {
                        VideosRow(
                            videos = state.favorites,
                            onPlay = viewModel::playVideo,
                            onFavoriteToggle = viewModel::toggleFavorite,
                            favorites = state.favorites,
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                item {
                    SectionTitle("Videos")
                    state.errorText?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    state.actionText?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                items(state.videos, key = { it.id }) { video ->
                    val isFavorite = state.favorites.any { it.id == video.id }
                    MainVideoCard(
                        video = video,
                        isFavorite = isFavorite,
                        onPlay = { viewModel.playVideo(video) },
                        onFavorite = { viewModel.toggleFavorite(video) },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        if (showFilters) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = sheetState,
                containerColor = menuPalette(darkModeEnabled).sheet,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                FiltersSheet(
                    darkModeEnabled = darkModeEnabled,
                    serverHost = "getfigleaf.com",
                    channelName = state.activeChannel,
                    onClose = { showFilters = false },
                    onExport = viewModel::exportDatabase,
                    onImport = viewModel::importDatabase,
                )
            }
        }

        if (showSettings) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = sheetState,
                containerColor = menuPalette(darkModeEnabled).sheet,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                SettingsSheet(
                    darkModeEnabled = darkModeEnabled,
                    onDarkModeToggle = onDarkModeToggle,
                    logs = state.logs,
                    onClearLogs = viewModel::clearLogs,
                    onClose = { showSettings = false },
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(onSettings: () -> Unit, onFilters: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "⚙",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onSettings),
        )

        Text(
            text = "Whirlpool",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Center),
        )

        Text(
            text = "◍",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onFilters),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        trailingIcon = {
            Text(
                text = "Go",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun CategoriesRow(categories: List<String>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(modifier = Modifier.width(6.dp)) }
        items(categories) { title ->
            val gradient = CategoryGradients[title.hashCode().absoluteValue % CategoryGradients.size]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(gradient))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        item { Spacer(modifier = Modifier.width(6.dp)) }
    }
}

@Composable
private fun VideosRow(
    videos: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onFavoriteToggle: (VideoItem) -> Unit,
    favorites: List<VideoItem>,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(modifier = Modifier.width(6.dp)) }
        items(videos, key = { it.id }) { video ->
            val isFavorite = favorites.any { it.id == video.id }
            VideoCard(
                video = video,
                isFavorite = isFavorite,
                onPlay = { onPlay(video) },
                onFavorite = { onFavoriteToggle(video) },
            )
        }
        item { Spacer(modifier = Modifier.width(6.dp)) }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onPlay),
    ) {
        Box {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )

            Text(
                text = if (isFavorite) "♥" else "♡",
                color = if (isFavorite) Color(0xFFFF3B30) else Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clickable(onClick = onFavorite),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MainVideoCard(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPlay)
            .padding(10.dp),
    ) {
        Box {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )

            Text(
                text = if (isFavorite) "♥" else "♡",
                color = if (isFavorite) Color(0xFFFF3B30) else Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clickable(onClick = onFavorite),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${video.authorName ?: "Unknown"}  ·  ${video.viewCount ?: 0u} views",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSheet(
    darkModeEnabled: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    logs: List<String>,
    onClearLogs: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = menuPalette(darkModeEnabled)

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
            Text("Close", color = palette.actionText, modifier = Modifier.clickable(onClick = onClose))
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText,
            )
            Spacer(modifier = Modifier.width(44.dp))
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.section),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.row)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Dark Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primaryText,
                    )
                    Text(
                        "Use a dark color theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryText,
                    )
                }
                Switch(
                    checked = darkModeEnabled,
                    onCheckedChange = onDarkModeToggle,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = palette.section),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Text(
                        "Debug Logs",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primaryText,
                    )
                    Text(
                        "Clear",
                        color = palette.actionText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onClearLogs),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.row)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "No logs yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryText,
                        )
                    } else {
                        logs.reversed().forEach { entry ->
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.primaryText.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FiltersSheet(
    darkModeEnabled: Boolean,
    serverHost: String,
    channelName: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val palette = menuPalette(darkModeEnabled)

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
            Text("Close", color = palette.actionText, modifier = Modifier.clickable(onClick = onClose))
            Text(
                "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText,
            )
            Spacer(modifier = Modifier.width(44.dp))
        }

        FilterSection(
            darkModeEnabled = darkModeEnabled,
            title = "NETWORK",
            rows = listOf(
                "Server" to serverHost,
                "Channel" to channelName,
            ),
        )

        FilterSection(
            darkModeEnabled = darkModeEnabled,
            title = "FILTERS",
            rows = listOf(
                "Sort" to "Top Rated",
                "Duration" to "Short (<10 min)",
                "Production Type" to "All Content",
                "Sexuality" to "Straight",
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill(darkModeEnabled = darkModeEnabled, label = "Export", onClick = onExport)
            ActionPill(darkModeEnabled = darkModeEnabled, label = "Import", onClick = onImport)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FilterSection(darkModeEnabled: Boolean, title: String, rows: List<Pair<String, String>>) {
    val palette = menuPalette(darkModeEnabled)

    Text(
        text = title,
        color = palette.secondaryText,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.section),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.row)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.first,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primaryText,
                    )
                    Text(
                        row.second,
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (index < rows.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(darkModeEnabled: Boolean, label: String, onClick: () -> Unit) {
    val palette = menuPalette(darkModeEnabled)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = palette.actionText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlayerMode(
    title: String,
    streamUrl: String?,
    requestHeaders: Map<String, String>,
    onPlaybackError: (String) -> Unit,
    onPlaybackEvent: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var controls by remember { mutableStateOf<VideoPlayerControls?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }
    var hudVisible by remember { mutableStateOf(true) }
    var hudTimerToken by remember { mutableStateOf(0) }

    val keepHudVisible = {
        hudVisible = true
        hudTimerToken += 1
    }
    val togglePlayback = {
        keepHudVisible()
        isPlaying = controls?.togglePlayPause?.invoke() ?: isPlaying
    }

    val displayedPositionMs = if (isScrubbing) scrubPositionMs else positionMs
    val progress = if (durationMs > 0L) {
        (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val hudAlpha by animateFloatAsState(
        // Keep controls virtually invisible instead of fully removed so their click targets stay active.
        targetValue = if (hudVisible) 1f else 0.001f,
        label = "hudAlpha",
    )

    androidx.compose.runtime.LaunchedEffect(hudTimerToken) {
        delay(5_000L)
        hudVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoPlayerSurface(
            streamUrl = streamUrl,
            requestHeaders = requestHeaders,
            onPlaybackError = onPlaybackError,
            onPlaybackEvent = onPlaybackEvent,
            onTimelineChanged = { position, duration, playing ->
                positionMs = position
                durationMs = duration
                isPlaying = playing
                if (!isScrubbing) {
                    scrubPositionMs = position
                }
            },
            onControlsReady = { readyControls ->
                controls = readyControls
            },
            onSurfaceTap = keepHudVisible,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(hudAlpha),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "✕",
                    color = Color.White,
                    modifier = Modifier.clickable {
                        keepHudVisible()
                        onClose()
                    },
                )
                Text(
                    text = title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text("♡", color = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "↺10",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable {
                        keepHudVisible()
                        controls?.seekByMs?.invoke(-10_000L)
                    },
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .clickable { togglePlayback() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(
                    "10↻",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable {
                        keepHudVisible()
                        controls?.seekByMs?.invoke(10_000L)
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        keepHudVisible()
                        isScrubbing = true
                        scrubPositionMs = (durationMs * newProgress).toLong()
                    },
                    onValueChangeFinished = {
                        keepHudVisible()
                        controls?.seekToMs?.invoke(scrubPositionMs)
                        isScrubbing = false
                    },
                    enabled = durationMs > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatPlayerTime(displayedPositionMs), color = Color.White)
                    Text(formatPlayerTime(durationMs), color = Color.White)
                }
            }
        }

    }
}

private fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) {
        return "00:00"
    }
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
