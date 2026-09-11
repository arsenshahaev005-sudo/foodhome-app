package market.foodhome.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import market.foodhome.app.R

/** Matches the site's WebAppLaunchOverlay: original wordmark, flat background, three dots. */
@Composable
internal fun FoodHomeLaunchSurface() {
    val description = stringResource(R.string.launch_loading)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(colorResource(R.color.foodhome_launch_background))
            .testTag("foodhome.shell.loading")
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        val logoWidth = minOf(maxWidth * 0.76f, 280.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.foodhome_wordmark),
                contentDescription = null,
                modifier = Modifier.width(logoWidth)
                    .aspectRatio(2850f / 500f)
                    .testTag("foodhome.shell.logo"),
            )
            val transition = rememberInfiniteTransition(label = "launch-dots")
            val rise = with(LocalDensity.current) { 4.dp.toPx() }
            val accent = colorResource(R.color.foodhome_launch_accent)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    // Compose animations obey the system animator duration scale (including zero).
                    val pulse by transition.animateFloat(
                        initialValue = 0f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 900
                                0f at 0; 1f at 360; 0f at 720; 0f at 900
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(index * 120),
                        ),
                        label = "launch-dot-$index",
                    )
                    Box(Modifier.size(6.dp).graphicsLayer {
                        alpha = 0.28f + 0.72f * pulse
                        translationY = -rise * pulse
                    }.background(accent, CircleShape).testTag("foodhome.shell.dot.$index"))
                }
            }
        }
    }
}
