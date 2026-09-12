package com.ahmed.carmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.diagnostics.DiagnosticSessionCodec
import com.ahmed.carmanager.data.diagnostics.DiagnosticSessionComparison
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity

@Composable
internal fun DiagnosticHealthCard(
    documents: List<VehicleDocumentEntity>,
    onOpenDiagnostics: () -> Unit
) {
    val sessions = remember(documents) { DiagnosticSessionCodec.sessions(documents) }
    val latest = sessions.firstOrNull()
    val comparison = remember(sessions) {
        if (sessions.size >= 2) DiagnosticSessionCodec.compare(sessions[0], sessions[1]) else null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onOpenDiagnostics,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Default.Memory, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (latest == null) "رفع تقرير" else "فتح")
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("التشخيص الإلكتروني • ThinkDiag", fontWeight = FontWeight.Bold)
                    if (latest == null) {
                        Text(
                            "لا توجد جلسة تشخيص محفوظة بعد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "${formatDate(latest.capturedAt)} • أكواد ${latest.codeCount} • مهم ${latest.importantCount} • حرج ${latest.criticalCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (latest.criticalCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            comparison?.let {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                DiagnosticHealthComparison(it)
            }

            if (latest != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "تأثير أكواد ThinkDiag على درجة الصحة يتم عبر الأعطال المرتبطة، لتجنب احتساب نفس المشكلة مرتين.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun DiagnosticHealthComparison(comparison: DiagnosticSessionComparison) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DiagnosticCountChip("اختفى", comparison.disappearedCodes.size, Modifier.weight(1f))
        DiagnosticCountChip("مستمر", comparison.persistentCodes.size, Modifier.weight(1f))
        DiagnosticCountChip("جديد", comparison.newCodes.size, Modifier.weight(1f))
    }
}

@Composable
private fun DiagnosticCountChip(title: String, count: Int, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}
