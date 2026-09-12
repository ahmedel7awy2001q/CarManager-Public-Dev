package com.ahmed.carmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Compact premium primitives shared by data-heavy screens. */
object CMPremium {
    val ScreenPadding = 12.dp
    val SectionGap = 12.dp
    val CardGap = 7.dp
    val CardRadius = 14.dp
    val SmallRadius = 11.dp
}

@Composable
fun PremiumSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .68f)
    else MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(CMPremium.CardRadius)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
fun PremiumIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Int = 34
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size((size * .48f).dp), tint = tone)
    }
}

@Composable
fun PremiumSectionTitle(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (actionText != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
            ) { Text(actionText, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.width(3.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PremiumMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PremiumIconBadge(
                icon = icon,
                tone = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                container = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                size = 28
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    val shape = RoundedCornerShape(13.dp)
    val color = if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.surface
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp
        ) { Column(Modifier.padding(8.dp), content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp
        ) { Column(Modifier.padding(8.dp), content = content) }
    }
}

@Composable
fun PremiumStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    kind: PremiumStatusKind = PremiumStatusKind.NEUTRAL
) {
    val container = when (kind) {
        PremiumStatusKind.GOOD -> MaterialTheme.colorScheme.secondaryContainer
        PremiumStatusKind.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        PremiumStatusKind.DANGER -> MaterialTheme.colorScheme.errorContainer
        PremiumStatusKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (kind) {
        PremiumStatusKind.GOOD -> MaterialTheme.colorScheme.secondary
        PremiumStatusKind.WARNING -> MaterialTheme.colorScheme.tertiary
        PremiumStatusKind.DANGER -> MaterialTheme.colorScheme.error
        PremiumStatusKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = CircleShape, color = container) {
        Text(
            text,
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

enum class PremiumStatusKind { GOOD, WARNING, DANGER, NEUTRAL }

data class DataReadinessItem(val label: String, val detail: String, val complete: Boolean, val points: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataReadinessSheet(
    score: Int,
    items: List<DataReadinessItem>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
                    CircularProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("$score%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("جاهزية التحليل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "تقيس توفر البيانات اللازمة لدقة التقارير والتنبيهات، وليست تقييمًا لحالة السيارة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.complete) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            Modifier.size(18.dp),
                            tint = if (item.complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("+${item.points}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(4.dp))
                                Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            }
                            Text(item.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("تم") }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun PremiumChevron() {
    Icon(CMIcons.Chevron, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PremiumGradientHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(15.dp)
    val brush = Brush.linearGradient(listOf(Color(0xFF091920), Color(0xFF12353D), Color(0xFF13232B)))
    val base = modifier.fillMaxWidth().clip(shape).background(brush)
        .border(1.dp, Color(0xFF52D4DE).copy(alpha = .14f), shape)

    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .08f)) {
                Icon(icon, null, Modifier.padding(6.dp).size(19.dp), tint = Color(0xFF65DCE5))
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.End)
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f), textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = base, color = Color.Transparent) { body() }
    else Box(base) { body() }
}
