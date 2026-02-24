package com.whirlpool.app.data

import android.content.Context
import com.whirlpool.engine.Engine
import com.whirlpool.engine.EngineConfig
import com.whirlpool.engine.FavoriteItem
import com.whirlpool.engine.ResolvedVideo
import com.whirlpool.engine.StatusSummary
import com.whirlpool.engine.VideoItem
import java.io.File

class EngineRepository(private val context: Context) {
    private val appContext = context.applicationContext

    private val databaseFile: File by lazy {
        File(appContext.filesDir, "shared/whirlpool.db")
    }

    private val ytDlpBinary: File by lazy {
        File(appContext.filesDir, "yt-dlp")
    }

    private val curlCffiBridge: File by lazy {
        File(appContext.filesDir, "curl_cffi_fetch.py")
    }

    private val engine: Engine by lazy {
        ensureBridgeScriptInstalled()
        val config = EngineConfig(
            apiBaseUrl = "https://getfigleaf.com",
            dbPath = databaseFile.absolutePath,
            ytDlpPath = ytDlpBinary.absolutePath,
            pythonExecutable = "python3",
            curlCffiScriptPath = curlCffiBridge.takeIf { it.exists() }?.absolutePath,
            ytDlpRepoApi = "https://api.github.com/repos/hottubapp/yt-dlp/releases/latest",
        )
        Engine(config)
    }

    fun status(): StatusSummary = engine.syncStatus()

    fun discover(query: String, page: UInt = 1u, limit: UInt = 30u): List<VideoItem> {
        return engine.discoverVideos(query = query, page = page, limit = limit)
    }

    fun resolve(pageUrl: String): ResolvedVideo = engine.resolveStream(pageUrl)

    fun favorites(): List<FavoriteItem> = engine.listFavorites()

    fun addFavorite(video: VideoItem): FavoriteItem = engine.addFavorite(video)

    fun removeFavorite(videoId: String): Boolean = engine.removeFavorite(videoId)

    fun exportDatabase(path: String): Boolean = engine.exportDatabase(path)

    fun importDatabase(path: String): Boolean = engine.importDatabase(path)

    fun transferPath(): String {
        return File(appContext.filesDir, "exports/whirlpool-export.db").absolutePath
    }

    fun checkYtDlpUpdates() = engine.checkYtDlpUpdate()

    private fun ensureBridgeScriptInstalled() {
        if (curlCffiBridge.exists()) return

        runCatching {
            appContext.assets.open("curl_cffi_fetch.py").use { input ->
                curlCffiBridge.parentFile?.mkdirs()
                curlCffiBridge.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            curlCffiBridge.setExecutable(true)
        }
    }
}
