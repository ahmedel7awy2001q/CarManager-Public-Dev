package com.ahmed.carmanager.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * High-impact automotive visual primitives used by v0.8.
 * These deliberately avoid generic "card + icon + text" repetition and make the important data
 * feel closer to a real vehicle cockpit while keeping all values factual and readable.
 */
@Composable
fun AutomotiveGaugeMeter(
    progress: Float,
    value: String,
    label: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "automotive-gauge"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().height(76.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - inset * 2f, size.height * 1.55f)
                val top = size.height * .10f
                val start = 200f
                val sweep = 140f
                drawArc(
                    color = Color.White.copy(alpha = .12f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, top),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = start,
                    sweepAngle = sweep * animated,
                    useCenter = false,
                    topLeft = Offset(inset, top),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = .09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(19.dp), tint = accent)
                }
            }
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .78f), maxLines = 1)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .54f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AutomotiveDonutChart(
    values: List<Double>,
    colors: List<Color>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val clean = values.map { it.coerceAtLeast(0.0) }
    val total = clean.sum()
    val gap = 5f
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fallbackColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2f + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (total > 0.0) {
                var start = -90f
                clean.forEachIndexed { index, value ->
                    if (value > 0.0) {
                        val rawSweep = (value / total * 360.0).toFloat()
                        val sweep = (rawSweep - gap).coerceAtLeast(1.5f)
                        drawArc(
                            color = colors.getOrElse(index) { fallbackColor },
                            startAngle = start + gap / 2f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        start += rawSweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AutomotiveLiveStatus(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = autoToneColors(if (active) AutoTone.GREEN else AutoTone.GRAPHITE)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tone.soft)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tone.strong))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone.onSoft)
    }
}
