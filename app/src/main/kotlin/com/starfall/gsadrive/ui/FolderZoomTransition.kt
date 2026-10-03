package com.starfall.gsadrive.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private data class FolderTransitionTarget(
    val key: String,
    val depth: Int
)

@Composable
internal fun FolderZoomTransition(
    key: String,
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = FolderTransitionTarget(key, depth),
        transitionSpec = {
            val openingFolder = targetState.depth >= initialState.depth
            val enterScale = if (openingFolder) 0.94f else 1.04f
            val exitScale = if (openingFolder) 1.04f else 0.94f

            (scaleIn(
                initialScale = enterScale,
                animationSpec = tween(150, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(90))).togetherWith(
                scaleOut(
                    targetScale = exitScale,
                    animationSpec = tween(120, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(90))
            )
        },
        modifier = modifier,
        label = "Folder zoom"
    ) {
        content()
    }
}
