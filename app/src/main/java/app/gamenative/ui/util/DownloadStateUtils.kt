package app.gamenative.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.data.DownloadInfo
import app.gamenative.service.SteamService
import app.gamenative.ui.enums.DownloadStatus

/**
 * Represents a status text resource that can be used with stringResource in Compose.
 * For simple strings, use [resourceId] with no arguments.
 * For formatted strings, use [resourceId] with [formatArgs].
 */
data class StatusTextResource(
    val resourceId: Int,
    val formatArgs: Array<Any> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StatusTextResource

        if (resourceId != other.resourceId) return false
        if (!formatArgs.contentEquals(other.formatArgs)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resourceId
        result = 31 * result + formatArgs.contentHashCode()
        return result
    }
}

/**
 * Extension function to convert StatusTextResource to a string using stringResource.
 * This makes it easier to use in Compose without manually checking formatArgs.
 */
@Composable
fun StatusTextResource.asString(): String {
    return if (formatArgs.isEmpty()) {
        stringResource(resourceId)
    } else {
        stringResource(resourceId, *formatArgs)
    }
}

/**
 * Utility functions for calculating download states.
 * These functions centralize the logic for determining download status
 * to avoid duplication across components.
 */
object DownloadStateUtils {
    /**
     * Session-scoped set of game IDs that are fresh installs.
     * This is cleared when the app is closed (not persistent).
     */
    private val freshInstalls = mutableSetOf<Int>()

    /**
     * Marks a game as a fresh install for the current session.
     * This flag will be used to show "Starting" instead of "Validating" for fresh installs.
     *
     * @param gameId The game ID to mark as fresh install
     */
    fun markAsFreshInstall(gameId: Int) {
        freshInstalls.add(gameId)
    }

    /**
     * Clears the fresh install flag for a game.
     *
     * @param gameId The game ID to clear
     */
    fun clearFreshInstall(gameId: Int) {
        freshInstalls.remove(gameId)
    }

    /**
     * Checks if a game is marked as a fresh install.
     *
     * @param gameId The game ID to check
     * @return true if the game is marked as a fresh install
     */
    fun isFreshInstall(gameId: Int): Boolean {
        return freshInstalls.contains(gameId)
    }
    /**
     * Determines if a download is actively downloading.
     * A download is considered downloading ONLY if it's the actively downloading game.
     * Conditions:
     * - isActivelyDownloading flag is true (this is the active download)
     * - downloadInfo exists
     * - progress is less than 100%
     * - job is active
     * - progress is greater than 0% (actually downloading data, not just starting/validating)
     *
     * Note: If isActivelyDownloading is false, this is NOT downloading (it's queued or paused).
     */
    fun isDownloading(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        isActivelyDownloading: Boolean = false
    ): Boolean {
        return isActivelyDownloading
            && downloadInfo != null
            && downloadProgress < 1f
            && isJobActive
    }

    /**
     * Determines if there's a partial download (paused or incomplete).
     */
    fun isPartiallyDownloaded(
        downloadProgress: Float,
        hasPartialDownload: Boolean
    ): Boolean {
        return (downloadProgress > 0f && downloadProgress < 1f) || hasPartialDownload
    }

    /**
     * Determines if a download is currently validating.
     * Validation occurs when resuming - the job is active but progress is very small/zero
     * and there's a partial download.
     */
    fun isValidating(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        hasPartialDownload: Boolean,
        justResumed: Boolean = false
    ): Boolean {
        return downloadInfo != null && isJobActive && downloadProgress <= 0.0001f && (hasPartialDownload || justResumed)
    }

    /**
     * Determines if a download is starting (job is active but progress is 0 and no partial download exists).
     */
    fun isStartingDownload(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        hasPartialDownload: Boolean
    ): Boolean {
        return downloadInfo != null && isJobActive && downloadProgress <= 0.0001f && !hasPartialDownload
    }

    /**
     * Determines if a download is queued (waiting for another download to complete).
     * A download is queued if:
     * - downloadInfo exists (game is in the download list)
     * - progress is less than 100% (not complete)
     * - this download is NOT actively downloading
     */
    fun isQueued(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        hasPartialDownload: Boolean,
        isActivelyDownloading: Boolean
    ): Boolean {
        return downloadInfo != null
            && downloadProgress < 1f
            && !isActivelyDownloading
            && !isDownloading(downloadInfo, downloadProgress, isJobActive, isActivelyDownloading)
    }

    /**
     * Determines the download status based on current state.
     * Returns null if there's no download activity.
     */
    fun getDownloadStatus(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        hasPartialDownload: Boolean,
        justResumed: Boolean = false,
        isActivelyDownloading: Boolean = false
    ): DownloadStatus? {
        val isDownloading = isDownloading(downloadInfo, downloadProgress, isJobActive, isActivelyDownloading)
        val isValidating = isValidating(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, justResumed)
        val isPartiallyDownloaded = isPartiallyDownloaded(downloadProgress, hasPartialDownload)
        val isQueued = isQueued(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, isActivelyDownloading)

        return when {
            downloadProgress >= 1f -> DownloadStatus.COMPLETED
            isValidating -> DownloadStatus.VALIDATING
            isDownloading -> DownloadStatus.DOWNLOADING
            isQueued -> DownloadStatus.QUEUED  // Check queued before paused - if not actively downloading, it's queued
            isPartiallyDownloaded -> DownloadStatus.PAUSED
            downloadInfo != null && downloadProgress == 0f && !isJobActive -> DownloadStatus.QUEUED
            else -> null
        }
    }

