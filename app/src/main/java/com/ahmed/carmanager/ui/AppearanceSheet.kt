package com.ahmed.carmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.settings.AccentPalette
import com.ahmed.carmanager.data.settings.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    themeMode: AppThemeMode,
    accentPalette: AccentPalette,
    onThemeMode: (AppThemeMode) -> Unit,
    onAccent: (AccentPalette) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            PremiumGradientHeader(
                title = "المظهر",
                subtitle = "اختر تجربة Cockpit تناسبك بدون أي تأثير على بيانات المركبة",
                icon = Icons.Rounded.Palette
            )

            AppearancePreview(accentPalette)

            AutomotiveSectionTitle("وضع العرض", "يتبع النظام أو يثبت الوضع الذي تختاره")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeCard("النظام", Icons.Rounded.SettingsBrightness, themeMode == AppThemeMode.SYSTEM, { onThemeMode(AppThemeMode.SYSTEM) }, Modifier.weight(1f), AutoTone.GRAPHITE)
                ThemeModeCard("فاتح", Icons.Rounded.LightMode, themeMode == AppThemeMode.LIGHT, { onThemeMode(AppThemeMode.LIGHT) }, Modifier.weight(1f), AutoTone.AMBER)
                ThemeModeCard("داكن", Icons.Rounded.DarkMode, themeMode == AppThemeMode.DARK, { onThemeMode(AppThemeMode.DARK) }, Modifier.weight(1f), AutoTone.VIOLET)
            }

            AutomotiveSectionTitle("هوية اللون", "اللون الأساسي للأزرار والحالات النشطة؛ ألوان الأقسام تظل دلالية")
            AccentPalette.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { palette ->
                        AccentCard(palette, accentPalette == palette, { onAccent(palette) }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "الأحمر يظل للخطر، والأخضر للحالة الجيدة، والأزرق للتتبع والوقود، والذهبي للتكلفة؛ تغيير الهوية لا يزيل هذه الدلالات.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearancePreview(accentPalette: AccentPalette) {
    val preview = palettePreview(accentPalette)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF0A171D),
        border = BorderStroke(1.dp, preview.copy(alpha = .35f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AutoTone.CORAL, AutoTone.BLUE, AutoTone.GREEN, AutoTone.AMBER).forEach { tone ->
                        Box(Modifier.size(9.dp).clip(CircleShape).background(autoToneColors(tone).strong))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("CarManager", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("Premium Automotive", color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(120.dp).height(5.dp).clip(CircleShape).background(preview))
            }
        }
    }
}

@Composable
private fun ThemeModeCard(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier, tone: AutoTone) {
    val c = autoToneColors(tone)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) c.soft else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.strong else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = c.strong)
            Spacer(Modifier.height(6.dp))
            Text(title, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
            if (selected) {
                Spacer(Modifier.height(4.dp))
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp), tint = c.strong)
            }
        }
    }
}

@Composable
private fun AccentCard(palette: AccentPalette, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val preview = palettePreview(palette)
    val label = when (palette) {
        AccentPalette.OCEAN -> "محيطي"
        AccentPalette.EMERALD -> "زمردي"
        AccentPalette.CRIMSON -> "قرمزي"
        AccentPalette.VIOLET -> "بنفسجي"
        AccentPalette.AMBER -> "كهرماني"
        AccentPalette.GRAPHITE -> "جرافيت"
    }
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) preview else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(preview), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

private fun palettePreview(palette: AccentPalette): Color = when (palette) {
    AccentPalette.OCEAN -> Color(0xFF008FA1)
    AccentPalette.EMERALD -> Color(0xFF1E7B55)
    AccentPalette.CRIMSON -> Color(0xFFA4343B)
    AccentPalette.VIOLET -> Color(0xFF6250A5)
    AccentPalette.AMBER -> Color(0xFF97611A)
    AccentPalette.GRAPHITE -> Color(0xFF3F596A)
}
