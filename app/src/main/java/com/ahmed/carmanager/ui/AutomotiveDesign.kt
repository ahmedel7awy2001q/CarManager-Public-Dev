package com.ahmed.carmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Distinctive multi-colour automotive palette used for meaning, not decoration. */
enum class AutoTone { TEAL, BLUE, GREEN, AMBER, CORAL, VIOLET, RED, GRAPHITE }

data class AutoToneColors(val strong: Color, val soft: Color, val onSoft: Color)

@Composable
fun autoToneColors(tone: AutoTone): AutoToneColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    return if (!dark) when (tone) {
        AutoTone.TEAL -> AutoToneColors(Color(0xFF008FA1), Color(0xFFDDF7F9), Color(0xFF04505A))
        AutoTone.BLUE -> AutoToneColors(Color(0xFF286BE6), Color(0xFFE6EEFF), Color(0xFF17438F))
        AutoTone.GREEN -> AutoToneColors(Color(0xFF16966B), Color(0xFFDDF6EA), Color(0xFF0E5B42))
        AutoTone.AMBER -> AutoToneColors(Color(0xFFC98512), Color(0xFFFFF1D2), Color(0xFF704807))
        AutoTone.CORAL -> AutoToneColors(Color(0xFFE05A52), Color(0xFFFFE6E3), Color(0xFF8A2E2A))
        AutoTone.VIOLET -> AutoToneColors(Color(0xFF7558C8), Color(0xFFF0EAFF), Color(0xFF46347E))
        AutoTone.RED -> AutoToneColors(Color(0xFFC93D42), Color(0xFFFFE4E6), Color(0xFF7A2024))
        AutoTone.GRAPHITE -> AutoToneColors(Color(0xFF263843), Color(0xFFE7EDF0), Color(0xFF20313A))
    } else when (tone) {
        AutoTone.TEAL -> AutoToneColors(Color(0xFF5EDBE5), Color(0xFF143C42), Color(0xFFBDF8FC))
        AutoTone.BLUE -> AutoToneColors(Color(0xFF88AFFF), Color(0xFF1B3157), Color(0xFFD9E5FF))
        AutoTone.GREEN -> AutoToneColors(Color(0xFF72DCAE), Color(0xFF173D30), Color(0xFFCFF7E6))
        AutoTone.AMBER -> AutoToneColors(Color(0xFFF4C66E), Color(0xFF46371E), Color(0xFFFFEBC4))
        AutoTone.CORAL -> AutoToneColors(Color(0xFFFF9B93), Color(0xFF4C2928), Color(0xFFFFD9D5))
        AutoTone.VIOLET -> AutoToneColors(Color(0xFFC2ADFF), Color(0xFF372E53), Color(0xFFEDE6FF))
        AutoTone.RED -> AutoToneColors(Color(0xFFFFA2A6), Color(0xFF51272A), Color(0xFFFFDADD))
        AutoTone.GRAPHITE -> AutoToneColors(Color(0xFFC4D3DB), Color(0xFF26343D), Color(0xFFEAF1F5))
    }
}

@Composable
fun AutomotiveIconBadge(icon: ImageVector, tone: AutoTone, modifier: Modifier = Modifier, size: Int = 36) {
    val c = autoToneColors(tone)
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size * .30f).dp)).background(c.soft.copy(alpha = .88f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size((size * .45f).dp), tint = c.strong)
    }
}

/**
 * Compact premium action used throughout the app. The icon sits beside the text rather than above
 * it so common actions consume less vertical space. The translucent pearl surface gives a light
 * aqua/glass character without blur, shaders, or other expensive effects.
 */
@Composable
fun AutomotiveActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: AutoTone,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val c = autoToneColors(tone)
    val container = if (filled) c.strong else c.soft.copy(alpha = .58f)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (filled) Color.White.copy(alpha = .16f) else c.strong.copy(alpha = .22f)
        ),
        shadowElevation = if (filled) 1.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (filled) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.surface.copy(alpha = .80f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(15.dp), tint = if (filled) Color.White else c.strong)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (filled) Color.White.copy(alpha = .76f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AutomotiveMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tone: AutoTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = autoToneColors(tone)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AutomotiveIconBadge(icon, tone, size = 28)
                Spacer(Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Spacer(Modifier.height(3.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(c.soft)) {
                Box(Modifier.fillMaxWidth(.62f).height(2.dp).background(c.strong))
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            content = body
        )
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            content = body
        )
    }
}

@Composable
fun AutomotiveSectionTitle(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

/** Compact feature tile: a restrained glass-like surface without blur or expensive effects. */
@Composable
fun AutomotiveFeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: AutoTone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = autoToneColors(tone)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.strong.copy(alpha = .16f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutomotiveIconBadge(icon, tone, size = 30)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
