package com.ahmed.carmanager.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.diagnostics.AppCrashDiagnostics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("DEPRECATION")
fun AppDiagnosticsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(AppCrashDiagnostics.lastCrash(context)) }
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "—"
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode?.toString() else packageInfo?.versionCode?.toString()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            PremiumGradientHeader(
                title = "تشخيص التطبيق",
                subtitle = "معلومات تقنية محلية تساعدنا في معرفة سبب أي توقف مفاجئ دون رفع بياناتك تلقائيًا.",
                icon = Icons.Rounded.BugReport
            )
            Spacer(Modifier.height(14.dp))
            PremiumSurfaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PremiumIconBadge(Icons.Rounded.Verified, tone = MaterialTheme.colorScheme.secondary, container = MaterialTheme.colorScheme.secondaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("الإصدار المثبت", fontWeight = FontWeight.Bold)
                        Text("CarManager $versionName • build ${versionCode ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            PremiumSurfaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PremiumIconBadge(
                        if (crash == null) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        tone = if (crash == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        container = if (crash == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(if (crash == null) "لا يوجد توقف مسجل" else "تم رصد توقف سابق", fontWeight = FontWeight.Bold)
                        Text(
                            crash?.let { AppCrashDiagnostics.summary(it) }
                                ?: "سيحفظ التطبيق آخر خطأ تقني محليًا إذا حدث توقف غير متوقع.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
                if (crash != null) {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { AppCrashDiagnostics.clear(context); crash = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("مسح سجل التوقف بعد المراجعة")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
            ) {
                Text(
                    "لا يتم إرسال سجل الأعطال تلقائيًا. إذا ظهر توقف جديد يمكنك فتح هذه الصفحة وتصوير الملخص أو مشاركة السجل معنا في جولة الدعم التالية.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("تم") }
            Spacer(Modifier.height(18.dp))
        }
    }
}
