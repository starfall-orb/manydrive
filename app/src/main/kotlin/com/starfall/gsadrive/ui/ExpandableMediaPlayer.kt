package com.starfall.gsadrive.ui

import com.starfall.gsadrive.tr

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.starfall.gsadrive.MediaPagePlayback
import com.starfall.gsadrive.PlaybackSourceRegistry
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.isMediaPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The horizontal pager is the viewer itself, not a swipe animation layered over one fixed item.
 * Items run left to right: drag from right to left for next, left to right for previous.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun ExpandableMediaPlayer(
    player: Player,
    minimized: Boolean,
    topPadding: Dp,
    miniBounds: Rect?,
    onMinimize: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    queue: List<DriveFile>,
    index: Int,
    previewPaths: Map<String, String>,
    error: String? = null,
    onSwipeTo: (Int) -> Unit
) {
    var fullscreen by remember { mutableStateOf(false) }
    var landscapeVideo by remember(player) {
        mutableStateOf(player.videoSize.width > player.videoSize.height && player.videoSize.height > 0)
    }
    val view = LocalView.current

    SideEffect {
        if (!minimized) {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightNavigationBars = false
        }
    }

    BackHandler(enabled = !minimized) {
        if (fullscreen) fullscreen = false else onMinimize()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    landscapeVideo = player.videoSize.width > player.videoSize.height && player.videoSize.height > 0
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(minimized) {
        if (minimized) fullscreen = false
    }

    DisposableEffect(fullscreen, landscapeVideo, view) {
        val activity = view.context as? Activity
        if (activity != null) {
            val bars = WindowCompat.getInsetsController(activity.window, view)
            if (fullscreen) {
                bars.hide(WindowInsetsCompat.Type.systemBars())
                bars.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (landscapeVideo) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            } else {
                bars.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (fullscreen && activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val pageOwner by MediaPagePlayback.owner.collectAsState()
    val playlistRevision = pageOwner?.playlistRevision?.collectAsState()?.value
    // Warm adjacent pages once, then keep at most prev/current/next resident for the viewer's
    // lifetime. Creating/releasing ExoPlayers on every mini/full transition caused frame spikes.
    var neighborsComposed by remember { mutableStateOf(!minimized) }
    LaunchedEffect(pageOwner, queue) {
        if (!neighborsComposed) {
            delay(300)
            neighborsComposed = true
        }
    }
    val visibleMediaIds = remember(queue, index, neighborsComposed, playlistRevision) {
        val visibleFiles = (if (neighborsComposed) listOf(index - 1, index, index + 1) else listOf(index))
            .mapNotNull(queue::getOrNull).map { it.id }.toSet()
        PlaybackSourceRegistry.all().filter { it.file.id in visibleFiles }.map { it.mediaId }.toSet()
    }
    // Apply the window before page acquisition effects, including images between media items.
    DisposableEffect(pageOwner, visibleMediaIds) {
        pageOwner?.setVisiblePages(visibleMediaIds)
        onDispose { }
    }
    DisposableEffect(pageOwner) {
        onDispose { pageOwner?.setVisiblePages(null) }
    }

    val currentMinimize by rememberUpdatedState(onMinimize)
    val currentExpand by rememberUpdatedState(onExpand)
    val currentSwipe by rememberUpdatedState(onSwipeTo)
    val currentIndex by rememberUpdatedState(index)

    val density = LocalDensity.current
    val systemBottomInset = WindowInsets.safeDrawing.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val miniHeight = miniBounds?.height ?: with(density) { 72.dp.toPx() }
        val miniTop = miniBounds?.top ?: (constraints.maxHeight - systemBottomInset - miniHeight)
        // Draw the player behind the transparent navigation bar. Controls apply their own
        // navigation-bar inset, so the content remains edge-to-edge without losing safe area.
        val fullHeight = constraints.maxHeight.toFloat()
        val safeInitialIndex = index.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
        val pager = rememberPagerState(initialPage = safeInitialIndex, pageCount = { queue.size })

        LaunchedEffect(index, queue.size) {
            val target = index
            if (target in queue.indices && !pager.isScrollInProgress && pager.currentPage != target) {
                pager.animateScrollToPage(target)
            }
        }
        LaunchedEffect(pager, queue) {
            snapshotFlow { pager.isScrollInProgress to pager.settledPage }
                .distinctUntilChanged()
                .collect { (scrolling, page) ->
                    if (!scrolling && page in queue.indices && page != currentIndex) currentSwipe(page)
                }
        }

        var dragFraction by remember(player) { mutableStateOf<Float?>(null) }
        var dragDistance by remember(player) { mutableFloatStateOf(0f) }
        val fraction by animateFloatAsState(
            targetValue = dragFraction ?: if (minimized) 1f else 0f,
            animationSpec = if (dragFraction != null) snap() else tween(240),
            label = "media screen transition"
        )
        val fullTop = 0f
        val height = fullHeight + (miniHeight - fullHeight) * fraction
        val y = fullTop + (miniTop - fullTop) * fraction
        val travel = (miniTop - fullTop).coerceAtLeast(1f)
        val miniChromeAlpha = ((fraction - 0.94f) / 0.06f).coerceIn(0f, 1f)
        val headerAlpha = ((0.30f - fraction) / 0.30f).coerceIn(0f, 1f)

        Box(
            Modifier.offset { IntOffset(0, y.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { height.toDp() })
                .background(Color.Black)
                .clipToBounds()
                .pointerInput(player, fullscreen) {
                    if (fullscreen) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction = fraction; dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount
                            dragFraction = ((dragFraction ?: fraction) + amount / travel).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val collapse = when {
                                dragDistance > 64.dp.toPx() -> true
                                dragDistance < -64.dp.toPx() -> false
                                else -> (dragFraction ?: fraction) >= 0.5f
                            }
                            if (collapse) currentMinimize() else currentExpand()
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            MediaViewer(
                player = player,
                alwaysShowControls = queue.getOrNull(index)?.mimeType?.startsWith("audio/") == true,
                compactFraction = fraction,
                title = queue.getOrNull(index)?.name.orEmpty(),
                onExpand = onExpand,
                onClose = onClose,
                interactive = queue.getOrNull(index)?.let { isMediaPreview(it) } == true,
                miniChromeAlpha = miniChromeAlpha,
                fullscreen = fullscreen,
                onToggleFullscreen = { fullscreen = !fullscreen }
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HorizontalPager(
                        state = pager,
                        reverseLayout = false,
                        modifier = Modifier.fillMaxSize().background(Color.Black).testTag("media-pager"),
                        userScrollEnabled = !minimized && fraction < 0.05f,
                        beyondViewportPageCount = if (neighborsComposed) 1 else 0,
                        key = { page -> queue[page].id }
                    ) { page ->
                        val item = queue[page]
                        Box(Modifier.fillMaxSize().testTag("media-page-${item.id}")) {
                            if (page == index && error != null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(error, color = Color.White, modifier = Modifier.padding(24.dp))
                                }
                            } else if (item.mimeType.startsWith("image/")) {
                                val path = previewPaths[item.id]
                                if (path != null) ImageViewer(path)
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (page == index || (neighborsComposed && abs(page - index) == 1)) {
                                MediaItemPage(item = item, selected = page == index, compactFraction = fraction)
                            } else {
                                Box(Modifier.fillMaxSize().background(Color.Black))
                            }
                        }
                    }
                }
            }

            if (!fullscreen && headerAlpha > 0f) {
                Row(
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = headerAlpha }
                        .statusBarsPadding()
                        .height(64.dp)
                        .background(Color.Black)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = tr("Minimize player"),
                            tint = Color.White
                        )
                    }
                    Text(
                        queue.getOrNull(index)?.name.orEmpty(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Each keyed pager page keeps the same player and surface before, during and after selection. */
@OptIn(UnstableApi::class)
@Composable
private fun MediaItemPage(
    item: DriveFile,
    selected: Boolean,
    compactFraction: Float
) {
    val owner by MediaPagePlayback.owner.collectAsState()
    val revision = owner?.playlistRevision?.collectAsState()?.value
    val source = PlaybackSourceRegistry.all().firstOrNull { it.file.id == item.id }
    var pagePlayer by remember(owner, source?.mediaId) { mutableStateOf<Player?>(null) }
    DisposableEffect(owner, source?.mediaId, revision) {
        val borrowed = source?.let { owner?.acquire(it.mediaId) }
        pagePlayer = borrowed
        onDispose {
            if (borrowed != null) owner?.relinquish(source.mediaId, borrowed)
        }
    }
    val mediaPlayer = pagePlayer
    if (mediaPlayer == null) {
        AdjacentPreview(item)
    } else {
        MediaSurface(
            player = mediaPlayer,
            audio = item.mimeType.startsWith("audio/"),
            compactFraction = compactFraction,
            title = item.name,
            selected = selected
        )
    }
}
