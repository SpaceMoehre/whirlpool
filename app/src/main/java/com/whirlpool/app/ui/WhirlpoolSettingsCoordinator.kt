package com.whirlpool.app.ui

import com.whirlpool.app.data.EngineRepository
import com.whirlpool.app.data.SourceServerConfig
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WhirlpoolSettingsCoordinator(
    private val repository: EngineRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun loadSettingsAndSources(): LoadedPreferences = withContext(ioDispatcher) {
        val persisted = repository.loadSettings()
        val servers = repository.listSourceServers()
        val activeBase = repository.activeApiBaseUrl()
        val activeChannel = persisted[SettingKeys.FEED_ACTIVE_CHANNEL]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val selectedFilters = FeedPreferenceCodec.decodeFilterSelection(
            persisted[SettingKeys.FEED_SELECTED_FILTERS].orEmpty(),
        )
        LoadedPreferences(
            persisted = persisted,
            servers = servers,
            activeBase = activeBase,
            activeChannel = activeChannel,
            selectedFilters = selectedFilters,
        )
    }

    suspend fun persistFeedState(
        activeChannel: String,
        selectedFilters: Map<String, Set<String>>,
    ) = withContext(ioDispatcher) {
        val channelSaved = repository.setSetting(
            SettingKeys.FEED_ACTIVE_CHANNEL,
            activeChannel,
        )
        val filtersSaved = repository.setSetting(
            SettingKeys.FEED_SELECTED_FILTERS,
            FeedPreferenceCodec.encodeFilterSelection(selectedFilters),
        )
        if (!channelSaved || !filtersSaved) {
            throw IOException("Unable to save feed filter settings.")
        }
    }

    suspend fun persistSetting(key: String, value: String) = withContext(ioDispatcher) {
        val saved = repository.setSetting(key, value)
        if (!saved) {
            throw IOException("Unable to save setting: $key")
        }
    }
}

internal data class LoadedPreferences(
    val persisted: Map<String, String>,
    val servers: List<SourceServerConfig>,
    val activeBase: String,
    val activeChannel: String?,
    val selectedFilters: Map<String, Set<String>>,
)
