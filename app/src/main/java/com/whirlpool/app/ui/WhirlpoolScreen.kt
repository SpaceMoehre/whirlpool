package com.whirlpool.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.whirlpool.app.player.VideoPlayerSurface
import com.whirlpool.engine.VideoItem

private val CategoryGradients = listOf(
    listOf(Color(0xFF0A83E8), Color(0xFF32A9D4)),
    listOf(Color(0xFF22C55E), Color(0xFF14B8A6)),
    listOf(Color(0xFFF59E0B), Color(0xFFF43F5E)),
    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhirlpoolScreen(viewModel: WhirlpoolViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    if (state.streamUrl != null) {
        PlayerMode(
            title = state.selectedVideo?.title ?: "Now Playing",
            streamUrl = state.streamUrl,
            onClose = viewModel::dismissPlayer,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F5FA)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HeaderRow(onFilters = { showFilters = true })
            }

            item {
                SearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::search,
                )
            }

            item {
                PromoBanner()
            }

            item {
                SectionTitle("Categories", "See More")
                CategoriesRow(state.videos)
            }

            item {
                SectionTitle("Favorites", "See More")
                VideosRow(
                    videos = state.favorites.ifEmpty { state.videos.take(3) },
                    onPlay = viewModel::playVideo,
                    onFavoriteToggle = viewModel::toggleFavorite,
                    favorites = state.favorites,
                    largeCard = true,
                )
            }

            item {
                SectionTitle("Recents", "See More")
                VideosRow(
                    videos = state.videos,
                    onPlay = viewModel::playVideo,
                    onFavoriteToggle = viewModel::toggleFavorite,
                    favorites = state.favorites,
                    largeCard = false,
                )
            }

            item {
                state.errorText?.let {
                    Text(
                        text = it,
                        color = Color(0xFFC2412D),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                state.actionText?.let {
                    Text(
                        text = it,
                        color = Color(0xFF2563EB),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showFilters) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = sheetState,
                containerColor = Color(0xFFF2F2F7),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                FiltersSheet(
                    onClose = { showFilters = false },
                    onExport = viewModel::exportDatabase,
                    onImport = viewModel::importDatabase,
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(onFilters: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("⚙", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Hot Tub",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF7C8FFF), Color(0xFF9064F8)),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("✨ Get Pro", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "◍",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onFilters),
            )
        }
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
                color = Color(0xFF2563EB),
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
private fun PromoBanner() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFFB34DFF), Color(0xFFFF8A1D)),
                    ),
                )
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Try Hot Tub Pro for free!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Get access to all features and benefits of Hot Tub Pro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(action, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8E8E93))
    }
}

@Composable
private fun CategoriesRow(videos: List<VideoItem>) {
    val categories = videos.map { it.title.split(' ').take(2).joinToString(" ") }.distinct().take(8)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(modifier = Modifier.width(6.dp)) }
        items(categories.ifEmpty { listOf("Funny Cats", "Kittens", "Cat Fails") }) { title ->
            val gradient = CategoryGradients[title.hashCode().absoluteValue % CategoryGradients.size]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(gradient))
                    .padding(horizontal = 24.dp, vertical = 24.dp),
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
    largeCard: Boolean,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(modifier = Modifier.width(6.dp)) }
        items(videos, key = { it.id }) { video ->
            val isFavorite = favorites.any { it.id == video.id }
            VideoCard(
                video = video,
                isFavorite = isFavorite,
                largeCard = largeCard,
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
    largeCard: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    val width = if (largeCard) 260.dp else 200.dp
    val thumbHeight = if (largeCard) 146.dp else 112.dp

    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onPlay),
    ) {
        Box {
            AsyncImage(
                model = video.imageUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )

            Text(
                text = "WATCHED",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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

            Text(
                text = formatDuration(video.durationSeconds),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
        Text(
            text = "${video.authorName ?: "Community"}  ·  ${video.viewCount?.toString() ?: "--"} views",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E93),
        )
    }
}

@Composable
private fun FiltersSheet(
    onClose: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
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
            Text("Close", color = Color(0xFF0A84FF), modifier = Modifier.clickable(onClick = onClose))
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(44.dp))
        }

        FilterSection(
            title = "NETWORK",
            rows = listOf(
                "Server" to "getfigleaf.com",
                "Channel" to "Community",
            ),
        )

        FilterSection(
            title = "FILTERS",
            rows = listOf(
                "Sort" to "Top Rated",
                "Duration" to "Short (<10 min)",
                "Production Type" to "All Content",
                "Sexuality" to "Straight",
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill("Export", onExport)
            ActionPill("Import", onImport)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FilterSection(title: String, rows: List<Pair<String, String>>) {
    Text(
        text = title,
        color = Color(0xFF8E8E93),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.first, style = MaterialTheme.typography.titleMedium)
                    Text(row.second, color = Color(0xFF8E8E93), style = MaterialTheme.typography.titleMedium)
                }
                if (index < rows.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE5E5EA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color(0xFF0A84FF), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlayerMode(
    title: String,
    streamUrl: String?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoPlayerSurface(
            streamUrl = streamUrl,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", color = Color.White, modifier = Modifier.clickable(onClick = onClose))
            Text(
                text = title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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
            Text("↺10", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f))
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.Black, style = MaterialTheme.typography.headlineSmall)
            }
            Text("10↻", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            LinearProgressIndicator(
                progress = 0.55f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("00:02", color = Color.White)
                Text("00:02", color = Color.White)
            }
        }
    }
}

private fun formatDuration(durationSeconds: UInt?): String {
    val total = durationSeconds?.toInt() ?: return "--:--"
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

private val Int.absoluteValue: Int
    get() = if (this < 0) -this else this
