package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.ReceiptLong

import androidx.compose.material.icons.automirrored.filled.FactCheck

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.diagnostics.DiagnosticSessionCodec
import com.ahmed.carmanager.data.local.model.DocumentType
import com.ahmed.carmanager.data.local.model.VehicleDocumentEntity
import com.ahmed.carmanager.data.local.model.VehicleEntity
import com.ahmed.carmanager.data.repository.DocumentInput
import java.util.concurrent.TimeUnit

@Composable
fun ProfessionalDocumentsScreen(
    vehicle: VehicleEntity?,
    documents: List<VehicleDocumentEntity>,
    onAdd: (DocumentInput) -> Unit,
    onMessage: (String) -> Unit
) {
    if (vehicle == null) {
        EmptyState("لا توجد مركبة محددة", "اختر مركبة أولًا لفتح خزنة المستندات.", Icons.Default.FolderCopy)
        return
    }

    var showAdd by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val activeDocs = documents.filter { !it.isDeleted && !DiagnosticSessionCodec.isSessionDocument(it) }
    val expired = activeDocs.count { it.expiryDate?.let { date -> date < now } == true }
    val soon = activeDocs.count { it.expiryDate?.let { date -> date in now..(now + 30L * 86_400_000L) } == true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 104.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            PremiumGradientHeader(
                title = "خزنة المستندات",
                subtitle = "${vehicle.displayName ?: vehicle.model} • رخص وفواتير وملفات مهمة",
                icon = CMIcons.Document
            )
        }
        item {
            AutomotiveActionCard(
                title = "إضافة مستند",
                subtitle = "رخصة أو تأمين أو فاتورة أو PDF",
                icon = CMIcons.Document,
                tone = AutoTone.BLUE,
                modifier = Modifier.fillMaxWidth(),
                filled = true,
                onClick = { showAdd = true }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomotiveMetricCard("منتهي", expired.toString(), Icons.Default.EventBusy, if (expired > 0) AutoTone.RED else AutoTone.GRAPHITE, Modifier.weight(1f))
                AutomotiveMetricCard("خلال 30 يوم", soon.toString(), Icons.Default.Event, AutoTone.AMBER, Modifier.weight(1f))
                AutomotiveMetricCard("المحفوظ", activeDocs.size.toString(), CMIcons.Document, AutoTone.BLUE, Modifier.weight(1f))
            }
        }

        if (activeDocs.isEmpty()) {
            item { EmptyState("الخزنة فارغة", "أضف الترخيص أو التأمين أو الفحص أو أي فاتورة/مستند مهم مع صورة أو PDF اختياري.", Icons.Default.Description) }
        } else {
            item { SectionHeader("المستندات", "مرتبة حسب أقرب تاريخ انتهاء") }
            items(activeDocs.sortedWith(compareBy<VehicleDocumentEntity> { it.expiryDate ?: Long.MAX_VALUE }.thenByDescending { it.createdAt }), key = { it.id }) { doc ->
                DocumentVaultCard(doc, now, onMessage)
            }
        }
    }

    if (showAdd) {
        AddDocumentSheet(
            onDismiss = { showAdd = false },
            onSave = {
                onAdd(it)
                showAdd = false
            },
            onMessage = onMessage
        )
    }
}

@Composable
private fun VaultMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentVaultCard(doc: VehicleDocumentEntity, now: Long, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val days = doc.expiryDate?.let { TimeUnit.MILLISECONDS.toDays(it - now) }
    val level = when {
        days == null -> 0
        days < 0 -> 2
        days <= 30 -> 1
        else -> 0
    }
    val container = when (level) {
        2 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .55f)
        1 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .65f)
        else -> MaterialTheme.colorScheme.surface
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.elevatedCardColors(containerColor = container)) {
        Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!doc.fileUri.isNullOrBlank()) {
                    IconButton(onClick = {
                        runCatching {
                            val uri = android.net.Uri.parse(doc.fileUri)
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }.onFailure { onMessage("تعذر فتح الملف على هذا الجهاز.") }
                    }) { Icon(Icons.Default.Attachment, "فتح المرفق", tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(doc.documentType.arLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    doc.documentNumber?.takeIf { it.isNotBlank() }?.let { Text("رقم $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(documentIcon(doc.documentType), null, Modifier.padding(7.dp).size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (doc.fileUri.isNullOrBlank()) "بدون ملف" else "مرفق محفوظ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        days == null -> "بدون تاريخ انتهاء"
                        days < 0 -> "منتهي منذ ${-days} يوم"
                        days == 0L -> "ينتهي اليوم"
                        else -> "متبقي $days يوم"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (level == 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            doc.expiryDate?.let { Text("الانتهاء: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            doc.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDocumentSheet(onDismiss: () -> Unit, onSave: (DocumentInput) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(DocumentType.VEHICLE_LICENSE) }
    var number by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf<Long?>(null) }
    var expiryDate by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fileUri = uri.toString()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("إضافة مستند", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            EnumSelector("نوع المستند", DocumentType.entries, type, { type = it }) { it.arLabel() }
            AppField(number, { number = it }, "رقم المستند - اختياري")
            AppDateSelector("تاريخ الإصدار - اختياري", issueDate, allowClear = true) { issueDate = it }
            AppDateSelector("تاريخ الانتهاء - اختياري", expiryDate, allowClear = true) { expiryDate = it }
            AppField(notes, { notes = it }, "ملاحظات - اختياري")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (fileUri == null) Icons.Default.AttachFile else Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(7.dp))
                Text(if (fileUri == null) "إرفاق صورة أو PDF" else "تم اختيار ملف — تغيير")
            }

            if (fileUri != null) {
                Text("سيحتفظ التطبيق بإذن قراءة الملف على هذا الجهاز. عند نقل البيانات لجهاز آخر قد تحتاج لإرفاق الملف مجددًا ما لم تتم مزامنته سحابيًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }

            Button(
                onClick = {
                    if (expiryDate != null && issueDate != null && expiryDate!! < issueDate!!) {
                        onMessage("تاريخ الانتهاء يجب أن يكون بعد تاريخ الإصدار.")
                    } else {
                        onSave(DocumentInput(type, number, issueDate, expiryDate, fileUri, notes))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("حفظ المستند")
            }
        }
    }
}

private fun documentIcon(type: DocumentType) = when (type) {
    DocumentType.VEHICLE_LICENSE -> Icons.Default.Badge
    DocumentType.INSURANCE -> Icons.Default.VerifiedUser
    DocumentType.INSPECTION -> Icons.AutoMirrored.Filled.FactCheck
    DocumentType.CONTRACT -> Icons.Default.Handshake
    DocumentType.RECEIPT -> Icons.AutoMirrored.Filled.ReceiptLong
    DocumentType.OTHER -> Icons.Default.Description
}
