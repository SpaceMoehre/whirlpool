package com.whirlpool.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.whirlpool.app.R
import com.whirlpool.app.player.VideoPlayerControls
import com.whirlpool.app.player.VideoPlayerSurface
import com.whirlpool.engine.StatusChannel
import com.whirlpool.engine.StatusFilterOption
import com.whirlpool.engine.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FEED_NEXT_PAGE_PREFETCH_DISTANCE = 12
private val HEADER_BAR_HEIGHT = 72.dp
private val HEADER_BLUR_RADIUS = 24.dp

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

private fun Modifier.hapticClickable(
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    clickable {
        haptics.performHapticFeedback(hapticType)
        onClick()
    }
}

private fun categoryVibrantGradient(word: String): List<Color> {
    val normalized = word.trim().lowercase()
    val primarySeed = stableWordSeed(normalized)
    val accentSeed = stableWordSeed("$normalized#accent")

    val hueA = positiveMod(primarySeed, 360).toFloat()
    val hueB = (hueA + 36f + positiveMod(accentSeed ushr 7, 72).toFloat()) % 360f

    val saturationA = 0.78f + positiveMod(primarySeed ushr 3, 18) / 100f
    val saturationB = 0.74f + positiveMod(accentSeed ushr 5, 20) / 100f
    val lightnessA = 0.34f + positiveMod(primarySeed ushr 11, 18) / 100f
    val lightnessB = 0.30f + positiveMod(accentSeed ushr 13, 20) / 100f

    return listOf(
        hslToColor(hueA, saturationA, lightnessA),
        hslToColor(hueB, saturationB, lightnessB),
    )
}

private fun stableWordSeed(input: String): Long {
    var hash = 1_125_899_906_842_597L
    input.forEach { ch ->
        hash = 31L * hash + ch.code.toLong()
    }
    return hash
}

private fun positiveMod(value: Long, mod: Int): Int {
    val raw = value % mod
    return if (raw < 0) (raw + mod).toInt() else raw.toInt()
}

