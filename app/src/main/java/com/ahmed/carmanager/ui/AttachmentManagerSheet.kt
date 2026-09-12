package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.OpenInNew

import androidx.compose.material.icons.automirrored.filled.InsertDriveFile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ahmed.carmanager.CarManagerApplication
import com.ahmed.carmanager.data.local.model.AttachmentEntity
import com.ahmed.carmanager.data.local.model.AttachmentType
import com.ahmed.carmanager.data.local.model.EntityType
import com.ahmed.carmanager.data.repository.VehicleRepositoryResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentManagerSheet(
    vehicleId: String,
    entityType: EntityType,
    entityId: String,
    title: String,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as CarManagerApplication).container }
    val repository = container.attachmentRepository
    val attachments by repository.observe(vehicleId, entityType, entityId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingUri = uri
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("المرفقات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                }
            }

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
            ) {
                Text(
                    "احتفظ بصورة الفاتورة أو PDF أو أي مستند مرتبط بهذه الحركة. الملف الأصلي يبقى على الجهاز، بينما يسجل CarManager ارتباطه بالسجل.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf", "text/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (pendingUri == null) Icons.Default.AddPhotoAlternate else Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(7.dp))
                Text(if (pendingUri == null) "اختيار صورة / PDF / مستند" else "تم اختيار ملف — تغيير")
            }

            if (pendingUri != null) {
                AppField(caption, { caption = it }, "وصف المرفق - اختياري")
                Button(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = {
                        val uri = pendingUri ?: return@Button
                        saving = true
                        scope.launch {
                            val mime = context.contentResolver.getType(uri).orEmpty()
                            val type = when {
                                mime.startsWith("image/") -> AttachmentType.IMAGE
                                mime == "application/pdf" -> AttachmentType.PDF
                                mime.isNotBlank() -> AttachmentType.DOCUMENT
                                else -> AttachmentType.OTHER
                            }
                            when (repository.add(vehicleId, entityType, entityId, type, uri.toString(), caption)) {
                                is VehicleRepositoryResult.Success -> {
                                    pendingUri = null
                                    caption = ""
                                    onMessage("تم حفظ المرفق وربطه بالسجل.")
                                    runCatching { container.cloudBackupManager.uploadLatest() }
                                }
                                is VehicleRepositoryResult.Error -> onMessage("تعذر حفظ المرفق.")
                            }
                            saving = false
                        }
                    }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(7.dp))
                    Text("حفظ المرفق")
                }
            }

            Text("الملفات المرتبطة (${attachments.count { !it.isDeleted }})", fontWeight = FontWeight.Bold)
            if (attachments.none { !it.isDeleted }) {
                Text("لا توجد مرفقات لهذا السجل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments.filter { !it.isDeleted }, key = { it.id }) { item ->
                        AttachmentRow(item = item, onMessage = onMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(item: AttachmentEntity, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    ElevatedCard(
        onClick = {
            runCatching {
                val uri = Uri.parse(item.fileUri)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { onMessage("تعذر فتح المرفق على هذا الجهاز.") }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Column(Modifier.weight(5f), horizontalAlignment = Alignment.End) {
                Text(item.captionAr ?: attachmentLabel(item.attachmentType), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("${attachmentLabel(item.attachmentType)} • ${formatDate(item.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Icon(attachmentIcon(item.attachmentType), null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun attachmentLabel(type: AttachmentType): String = when (type) {
    AttachmentType.IMAGE -> "صورة"
    AttachmentType.PDF -> "PDF"
    AttachmentType.DOCUMENT -> "مستند"
    AttachmentType.OTHER -> "ملف"
}

private fun attachmentIcon(type: AttachmentType) = when (type) {
    AttachmentType.IMAGE -> Icons.Default.Image
    AttachmentType.PDF -> Icons.Default.PictureAsPdf
    AttachmentType.DOCUMENT -> Icons.Default.Description
    AttachmentType.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}
