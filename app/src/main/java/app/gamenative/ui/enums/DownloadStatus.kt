package app.gamenative.ui.enums

import androidx.annotation.StringRes
import app.gamenative.R

enum class DownloadStatus(@StringRes val stringRes: Int) {
    COMPLETED(R.string.completed),
    VALIDATING(R.string.validating),
    DOWNLOADING(R.string.downloading_status),
    PAUSED(R.string.paused),
    QUEUED(R.string.queued),
}