private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val chroma = (1f - abs(2f * l - 1f)) * s
    val x = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - chroma / 2f

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(chroma, x, 0f)
        h < 120f -> Triple(x, chroma, 0f)
        h < 180f -> Triple(0f, chroma, x)
        h < 240f -> Triple(0f, x, chroma)
        h < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }

    return Color(
        red = (rPrime + m).coerceIn(0f, 1f),
        green = (gPrime + m).coerceIn(0f, 1f),
        blue = (bPrime + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhirlpoolScreen(
    viewModel: WhirlpoolViewModel,
    darkModeEnabled: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerOverlayHeight = statusBarTop + HEADER_BAR_HEIGHT
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

    LaunchedEffect(
        feedListState,
        state.videos.size,
        state.hasMorePages,
        state.isLoading,
        state.isLoadingNextPage,
    ) {
        snapshotFlow {
            val layoutInfo = feedListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val prefetchStartIndex = (totalItems - FEED_NEXT_PAGE_PREFETCH_DISTANCE).coerceAtLeast(0)
            val nearEnd = totalItems > 0 && lastVisibleIndex >= prefetchStartIndex
            val hasScrolled = feedListState.firstVisibleItemIndex > 0 ||
                feedListState.firstVisibleItemScrollOffset > 0
            nearEnd && hasScrolled
        }
            .distinctUntilChanged()
            .collect { shouldLoadNextPage ->
                if (
                    shouldLoadNextPage &&
                    state.hasMorePages &&
                    !state.isLoading &&
                    !state.isLoadingNextPage
                ) {
                    viewModel.loadNextPage()
                }
            }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::search,
                modifier = Modifier
                    .fillMaxSize()
                    .topRegionBlur(
                        blurHeight = headerOverlayHeight,
                        blurRadius = HEADER_BLUR_RADIUS,
                        overlayColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    ),
            ) {
                LazyColumn(
                    state = feedListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Spacer(
                            modifier = Modifier
                                .statusBarsPadding()
                                .height(48.dp),
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
                        if (state.settings.categoriesSection) {
                            SectionTitle("Categories")
                            CategoriesRow(
                                categories = state.categories,
                                onCategoryClick = { category ->
                                    viewModel.onQueryChange(category)
                                    viewModel.search()
                                },
                            )
                        }
                    }

                    item {
                        if (state.settings.favoritesSection) {
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
                            showDetails = state.settings.videoRowDetails,
                            onPlay = { viewModel.playVideo(video) },
                            onFavorite = { viewModel.toggleFavorite(video) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            HeaderBackdrop(
                modifier = Modifier.align(Alignment.TopCenter),
                height = headerOverlayHeight,
            )

            HeaderRow(
                modifier = Modifier.align(Alignment.TopCenter),
                onSettings = { showSettings = true },
                onFilters = { showFilters = true },
                onTitleTap = {
                    scope.launch {
                        feedListState.scrollToItem(index = 0)
                    }
                },
            )
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
                    serverHost = state.activeServerBaseUrl
                        .substringAfter("://")
                        .substringBefore("/")
                        .ifBlank { "No source" },
                    channels = state.channelDetails,
                    availableChannels = state.availableChannels,
                    activeChannelId = state.activeChannel,
                    activeServerBaseUrl = state.activeServerBaseUrl,
                    selectedFilters = state.selectedFilters,
                    onChannelSelected = { serverBaseUrl, channelId ->
                        viewModel.onChannelSelected(serverBaseUrl, channelId)
                    },
                    onFilterSelected = viewModel::onFilterSelected,
                    onFilterToggleAll = viewModel::onFilterToggleAll,
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
                SettingsRootSheet(
                    darkModeEnabled = darkModeEnabled,
                    settings = state.settings,
                    sources = state.sourceServers,
                    logs = state.logs,
                    onBooleanSettingChange = viewModel::updateBooleanSetting,
                    onTextSettingChange = viewModel::updateTextSetting,
                    onThemeSelected = { theme ->
                        viewModel.updateTextSetting(SettingKeys.THEME, theme)
                        onDarkModeToggle(theme == "Dark")
                    },
                    onAddSource = viewModel::addSource,
                    onRemoveSource = viewModel::removeSource,
                    onActivateSource = viewModel::activateSource,
                    onAddTag = viewModel::addFollowingTag,
                    onRemoveTag = viewModel::removeFollowingTag,
                    onAddUploader = viewModel::addFollowingUploader,
                    onRemoveUploader = viewModel::removeFollowingUploader,
                    onClearCache = viewModel::clearCacheData,
                    onExport = viewModel::exportDatabase,
                    onImport = viewModel::importDatabase,
                    onClearWatchHistory = viewModel::clearWatchHistory,
                    onClearFavorites = viewModel::clearAllFavorites,
                    onClearAchievements = viewModel::clearAchievements,
                    onResetAllData = viewModel::resetAllData,
                    onClose = { showSettings = false },
                )
            }
        }
    }
}

@Composable
private fun HeaderBackdrop(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

private fun Modifier.topRegionBlur(
    blurHeight: androidx.compose.ui.unit.Dp,
    blurRadius: androidx.compose.ui.unit.Dp,
    overlayColor: Color,
): Modifier = composed {
    val blurLayer = rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }

    drawWithContent {
        drawContent()

        val clipHeight = blurHeight.toPx().coerceAtMost(size.height)
        val radiusPx = blurRadius.toPx()
        if (clipHeight <= 0f || radiusPx <= 0f) {
            return@drawWithContent
        }

        blurLayer.renderEffect = BlurEffect(radiusX = radiusPx, radiusY = radiusPx)
        blurLayer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(
                width = size.width.roundToInt().coerceAtLeast(1),
                height = size.height.roundToInt().coerceAtLeast(1),
            ),
        ) {
            this@drawWithContent.drawContent()
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipRect(0f, 0f, size.width, clipHeight)
        drawLayer(blurLayer)
        canvas.restore()

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    overlayColor,
                    Color.Transparent,
                ),
                endY = clipHeight,
            ),
            size = Size(size.width, clipHeight),
        )
    }
}

@Composable
private fun HeaderRow(
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onFilters: () -> Unit,
    onTitleTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_whirlpool_settings),
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(24.dp)
                .hapticClickable(onClick = onSettings),
        )

        Text(
            text = "Whirlpool",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .hapticClickable(onClick = onTitleTap),
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_whirlpool_filter),
            contentDescription = "Filters",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(24.dp)
                .hapticClickable(onClick = onFilters),
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
            Icon(
                painter = painterResource(id = R.drawable.ic_whirlpool_search),
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .hapticClickable(onClick = onSearch),
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
private fun CategoriesRow(
    categories: List<String>,
    onCategoryClick: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(modifier = Modifier.width(6.dp)) }
        items(categories) { title ->
            val gradient = remember(title) { categoryVibrantGradient(title) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(gradient))
                    .hapticClickable { onCategoryClick(title) }
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
            .hapticClickable(onClick = onPlay),
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

            FavoriteHeartButton(
                isFavorite = isFavorite,
                onToggle = onFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )

            DurationBadge(
                durationSeconds = video.durationSeconds,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
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
    showDetails: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .hapticClickable(onClick = onPlay)
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

            FavoriteHeartButton(
                isFavorite = isFavorite,
                onToggle = onFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )

            DurationBadge(
                durationSeconds = video.durationSeconds,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
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
            if (showDetails) {
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
}

@Composable
private fun FavoriteHeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pulse by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulse) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 480f),
        label = "favoriteScale",
    )
    val burstAlpha by animateFloatAsState(
        targetValue = if (pulse) 0.28f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "favoriteBurstAlpha",
    )
    val tint by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFFF4D73) else Color.White,
        animationSpec = tween(durationMillis = 220),
        label = "favoriteTint",
    )

    LaunchedEffect(pulse) {
        if (pulse) {
            delay(120L)
            pulse = false
        }
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.25f))
            .hapticClickable(hapticType = HapticFeedbackType.LongPress) {
                pulse = true
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF4D73).copy(alpha = burstAlpha)),
        )
        Icon(
            painter = painterResource(
                id = if (isFavorite) R.drawable.ic_whirlpool_favorite else R.drawable.ic_whirlpool_favorite_border,
            ),
            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DurationBadge(durationSeconds: UInt?, modifier: Modifier = Modifier) {
    val formatted = formatVideoDuration(durationSeconds) ?: return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = formatted,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
    val haptics = LocalHapticFeedback.current

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
            Text("Close", color = palette.actionText, modifier = Modifier.hapticClickable(onClick = onClose))
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
                    onCheckedChange = { enabled ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDarkModeToggle(enabled)
                    },
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
                        modifier = Modifier.hapticClickable(onClick = onClearLogs),
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
    channels: List<StatusChannel>,
    availableChannels: List<ChannelMenuItem>,
    activeChannelId: String,
    activeServerBaseUrl: String,
    selectedFilters: Map<String, Set<String>>,
    onChannelSelected: (serverBaseUrl: String, channelId: String) -> Unit,
    onFilterSelected: (optionId: String, choiceId: String) -> Unit,
    onFilterToggleAll: (optionId: String, selectAll: Boolean) -> Unit,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val palette = menuPalette(darkModeEnabled)
    val activeChannel = channels.firstOrNull { channel -> channel.id == activeChannelId }
        ?: channels.firstOrNull()
    val sortedChannels = availableChannels.sortedWith(
        compareBy(
            String.CASE_INSENSITIVE_ORDER,
            ChannelMenuItem::channelTitle,
        ).thenBy(
            String.CASE_INSENSITIVE_ORDER,
            ChannelMenuItem::serverTitle,
        ),
    )
    var showChannelSelector by remember { mutableStateOf(false) }
    var selectedOptionForDialog by remember { mutableStateOf<StatusFilterOption?>(null) }

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
            Text("Close", color = palette.actionText, modifier = Modifier.hapticClickable(onClick = onClose))
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
                FilterMenuRow(
                    label = "Channel",
                    value = buildString {
                        append(activeChannel?.title ?: activeChannelId)
                    },
                    enabled = sortedChannels.isNotEmpty(),
                    onClick = { showChannelSelector = true },
                ),
            ),
        )

        FilterSection(
            darkModeEnabled = darkModeEnabled,
            title = "FILTERS",
            rows = activeChannel
                ?.options
                ?.map { option ->
                    FilterMenuRow(
                        label = option.title,
                        value = selectedChoiceTitle(option, selectedFilters),
                        enabled = option.choices.isNotEmpty(),
                        onClick = {
                            if (option.choices.isNotEmpty()) {
                                selectedOptionForDialog = option
                            }
                        },
                    )
                }
                .orEmpty(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill(darkModeEnabled = darkModeEnabled, label = "Export", onClick = onExport)
            ActionPill(darkModeEnabled = darkModeEnabled, label = "Import", onClick = onImport)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showChannelSelector) {
        ChannelSelectionDialog(
            channels = sortedChannels,
            selectedChannelId = activeChannel?.id,
            selectedServerBaseUrl = activeServerBaseUrl,
            onSelect = { selected ->
                onChannelSelected(selected.serverBaseUrl, selected.channelId)
                showChannelSelector = false
            },
            onDismiss = { showChannelSelector = false },
        )
    }

    selectedOptionForDialog?.let { option ->
        val selectedIds = selectedFilters[option.id].orEmpty()
        val allSelected = option.choices.isNotEmpty() &&
            option.choices.all { choice -> choice.id in selectedIds }
        SelectionDialog(
            title = option.title,
            options = option.choices.map { choice -> choice.id to choice.title },
            selectedIds = selectedIds,
            multiSelect = option.multiSelect,
            onSelect = { selected ->
                onFilterSelected(option.id, selected)
                if (!option.multiSelect) {
                    selectedOptionForDialog = null
                }
            },
            toggleAllLabel = if (option.multiSelect) {
                if (allSelected) "Deselect all" else "Select all"
            } else {
                null
            },
            onToggleAll = if (option.multiSelect) {
                {
                    onFilterToggleAll(option.id, !allSelected)
                }
            } else {
                null
            },
            onDismiss = { selectedOptionForDialog = null },
        )
    }
}

