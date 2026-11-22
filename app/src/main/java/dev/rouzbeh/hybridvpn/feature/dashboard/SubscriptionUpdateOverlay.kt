package dev.rouzbeh.hybridvpn.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rouzbeh.hybridvpn.core.designsystem.PrimaryCyan
import kotlinx.coroutines.launch

/**
 * Full-screen, dark-glass cyberpunk orbital sync overlay.
 *
 * When [state.isLocked] is true:
 * - Completely intercepts and consumes all touch, scroll, drag, and pointer events.
 * - Blocks system Back press/gesture via [BackHandler].
 * - Displays a futuristic glassmorphic card with orbital sync animation, subscription name pill,
 *   and smooth completion feedback badge (SUCCESS / FAILED).
 */
@Composable
fun SubscriptionUpdateOverlay(
    state: SubscriptionUpdateState,
    modifier: Modifier = Modifier
) {
    // Lock Back press / gesture while update cycle & animation are executing
    BackHandler(enabled = state.isLocked) {
        /* Intentionally blocked while subscription update & animation complete */
    }

    AnimatedVisibility(
        visible = state.isLocked,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(260))
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF050914).copy(alpha = 0.80f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val borderColor by animateColorAsState(
                    targetValue = when (state.phase) {
                        SubscriptionUpdatePhase.UPDATING -> PrimaryCyan.copy(alpha = 0.40f)
                        SubscriptionUpdatePhase.SUCCESS -> Color(0xFF00E676).copy(alpha = 0.55f)
                        SubscriptionUpdatePhase.FAILED -> Color(0xFFFF4D6D).copy(alpha = 0.55f)
                        SubscriptionUpdatePhase.IDLE -> Color.Transparent
                    },
                    animationSpec = tween(300),
                    label = "overlayBorderColor"
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0E1629).copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, borderColor),
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .widthIn(min = 250.dp, max = 300.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Orbital Animation Canvas (108.dp)
                        OrbitalAnimationContainer(phase = state.phase)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Subscription Name Pill
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E293B).copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF334155).copy(alpha = 0.60f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = state.targetDisplayName ?: "Subscription",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SoraFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Headline Title
                        val titleText = when (state.phase) {
                            SubscriptionUpdatePhase.UPDATING -> "Updating Subscription"
                            SubscriptionUpdatePhase.SUCCESS -> "Subscription Updated"
                            SubscriptionUpdatePhase.FAILED -> "Update Failed"
                            SubscriptionUpdatePhase.IDLE -> ""
                        }
                        Text(
                            text = titleText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SoraFamily,
                            textAlign = TextAlign.Center
                        )

                        // Subtitle Detail
                        if (!state.detailText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.detailText,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = SoraFamily,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Bottom Animated Accent Track
                        BottomAccentTrack(phase = state.phase)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitalAnimationContainer(
    phase: SubscriptionUpdatePhase,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(108.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "OrbitalStateContent"
        ) { targetPhase ->
            when (targetPhase) {
                SubscriptionUpdatePhase.UPDATING -> UpdatingOrbitalCanvas()
                SubscriptionUpdatePhase.SUCCESS -> SuccessBadgeCanvas()
                SubscriptionUpdatePhase.FAILED -> FailedBadgeCanvas()
                SubscriptionUpdatePhase.IDLE -> Spacer(modifier = Modifier.size(108.dp))
            }
        }
    }
}

@Composable
private fun UpdatingOrbitalCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "UpdatingOrbital")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )
    val cometRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cometRotation"
    )
    val coreBreathing by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreathing"
    )
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val outerRadius = maxRadius * 0.88f
        val cometRadius = maxRadius * 0.70f
        val coreRadius = maxRadius * 0.42f * coreBreathing

        // 1. Pulsing radial glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryCyan.copy(alpha = 0.28f * haloPulse), Color.Transparent),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // 2. Outer dashed orbital track rotating clockwise
        rotate(degrees = outerRotation, pivot = center) {
            drawCircle(
                color = PrimaryCyan.copy(alpha = 0.22f),
                radius = outerRadius,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            )

            // 3 orbiting luminous data nodes around outer track at 0, 120, 240 deg
            val nodeAngles = floatArrayOf(0f, 120f, 240f)
            for (angle in nodeAngles) {
                val rad = Math.toRadians(angle.toDouble())
                val nodeX = center.x + outerRadius * kotlin.math.cos(rad).toFloat()
                val nodeY = center.y + outerRadius * kotlin.math.sin(rad).toFloat()
                drawCircle(
                    color = PrimaryCyan.copy(alpha = 0.35f),
                    radius = 5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }
        }

        // 3. Inner sweeping comet gradient arc rotating counter-clockwise
        rotate(degrees = cometRotation, pivot = center) {
            val sweepBrush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFF7C4DFF).copy(alpha = 0.05f),
                    PrimaryCyan.copy(alpha = 0.65f),
                    PrimaryCyan
                ),
                center = center
            )
            drawArc(
                brush = sweepBrush,
                startAngle = 0f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(center.x - cometRadius, center.y - cometRadius),
                size = Size(cometRadius * 2f, cometRadius * 2f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 4. Center glowing glass core circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryCyan.copy(alpha = 0.30f), Color(0xFF0E1629).copy(alpha = 0.88f)),
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )
        drawCircle(
            color = PrimaryCyan.copy(alpha = 0.50f),
            radius = coreRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // 5. Crisp vector sync mark drawn directly in Canvas
        rotate(degrees = syncRotation, pivot = center) {
            val syncArcRadius = coreRadius * 0.52f
            val syncStrokeWidth = 2.dp.toPx()

            // Arc 1: upper semi-arc
            drawArc(
                color = PrimaryCyan,
                startAngle = 20f,
                sweepAngle = 135f,
                useCenter = false,
                topLeft = Offset(center.x - syncArcRadius, center.y - syncArcRadius),
                size = Size(syncArcRadius * 2f, syncArcRadius * 2f),
                style = Stroke(width = syncStrokeWidth, cap = StrokeCap.Round)
            )
            // Arrowhead 1 at ~155°
            val rad1 = Math.toRadians(155.0)
            val tip1X = center.x + syncArcRadius * kotlin.math.cos(rad1).toFloat()
            val tip1Y = center.y + syncArcRadius * kotlin.math.sin(rad1).toFloat()
            val arrowLen = 4.dp.toPx()
            drawLine(
                color = PrimaryCyan,
                start = Offset(tip1X, tip1Y),
                end = Offset(tip1X - arrowLen * 0.7f, tip1Y - arrowLen * 0.7f),
                strokeWidth = syncStrokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = PrimaryCyan,
                start = Offset(tip1X, tip1Y),
                end = Offset(tip1X + arrowLen * 0.7f, tip1Y - arrowLen * 0.7f),
                strokeWidth = syncStrokeWidth,
                cap = StrokeCap.Round
            )

            // Arc 2: lower semi-arc
            drawArc(
                color = PrimaryCyan,
                startAngle = 200f,
                sweepAngle = 135f,
                useCenter = false,
                topLeft = Offset(center.x - syncArcRadius, center.y - syncArcRadius),
                size = Size(syncArcRadius * 2f, syncArcRadius * 2f),
                style = Stroke(width = syncStrokeWidth, cap = StrokeCap.Round)
            )
            // Arrowhead 2 at ~335°
            val rad2 = Math.toRadians(335.0)
            val tip2X = center.x + syncArcRadius * kotlin.math.cos(rad2).toFloat()
            val tip2Y = center.y + syncArcRadius * kotlin.math.sin(rad2).toFloat()
            drawLine(
                color = PrimaryCyan,
                start = Offset(tip2X, tip2Y),
                end = Offset(tip2X + arrowLen * 0.7f, tip2Y + arrowLen * 0.7f),
                strokeWidth = syncStrokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = PrimaryCyan,
                start = Offset(tip2X, tip2Y),
                end = Offset(tip2X - arrowLen * 0.7f, tip2Y + arrowLen * 0.7f),
                strokeWidth = syncStrokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SuccessBadgeCanvas() {
    val rippleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            checkProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val emerald = Color(0xFF00E676)
        val badgeRadius = maxRadius * 0.54f

        // Expanding emerald ripple ring
        val rippleRadius = badgeRadius + (maxRadius - badgeRadius) * rippleProgress.value
        val rippleAlpha = (1f - rippleProgress.value).coerceIn(0f, 1f) * 0.65f
        if (rippleAlpha > 0.01f) {
            drawCircle(
                color = emerald.copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Emerald halo glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(emerald.copy(alpha = 0.32f), Color.Transparent),
                center = center,
                radius = badgeRadius * 1.45f
            ),
            radius = badgeRadius * 1.45f,
            center = center
        )

        // Core badge fill & border
        drawCircle(
            color = emerald.copy(alpha = 0.16f),
            radius = badgeRadius,
            center = center
        )
        drawCircle(
            color = emerald,
            radius = badgeRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Animated checkmark
        val p1 = Offset(center.x - 14.dp.toPx(), center.y - 1.dp.toPx())
        val p2 = Offset(center.x - 4.dp.toPx(), center.y + 9.dp.toPx())
        val p3 = Offset(center.x + 15.dp.toPx(), center.y - 10.dp.toPx())

        val fullCheck = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
        }
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(fullCheck, false)
        val dst = Path()
        pathMeasure.getSegment(0f, pathMeasure.length * checkProgress.value, dst, true)
        drawPath(
            path = dst,
            color = emerald,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun FailedBadgeCanvas() {
    val rippleProgress = remember { Animatable(0f) }
    val xProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            xProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        val coral = Color(0xFFFF4D6D)
        val badgeRadius = maxRadius * 0.54f

        // Soft coral pulse ring
        val rippleRadius = badgeRadius + (maxRadius - badgeRadius) * rippleProgress.value
        val rippleAlpha = (1f - rippleProgress.value).coerceIn(0f, 1f) * 0.65f
        if (rippleAlpha > 0.01f) {
            drawCircle(
                color = coral.copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Coral halo glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coral.copy(alpha = 0.32f), Color.Transparent),
                center = center,
                radius = badgeRadius * 1.45f
            ),
            radius = badgeRadius * 1.45f,
            center = center
        )

        // Core badge fill & border
        drawCircle(
            color = coral.copy(alpha = 0.16f),
            radius = badgeRadius,
            center = center
        )
        drawCircle(
            color = coral,
            radius = badgeRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Animated X mark
        val pSize = 11.dp.toPx()
        val prog = xProgress.value
        val arm1End = Offset(
            center.x - pSize + 2f * pSize * prog,
            center.y - pSize + 2f * pSize * prog
        )
        drawLine(
            color = coral,
            start = Offset(center.x - pSize, center.y - pSize),
            end = arm1End,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        val arm2End = Offset(
            center.x + pSize - 2f * pSize * prog,
            center.y - pSize + 2f * pSize * prog
        )
        drawLine(
            color = coral,
            start = Offset(center.x + pSize, center.y - pSize),
            end = arm2End,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun BottomAccentTrack(
    phase: SubscriptionUpdatePhase,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.50f))
    ) {
        when (phase) {
            SubscriptionUpdatePhase.UPDATING -> {
                val infiniteTransition = rememberInfiniteTransition(label = "BottomShimmer")
                val shimmerShift by infiniteTransition.animateFloat(
                    initialValue = -0.4f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "bottomShimmerShift"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val startX = w * (shimmerShift - 0.35f)
                    val endX = w * (shimmerShift + 0.35f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                PrimaryCyan.copy(alpha = 0.70f),
                                Color(0xFF7C4DFF),
                                PrimaryCyan,
                                Color.Transparent
                            ),
                            startX = startX,
                            endX = endX
                        )
                    )
                }
            }
            SubscriptionUpdatePhase.SUCCESS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF00E676))
                )
            }
            SubscriptionUpdatePhase.FAILED -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFF4D6D))
                )
            }
            SubscriptionUpdatePhase.IDLE -> Unit
        }
    }
}
