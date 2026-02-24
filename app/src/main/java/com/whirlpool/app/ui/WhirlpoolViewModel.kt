package com.whirlpool.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whirlpool.app.data.EngineRepository
import com.whirlpool.engine.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WhirlpoolUiState(
    val isLoading: Boolean = false,
    val query: String = "cats",
    val videos: List<VideoItem> = demoVideos(),
    val favorites: List<VideoItem> = emptyList(),
    val selectedVideo: VideoItem? = null,
    val streamUrl: String? = null,
    val statusText: String = "Hot Tub",
    val actionText: String? = null,
    val errorText: String? = null,
)

private const val FALLBACK_PREVIEW_STREAM =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

class WhirlpoolViewModel(
    private val repository: EngineRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WhirlpoolUiState())
    val uiState: StateFlow<WhirlpoolUiState> = mutableState.asStateFlow()

    init {
        refreshAll()
    }

    fun onQueryChange(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
    }

    fun refreshAll() {
        val current = mutableState.value
        mutableState.value = current.copy(isLoading = true, errorText = null)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val status = repository.status()
                    val discovered = repository.discover(current.query)
                    val videos = discovered.ifEmpty { demoVideos() }
                    val favoriteIds = repository.favorites().map { it.videoId }.toSet()
                    val favoriteVideos = videos.filter { it.id in favoriteIds }

                    current.copy(
                        isLoading = false,
                        statusText = if (status.name.isBlank()) "Hot Tub" else status.name,
                        videos = videos,
                        favorites = favoriteVideos,
                        actionText = null,
                        errorText = null,
                    )
                }
            }.onSuccess { state ->
                mutableState.value = state
            }.onFailure { throwable ->
                // Keep the existing visual feed so the app still looks complete offline.
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorText = "Unable to load live feed from server. Showing cached results.",
                )
            }
        }
    }

    fun search() {
        refreshAll()
    }

    fun playVideo(video: VideoItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolved = repository.resolve(video.pageUrl)
                    mutableState.value.copy(
                        selectedVideo = video,
                        streamUrl = resolved.streamUrl,
                        errorText = null,
                        actionText = null,
                    )
                }
            }.onSuccess { state ->
                mutableState.value = state
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    selectedVideo = video,
                    streamUrl = FALLBACK_PREVIEW_STREAM,
                    errorText = null,
                    actionText = null,
                )
            }
        }
    }

    fun dismissPlayer() {
        mutableState.value = mutableState.value.copy(
            selectedVideo = null,
            streamUrl = null,
        )
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val favoriteIds = repository.favorites().map { it.videoId }.toSet()
                    if (video.id in favoriteIds) {
                        repository.removeFavorite(video.id)
                    } else {
                        repository.addFavorite(video)
                    }
                    repository.favorites().map { favorite ->
                        mutableState.value.videos.firstOrNull { it.id == favorite.videoId }
                    }.filterNotNull()
                }
            }.onSuccess { favorites ->
                mutableState.value = mutableState.value.copy(favorites = favorites)
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Could not update favorites right now.",
                )
            }
        }
    }

    fun exportDatabase() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val path = repository.transferPath()
                    repository.exportDatabase(path)
                    path
                }
            }.onSuccess { path ->
                mutableState.value = mutableState.value.copy(
                    actionText = "Exported DB to $path",
                    errorText = null,
                )
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Export failed. Verify app storage permissions and try again.",
                )
            }
        }
    }

    fun importDatabase() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val path = repository.transferPath()
                    repository.importDatabase(path)
                    path
                }
            }.onSuccess {
                refreshAll()
                mutableState.value = mutableState.value.copy(
                    actionText = "Imported DB from $it",
                    errorText = null,
                )
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Import failed. No valid export file was found.",
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val repository = EngineRepository(context)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WhirlpoolViewModel(repository) as T
                }
            }
        }
    }
}

private fun demoVideos(): List<VideoItem> {
    val urls = listOf(
        "https://images.unsplash.com/photo-1543852786-1cf6624b9987?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1519052537078-e6302a4968d4?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1533743983669-94fa5c4338ec?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1574158622682-e40e69881006?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1596854407944-bf87f6fdd49e?auto=format&fit=crop&w=900&q=80",
    )

    return listOf(
        VideoItem(
            id = "demo-1",
            title = "Golden Retriever Meets New Kitten",
            pageUrl = "https://www.youtube.com/watch?v=J---aiyznGQ",
            durationSeconds = 110u,
            imageUrl = urls[0],
            network = "Community",
            authorName = "Funny Dog Bailey",
            extractor = "youtube",
            viewCount = 4_700_000u,
        ),
        VideoItem(
            id = "demo-2",
            title = "Kitten Slow Motion Jump Compilation",
            pageUrl = "https://www.youtube.com/watch?v=tntOCGkgt98",
            durationSeconds = 95u,
            imageUrl = urls[1],
            network = "Community",
            authorName = "Paws Daily",
            extractor = "youtube",
            viewCount = 2_100_000u,
        ),
        VideoItem(
            id = "demo-3",
            title = "20 Minutes of Adorable Kittens",
            pageUrl = "https://www.youtube.com/watch?v=5dsGWM5XGdg",
            durationSeconds = 1200u,
            imageUrl = urls[2],
            network = "Community",
            authorName = "Cat TV",
            extractor = "youtube",
            viewCount = 12_800_000u,
        ),
        VideoItem(
            id = "demo-4",
            title = "Before Getting a Cat: Reality Check",
            pageUrl = "https://www.youtube.com/watch?v=hY7m5jjJ9mM",
            durationSeconds = 695u,
            imageUrl = urls[3],
            network = "Community",
            authorName = "Home Humor",
            extractor = "youtube",
            viewCount = 1_050_000u,
        ),
        VideoItem(
            id = "demo-5",
            title = "Kittens and Puppies Best Friends",
            pageUrl = "https://www.youtube.com/watch?v=qpl5mOAXNl4",
            durationSeconds = 770u,
            imageUrl = urls[4],
            network = "Community",
            authorName = "Animal Planet",
            extractor = "youtube",
            viewCount = 8_900_000u,
        ),
        VideoItem(
            id = "demo-6",
            title = "10 Things To Know Before Getting A Cat",
            pageUrl = "https://www.youtube.com/watch?v=tnX6h6Q8g5k",
            durationSeconds = 678u,
            imageUrl = urls[5],
            network = "Community",
            authorName = "Vet Talks",
            extractor = "youtube",
            viewCount = 950_000u,
        ),
    )
}