@Composable
private fun FilterSection(darkModeEnabled: Boolean, title: String, rows: List<FilterMenuRow>) {
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
                        .then(
                            if (row.enabled && row.onClick != null) {
                                Modifier.hapticClickable(onClick = row.onClick)
                            } else {
                                Modifier
                            },
                        )
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primaryText,
                    )
                    Text(
                        row.value,
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

private data class FilterMenuRow(
    val label: String,
    val value: String,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
)

private fun selectedChoiceTitle(
    option: StatusFilterOption,
    selectedFilters: Map<String, Set<String>>,
): String {
    val selectedIds = selectedFilters[option.id].orEmpty()
    if (option.multiSelect) {
        val selectedTitles = option.choices
            .filter { choice -> choice.id in selectedIds }
            .map { choice -> choice.title }
        return when {
            selectedTitles.isEmpty() -> "Not set"
            selectedTitles.size <= 2 -> selectedTitles.joinToString(", ")
            else -> "${selectedTitles.size} selected"
        }
    }

    val selectedId = selectedIds.firstOrNull()
    return option.choices.firstOrNull { choice -> choice.id == selectedId }?.title
        ?: option.choices.firstOrNull()?.title
        ?: "Not set"
}

@Composable
private fun ChannelSelectionDialog(
    channels: List<ChannelMenuItem>,
    selectedChannelId: String?,
    selectedServerBaseUrl: String,
    onSelect: (ChannelMenuItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Channel") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    channels,
                    key = { channel -> "${channel.serverBaseUrl}::${channel.channelId}" },
                ) { channel ->
                    val isSelected = channel.channelId == selectedChannelId &&
                        channel.serverBaseUrl == selectedServerBaseUrl
                    val title = channel.channelTitle.ifBlank { channel.channelId }
                    val description = buildString {
                        append(channel.serverTitle.ifBlank { channel.serverHost })
                        channel.channelDescription
                            ?.takeIf { text -> text.isNotBlank() }
                            ?.let { text ->
                                append(" · ")
                                append(text)
                            }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                            .hapticClickable { onSelect(channel) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            val faviconUrl = channel.channelFaviconUrl?.takeIf { url -> url.isNotBlank() }
                            if (faviconUrl != null) {
                                AsyncImage(
                                    model = faviconUrl,
                                    contentDescription = "$title favicon",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedIds: Set<String>,
    multiSelect: Boolean,
    onSelect: (String) -> Unit,
    toggleAllLabel: String?,
    onToggleAll: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { option -> option.first }) { option ->
                    val isSelected = option.first in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .hapticClickable { onSelect(option.first) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.second,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiSelect && onToggleAll != null && toggleAllLabel != null) {
                TextButton(onClick = onToggleAll) {
                    Text(toggleAllLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (multiSelect) "Done" else "Close")
            }
        },
    )
}

@Composable
private fun ActionPill(darkModeEnabled: Boolean, label: String, onClick: () -> Unit) {
    val palette = menuPalette(darkModeEnabled)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.row)
            .hapticClickable(onClick = onClick)
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
    val haptics = LocalHapticFeedback.current
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

    LaunchedEffect(hudTimerToken) {
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
                Icon(
                    painter = painterResource(id = R.drawable.ic_whirlpool_close),
                    contentDescription = "Close player",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .hapticClickable(hapticType = HapticFeedbackType.LongPress) {
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
                Icon(
                    painter = painterResource(id = R.drawable.ic_whirlpool_favorite_border),
                    contentDescription = "Favorite",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(20.dp)
                        .hapticClickable {
                            keepHudVisible()
                        },
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_whirlpool_rewind),
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .hapticClickable {
                                keepHudVisible()
                                controls?.seekByMs?.invoke(-10_000L)
                            },
                    )
                    Text("10s", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .hapticClickable(hapticType = HapticFeedbackType.LongPress) {
                            togglePlayback()
                        },
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_whirlpool_forward),
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .hapticClickable {
                                keepHudVisible()
                                controls?.seekByMs?.invoke(10_000L)
                            },
                    )
                    Text("10s", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
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
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

private fun formatVideoDuration(durationSeconds: UInt?): String? {
    val seconds = durationSeconds?.toLong() ?: return null
    if (seconds <= 0L) {
        return null
    }
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%02d:%02d".format(minutes, remainingSeconds)
    }
}
