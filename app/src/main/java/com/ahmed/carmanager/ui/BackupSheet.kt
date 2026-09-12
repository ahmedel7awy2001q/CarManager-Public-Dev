package com.ahmed.carmanager.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSheet(onDismiss: () -> Unit, onExport: (Uri) -> Unit, onImport: (Uri) -> Unit) {
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    val filename = remember {
        "CarManager-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json"
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PremiumGradientHeader(
            title = "النسخ الاحتياطي",
            subtitle = "حماية نسخة كاملة من ملف المركبة قبل أي تغيير أو نقل جهاز",
            icon = CMIcons.Backup
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AutomotiveActionCard(
                title = "تصدير نسخة",
                subtitle = "JSON محمول",
                icon = Icons.Rounded.Download,
                tone = AutoTone.GREEN,
                modifier = Modifier.weight(1f),
                filled = true,
                onClick = { exporter.launch(filename) }
            )
            AutomotiveActionCard(
                title = "استعادة",
                subtitle = "تحقق قبل الاستبدال",
                icon = Icons.Rounded.Upload,
                tone = AutoTone.BLUE,
                modifier = Modifier.weight(1f),
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            )
        }

        PremiumSurfaceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AutomotiveIconBadge(Icons.Rounded.Inventory2, AutoTone.VIOLET, size = 46)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("ماذا تشمل النسخة؟", fontWeight = FontWeight.ExtraBold)
                    Text(
                        "المركبات والصيانة والوقود والرحلات والمصاريف وGPS والمستندات والأعطال وباقي السجلات المحلية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = autoToneColors(AutoTone.AMBER).soft
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(19.dp), tint = autoToneColors(AutoTone.AMBER).strong)
                Spacer(Modifier.width(8.dp))
                Text(
                    "الاستعادة لا تبدأ إلا بعد اختيار الملف وتأكيدك. يتم فحص الملف أولًا، ويُفضّل الاحتفاظ بنسخة حديثة قبل الاستعادة.",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = autoToneColors(AutoTone.AMBER).onSoft,
                    textAlign = TextAlign.End
                )
            }
        }

        FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
        Spacer(Modifier.height(16.dp))
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            icon = { AutomotiveIconBadge(Icons.Rounded.Restore, AutoTone.AMBER, size = 46) },
            title = { Text("تأكيد الاستعادة") },
            text = { Text("سيتم استبدال بيانات التطبيق الحالية بالنسخة المختارة بعد التحقق منها. يفضّل إنشاء نسخة احتياطية للحالة الحالية أولًا.") },
            confirmButton = { Button(onClick = { onImport(uri); pendingImport = null; onDismiss() }) { Text("استعادة") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("إلغاء") } }
        )
    }
}