    /**
     * Refreshes download progress state from DownloadInfo.
     * Returns a data class with all the updated state values.
     */
    data class DownloadState(
        val downloadProgress: Float,
        val isJobActive: Boolean,
        val isJobCancelled: Boolean,
        val isJobCompleted: Boolean
    )

    fun refreshProgress(downloadInfo: DownloadInfo?): DownloadState {
        return DownloadState(
            downloadProgress = downloadInfo?.getProgress() ?: 0f,
            isJobActive = downloadInfo?.isJobActive() ?: false,
            isJobCancelled = downloadInfo?.isJobCancelled() ?: false,
            isJobCompleted = downloadInfo?.isJobCompleted() ?: false
        )
    }

    /**
     * Comprehensive download state information.
     * Contains all boolean flags for download states.
     */
    data class DownloadStateFlags(
        val isDownloading: Boolean,
        val isValidating: Boolean,
        val isStartingDownload: Boolean,
        val isQueued: Boolean,
        val isPartiallyDownloaded: Boolean,
        val isActivelyDownloading: Boolean
    )

    /**
     * Calculates all download state flags at once.
     * This centralizes all download state logic in one place.
     */
    fun calculateDownloadStates(
        downloadInfo: DownloadInfo?,
        downloadProgress: Float,
        isJobActive: Boolean,
        hasPartialDownload: Boolean,
        isActivelyDownloading: Boolean,
        justResumed: Boolean = false
    ): DownloadStateFlags {
        return DownloadStateFlags(
            isDownloading = isDownloading(downloadInfo, downloadProgress, isJobActive, isActivelyDownloading),
            isStartingDownload = isStartingDownload(downloadInfo, downloadProgress, isJobActive, hasPartialDownload),
            isValidating = isValidating(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, justResumed),
            isQueued = isQueued(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, isActivelyDownloading),
            isPartiallyDownloaded = isPartiallyDownloaded(downloadProgress, hasPartialDownload),
            isActivelyDownloading = isActivelyDownloading
        )
    }

    /**
     * Gets the download status for a game by its ID.
     * This function consolidates all the logic needed to determine download status
     * by fetching all necessary information from SteamService.
     *
     * @param gameId The game ID to check
     * @return The download status, or null if there's no download activity
     */
    fun getGameDownloadStatus(gameId: Int): DownloadStatus? {
        val downloadInfo = SteamService.getAppDownloadInfo(gameId)
        val downloadProgress = downloadInfo?.getProgress() ?: 0f
        val isJobActive = downloadInfo?.isJobActive() ?: false
        val hasPartialDownload = SteamService.hasPartialDownload(gameId)
        val isActivelyDownloading = SteamService.isActivelyDownloading(gameId)

        return getDownloadStatus(
            downloadInfo = downloadInfo,
            downloadProgress = downloadProgress,
            isJobActive = isJobActive,
            hasPartialDownload = hasPartialDownload,
            isActivelyDownloading = isActivelyDownloading
        )
    }

    /**
     * Gets the status text for a game's download/install state.
     * Returns localized strings based on the download status and installation state.
     *
     * @param downloadStatus The current download status, or null if no download activity
     * @param downloadProgress The download progress (0.0 to 1.0)
     * @param isInstalled Whether the game is installed. If not provided, defaults to false
     *                    unless downloadStatus is COMPLETED (then defaults to true).
     * @param gameId Optional game ID to check for fresh install status. If provided and
     *               the game is a fresh install, "Starting" will be shown instead of "Validating".
     * @return The localized status text string
     */
    @Composable
    fun getDownloadStatusText(
        downloadStatus: DownloadStatus?,
        downloadProgress: Float,
        isInstalled: Boolean? = null,
        gameId: Int? = null
    ): String {
        val effectiveIsInstalled = isInstalled ?: (downloadStatus == DownloadStatus.COMPLETED)
        val statusTextRes = when {
            downloadStatus != null -> when (downloadStatus) {
                DownloadStatus.QUEUED -> StatusTextResource(R.string.queued)
                DownloadStatus.PAUSED -> StatusTextResource(R.string.paused)
                DownloadStatus.VALIDATING -> {
                    // Show "Starting" for fresh installs, "Validating" for resumed downloads
                    if (gameId != null && isFreshInstall(gameId)) {
                        StatusTextResource(R.string.starting_download)
                    } else {
                        StatusTextResource(R.string.validating)
                    }
                }
                DownloadStatus.DOWNLOADING -> StatusTextResource(
                    R.string.installing_percent,
                    arrayOf(downloadProgress * 100)
                )
                DownloadStatus.COMPLETED -> StatusTextResource(R.string.installed)
            }
            effectiveIsInstalled -> StatusTextResource(R.string.installed)
            else -> StatusTextResource(R.string.not_installed_status)
        }
        return statusTextRes.asString()
    }

    /**
     * Gets the status text for a game's download/install state by game ID.
     * Returns localized strings based on the download status and installation state.
     *
     * @param gameId The game ID to check
     * @param downloadProgress The download progress (0.0 to 1.0)
     * @param isInstalled Whether the game is installed. If not provided, defaults to false
     *                    unless downloadStatus is COMPLETED (then defaults to true).
     * @return The localized status text string
     */
    @Composable
    fun getGameDownloadStatusText(
        gameId: Int,
        downloadProgress: Float,
        isInstalled: Boolean? = null
    ): String {
        val downloadStatus = getGameDownloadStatus(gameId)
        val effectiveIsInstalled = isInstalled ?: (downloadStatus == DownloadStatus.COMPLETED)
        return getDownloadStatusText(downloadStatus, downloadProgress, effectiveIsInstalled, gameId)
    }
}

