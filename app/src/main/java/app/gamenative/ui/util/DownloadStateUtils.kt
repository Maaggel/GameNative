package app.gamenative.ui.util

import app.gamenative.data.DownloadInfo
import app.gamenative.ui.enums.DownloadStatus

/**
 * Utility functions for calculating download states.
 * These functions centralize the logic for determining download status
 * to avoid duplication across components.
 */
object DownloadStateUtils {
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
}

