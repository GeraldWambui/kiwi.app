package co.juneai.june.ui.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * High-performance, physics-based spring animation spec for butter-smooth shared element morphing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val GalleryBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = Rect.VisibilityThreshold
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedElementContainer(
    content: @Composable SharedTransitionScope.() -> Unit
) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * Attaches a shared element transition to any thumbnail or fullscreen media element.
 * Gracefully no-ops if outside a SharedTransitionScope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMediaElement(
    key: Any,
    zIndexInOverlay: Float = 0f
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    return if (sharedScope != null && navScope != null) {
        with(sharedScope) {
            this@sharedMediaElement.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = navScope,
                boundsTransform = GalleryBoundsTransform,
                zIndexInOverlay = zIndexInOverlay
            )
        }
    } else {
        this
    }
}
