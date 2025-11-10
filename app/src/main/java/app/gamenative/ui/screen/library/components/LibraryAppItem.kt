package app.gamenative.ui.screen.library.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import app.gamenative.data.GameSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face4
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService
import app.gamenative.ui.enums.DownloadStatus
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.internal.fakeAppInfo
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.DownloadStateUtils
import app.gamenative.ui.util.ListItemImage

@SuppressLint("RememberReturnType")
@Composable
internal fun AppItem(
    modifier: Modifier = Modifier,
    appInfo: LibraryItem,
    onClick: () -> Unit,
    paneType: PaneType = PaneType.LIST,
    onFocus: () -> Unit = {},
    listRefreshTrigger: Int = 0, // Trigger that changes when list refreshes
) {
    var hideText by remember { mutableStateOf(true) }
    var alpha by remember { mutableFloatStateOf(1f) }
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(paneType) {
        hideText = true
        alpha = 1f
        showOverlay = false // Reset when pane type changes
        // Delay showing overlay to ensure image has started loading
        kotlinx.coroutines.delay(100)
        if (paneType != PaneType.LIST) {
            showOverlay = true
        }
    }

    /** Track download progress for overlay in Capsule/Hero views */
    val downloadInfo = remember(appInfo.appId) { SteamService.getAppDownloadInfo(appInfo.gameId) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isJobActive by remember { mutableStateOf(false) }
    val hasPartialDownload = remember(appInfo.appId) { SteamService.hasPartialDownload(appInfo.gameId) }
    var isActivelyDownloading by remember { mutableStateOf(SteamService.isActivelyDownloading(appInfo.gameId)) }

    /** Determine download status based on states **/
    val downloadStatus = remember(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, isActivelyDownloading) {
        DownloadStateUtils.getDownloadStatus(downloadInfo, downloadProgress, isJobActive, hasPartialDownload, isActivelyDownloading = isActivelyDownloading)
    }

    // Show overlay for downloading, paused, or queued states
    val showDownloadOverlay = downloadStatus != null && downloadStatus != DownloadStatus.COMPLETED

    // Function to refresh progress from downloadInfo
    val refreshProgress: () -> Unit = {
        val state = DownloadStateUtils.refreshProgress(downloadInfo)
        downloadProgress = state.downloadProgress
        isJobActive = state.isJobActive
    }

    // Initialize progress when component is created or downloadInfo changes
    remember(downloadInfo) {
        refreshProgress()
    }

    /** Update progress information **/
    // Refresh progress when list reloads or when downloadInfo changes
    LaunchedEffect(appInfo.appId, downloadInfo, listRefreshTrigger) {
        refreshProgress()
         
        // Update active download status
        isActivelyDownloading = SteamService.isActivelyDownloading(appInfo.gameId)

        // Also check for paused state even if downloadInfo is null
        if (downloadInfo == null && hasPartialDownload) {
            // Try to get download info again in case it exists
            val currentDownloadInfo = SteamService.getAppDownloadInfo(appInfo.gameId)
            if (currentDownloadInfo != null) {
                refreshProgress()
            }
        }
    }

    // Listen to real-time progress updates via listener
    DisposableEffect(downloadInfo) {
        // Update state immediately when downloadInfo changes
        downloadInfo?.let {
            downloadProgress = it.getProgress()
            isJobActive = it.isJobActive()
        }

        val onDownloadProgress: (Float) -> Unit = { progress ->
            downloadProgress = progress

            // Update job state when progress changes
            isJobActive = downloadInfo?.isJobActive() ?: false
        }
        downloadInfo?.addProgressListener(onDownloadProgress)

        onDispose {
            downloadInfo?.removeProgressListener(onDownloadProgress)
        }
    }

    /** Handle focus state for controller/keyboard navigation **/
    // True when selected, e.g. with controller
    var isFocused by remember { mutableStateOf(false) }

    // Border is used to highlight selected card
    val border = if (isFocused) {
        androidx.compose.foundation.BorderStroke(
            width = 3.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
            )
        )
    } else {
        androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }

    // Modern card-style item with gradient hover effect
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (isFocused) {
                    onFocus()
                }
            }
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = border,
    ) {
        val outerPadding = if (paneType == PaneType.LIST) {
            // Padding to make text easy to read
            16.dp
        } else {
            0.dp
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(outerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Game icon
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (paneType == PaneType.LIST) {
                    ListItemImage(
                        modifier = Modifier.size(56.dp),
                        imageModifier = Modifier.clip(RoundedCornerShape(10.dp)),
                        image = { appInfo.clientIconUrl }
                    )
                } else {
                    val aspectRatio = if (paneType == PaneType.GRID_CAPSULE) { 2/3f } else { 460/215f }
                    val imageUrl = if (paneType == PaneType.GRID_CAPSULE) {
                        "https://shared.steamstatic.com/store_item_assets/steam/apps/" + appInfo.gameId + "/library_600x900.jpg"
                    } else {
                        "https://shared.steamstatic.com/store_item_assets/steam/apps/" + appInfo.gameId + "/header.jpg"
                    }

                    /** Capsule/Hero view with download overlay **/
                    Box(modifier = Modifier.aspectRatio(aspectRatio)) {
                        ListItemImage(
                            modifier = Modifier.fillMaxSize(),
                            imageModifier = Modifier.clip(RoundedCornerShape(3.dp)).alpha(alpha),
                            image = { imageUrl },
                            onFailure = {
                                hideText = false
                                alpha = 0.1f
                            }
                        )

                        // Download progress overlay for Capsule/Hero views
                        // Only show overlay when image is visible and we've delayed enough for it to load
                        if (showDownloadOverlay && showOverlay && hideText && alpha > 0.5f) {
                            // Calculate overlay height: full height for queued/paused/validating, otherwise based on progress
                            val overlayHeight = when (downloadStatus) {
                                DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.VALIDATING -> 1f
                                else -> 1f - downloadProgress
                            }

                            // Show download status or percentage
                            val statusText = DownloadStateUtils.getDownloadStatusText(
                                downloadStatus = downloadStatus,
                                downloadProgress = downloadProgress,
                                isInstalled = false, // Overlay only shows for download states
                                gameId = appInfo.gameId
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(overlayHeight)
                                    .align(Alignment.TopStart)
                                    .background(
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(3.dp) // Match image rounded corners
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            blurRadius = 4f
                                        )
                                    ),
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                    }

                    // Only display text if the image loading has failed
                    if (! hideText) {
                        GameInfoBlock(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            appInfo = appInfo,
                            listRefreshTrigger = listRefreshTrigger,
                        )
                    } else {
                        val isInstalled = remember(appInfo.appId) {
                            SteamService.isAppInstalled(appInfo.gameId)
                        }
                        // Cute floating icons for install status/family share
                        if (isInstalled || appInfo.isShared) {
                            Row(
                                modifier = Modifier
                                    .align(alignment = Alignment.BottomEnd)
                                    .padding(4.dp) // Padding from the outer card
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) // Mid tone colour that shows up on light and dark images
                                    .height(24.dp)
                                    .padding(2.dp), // Padding for inner icons
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (isInstalled) {
                                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                                if (appInfo.isShared) {
                                    Icon(Icons.Filled.Face4, null, tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }

            if (paneType == PaneType.LIST) {
                GameInfoBlock(
                    modifier = Modifier.weight(1f),
                    appInfo = appInfo,
                    listRefreshTrigger = listRefreshTrigger,
                )

                // Play/Open button
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.open),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@SuppressLint("RememberReturnType")
@Composable
internal fun GameInfoBlock(
    modifier: Modifier,
    appInfo: LibraryItem,
    listRefreshTrigger: Int = 0, // Trigger that changes when list refreshes
) {
    // For text displayed in list view, or as override if image loading fails

    // Get download info for progress tracking
    val downloadInfo = remember(appInfo.appId) { SteamService.getAppDownloadInfo(appInfo.gameId) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isJobActive by remember { mutableStateOf(false) }
    val isInstalled = remember(appInfo.appId) {
        SteamService.isAppInstalled(appInfo.gameId)
    }

    /** Determine download status using consolidated function **/
    val downloadStatus = remember(appInfo.gameId, downloadInfo, downloadProgress, listRefreshTrigger) {
        DownloadStateUtils.getGameDownloadStatus(appInfo.gameId)
    }

    // Function to refresh progress from downloadInfo - can be called from remember and LaunchedEffect
    val refreshProgress: () -> Unit = {
        val state = DownloadStateUtils.refreshProgress(downloadInfo)
        downloadProgress = state.downloadProgress
        isJobActive = state.isJobActive
    }

    // Initialize progress when component is created or downloadInfo changes
    remember(downloadInfo) {
        refreshProgress()
    }

    // Refresh progress when list reloads (for downloading games) or when downloadInfo changes
    LaunchedEffect(appInfo.appId, downloadInfo, listRefreshTrigger) {
        refreshProgress()
        // Also check for paused state even if downloadInfo is null
        val hasPartialDownload = SteamService.hasPartialDownload(appInfo.gameId)
        if (downloadInfo == null && hasPartialDownload) {
            // Try to get download info again in case it exists
            val currentDownloadInfo = SteamService.getAppDownloadInfo(appInfo.gameId)
            if (currentDownloadInfo != null) {
                refreshProgress()
            }
        }
    }

    // Listen to real-time progress updates via listener
    DisposableEffect(downloadInfo) {
        // Update state immediately when downloadInfo changes
        downloadInfo?.let {
            downloadProgress = it.getProgress()
            isJobActive = it.isJobActive()
        }

        val onDownloadProgress: (Float) -> Unit = { progress ->
            downloadProgress = progress

            // Update job state when progress changes
            isJobActive = downloadInfo?.isJobActive() ?: false
        }
        downloadInfo?.addProgressListener(onDownloadProgress)

        onDispose {
            downloadInfo?.removeProgressListener(onDownloadProgress)
        }
    }

    var appSizeOnDisk by remember { mutableStateOf("") }

    var hideText by remember { mutableStateOf(true) }
    var alpha = remember(Int) {1f}

    LaunchedEffect(Unit) {
        if (isInstalled) {
            appSizeOnDisk = "..."
            DownloadService.getSizeOnDiskDisplay(appInfo.gameId) {  appSizeOnDisk = it }
        }
    }

    // Game info
    Column(
        modifier = modifier,
    ) {
        Text(
            text = appInfo.name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Status indicator: Installing / Installed / Not installed
            val statusText = DownloadStateUtils.getDownloadStatusText(
                downloadStatus = downloadStatus,
                downloadProgress = downloadProgress,
                isInstalled = isInstalled,
                gameId = appInfo.gameId
            )
            
            // Clear fresh install flag when status changes from VALIDATING to something else
            LaunchedEffect(downloadStatus) {
                if (downloadStatus != DownloadStatus.VALIDATING) {
                    DownloadStateUtils.clearFreshInstall(appInfo.gameId)
                }
            }
            val statusColor = when {
                downloadStatus != null || isInstalled -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = statusColor, shape = CircleShape)
                )
                // Status text
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
                // Download percentage when downloading (not paused or queued)
                if (downloadStatus == DownloadStatus.DOWNLOADING) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = String.format("%.1f%%", downloadProgress * 100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }

            // Game size on its own line for installed games
            if (isInstalled) {
                Text(
                    text = "$appSizeOnDisk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Family share indicator on its own line if needed
            if (appInfo.isShared) {
                Text(
                    text = stringResource(R.string.family_shared),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

/***********
 * PREVIEW *
 ***********/

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_AppItem() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            LazyColumn(
                modifier = Modifier.padding(16.dp)
            ) {
                items(
                    items = List(5) { idx ->
                        val item = fakeAppInfo(idx)
                        LibraryItem(
                            index = idx,
                            appId = "${GameSource.STEAM.name}_${item.id}",
                            name = item.name,
                            iconHash = item.iconHash,
                            isShared = idx % 2 == 0,
                        )
                    },
                    itemContent = {
                        AppItem(appInfo = it, onClick = {})
                    },
                )
            }
        }
    }
}

@Preview(device = "spec:width=1920px,height=1080px,dpi=440") // Odin2 Mini
@Composable
private fun Preview_AppItemGrid() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        Surface {
            Column {
                val appInfoList = List(4) { idx ->
                    val item = fakeAppInfo(idx)
                    LibraryItem(
                        index = idx,
                        appId = "${GameSource.STEAM.name}_${item.id}",
                        name = item.name,
                        iconHash = item.iconHash,
                        isShared = idx % 2 == 0,
                        gameSource = GameSource.STEAM,
                    )
                }

                // Hero
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 72.dp
                    ),
                ) {
                    items(items = appInfoList, key = { it.index }) { item ->
                        AppItem(
                            appInfo = item,
                            onClick = { },
                            paneType = PaneType.GRID_HERO,
                        )
                    }
                }

                // Capsule
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 72.dp
                    ),
                ) {
                    items(items = appInfoList, key = { it.index }) { item ->
                        AppItem(
                            appInfo = item,
                            onClick = { },
                            paneType = PaneType.GRID_CAPSULE,
                        )
                    }
                }
            }
        }
    }
}
