package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmed.carmanager.data.local.model.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

private val numberFormat = DecimalFormat("#,##0.##")
private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("ar", "EG"))

/*
 * CarManager UI System
 *
 * الهدف:
 * - واجهة Compact
 * - مساحات أقل
 * - حواف حديثة وليست ضخمة
 * - توحيد شكل التفاصيل والكروت
 * - الحفاظ على التوافق مع الشاشات الحالية
 */

object CMUi {
    val ScreenPadding = 16.dp

    val SmallSpacing = 4.dp
    val MediumSpacing = 8.dp
    val LargeSpacing = 12.dp
    val SectionSpacing = 16.dp

    val SmallRadius = 10.dp
    val CardRadius = 18.dp
    val LargeRadius = 24.dp

    val SmallIcon = 18.dp
    val StandardIcon = 21.dp
    val LargeIcon = 24.dp
}

fun formatKm(value: Double?): String =
    value?.let { numberFormat.format(it) } ?: "—"

fun formatMoney(value: Double?): String =
    value?.let { "${numberFormat.format(it)} ج.م" } ?: "—"

fun formatLiters(value: Double?): String =
    value?.let { "${numberFormat.format(it)} لتر" } ?: "—"

fun formatDate(value: Long?): String =
    value?.let { dateFormat.format(Date(it)) } ?: "—"

fun VehicleType.arLabel(custom: String? = null) = when (this) {
    VehicleType.CAR -> "سيارة"
    VehicleType.MOTORCYCLE -> "موتوسيكل"
    VehicleType.OTHER ->
        custom?.trim()?.takeIf { it.isNotEmpty() } ?: "مركبة أخرى"
}

fun FuelType.arLabel() = when (this) {
    FuelType.GASOLINE_80 -> "بنزين 80"
    FuelType.GASOLINE_92 -> "بنزين 92"
    FuelType.GASOLINE_95 -> "بنزين 95"
    FuelType.DIESEL -> "سولار"
    FuelType.ELECTRIC -> "كهرباء"
    FuelType.HYBRID -> "هجين"
    FuelType.OTHER -> "أخرى"
}

fun TransmissionType.arLabel() = when (this) {
    TransmissionType.MANUAL -> "يدوي"
    TransmissionType.AUTOMATIC -> "أوتوماتيك"
    TransmissionType.CVT -> "CVT"
    TransmissionType.DCT -> "DCT"
    TransmissionType.OTHER -> "أخرى"
}

fun TripType.arLabel() = when (this) {
    TripType.PERSONAL -> "شخصي"
    TripType.WORK -> "عمل"
    TripType.TRAVEL -> "سفر"
    TripType.SERVICE -> "صيانة"
    TripType.OTHER -> "أخرى"
}

fun ExpenseCategory.arLabel() = when (this) {
    ExpenseCategory.LICENSE -> "ترخيص"
    ExpenseCategory.INSURANCE -> "تأمين"
    ExpenseCategory.WASH -> "غسيل"
    ExpenseCategory.PARKING -> "ركن"
    ExpenseCategory.TOLL -> "رسوم طريق"
    ExpenseCategory.FINE -> "مخالفة"
    ExpenseCategory.ACCESSORY -> "إكسسوارات"
    ExpenseCategory.TOWING -> "ونش"
    ExpenseCategory.INSPECTION -> "فحص"
    ExpenseCategory.REPAIR -> "إصلاح"
    ExpenseCategory.OTHER -> "أخرى"
}

fun GpsProvider.arLabel() = when (this) {
    GpsProvider.ITRACK -> "iTrack"
    GpsProvider.ETRACK -> "eTrack"
    GpsProvider.PHONE -> "الهاتف"
    GpsProvider.HEAD_UNIT -> "شاشة السيارة"
    GpsProvider.GENERIC -> "جهاز آخر"
    GpsProvider.NONE -> "بدون GPS"
}

fun TirePosition.arLabel() = when (this) {
    TirePosition.FRONT_LEFT -> "أمامي يسار"
    TirePosition.FRONT_RIGHT -> "أمامي يمين"
    TirePosition.REAR_LEFT -> "خلفي يسار"
    TirePosition.REAR_RIGHT -> "خلفي يمين"
    TirePosition.SPARE -> "احتياطي"
    TirePosition.UNASSIGNED -> "غير محدد"
}

fun FaultSeverity.arLabel() = when (this) {
    FaultSeverity.LOW -> "بسيط"
    FaultSeverity.MEDIUM -> "متوسط"
    FaultSeverity.HIGH -> "مرتفع"
    FaultSeverity.CRITICAL -> "حرج"
}

fun DocumentType.arLabel() = when (this) {
    DocumentType.VEHICLE_LICENSE -> "رخصة المركبة"
    DocumentType.INSURANCE -> "تأمين"
    DocumentType.INSPECTION -> "فحص"
    DocumentType.CONTRACT -> "عقد"
    DocumentType.RECEIPT -> "إيصال"
    DocumentType.OTHER -> "أخرى"
}

/* ---------------------------------------------------------
 * Section header
 * --------------------------------------------------------- */

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End
            )

            if (!subtitle.isNullOrBlank()) {

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }

        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

/* ---------------------------------------------------------
 * Empty state
 * --------------------------------------------------------- */

@Composable
fun EmptyState(
    title: String,
    text: String,
    icon: ImageVector = Icons.Default.Info
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 22.dp,
                horizontal = 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 0.dp
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(11.dp)
                    .size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------------------------------------------------------
 * Metric card
 * Existing component - redesigned without breaking callers
 * --------------------------------------------------------- */

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {

    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(CMUi.CardRadius),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.End
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(CMUi.SmallIcon),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ---------------------------------------------------------
 * New CarManager shared components
 * --------------------------------------------------------- */

@Composable
fun CMSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CMUi.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {

        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

@Composable
fun CMCompactCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    if (onClick != null) {

        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(CMUi.CardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {

            Column(
                modifier = Modifier.padding(10.dp),
                content = content
            )
        }

    } else {

        Card(
            modifier = modifier,
            shape = RoundedCornerShape(CMUi.CardRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {

            Column(
                modifier = Modifier.padding(10.dp),
                content = content
            )
        }
    }
}

@Composable
fun CMStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {

        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 4.dp
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
fun CMInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (icon != null) {

            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(17.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun CMActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CMUi.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {

        Column(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 9.dp
            ),
            horizontalAlignment = Alignment.End
        ) {

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(7.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(7.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CMDetailHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    status: String? = null,
    onBack: (() -> Unit)? = null
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (onBack != null) {

            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {

                if (!status.isNullOrBlank()) {

                    CMStatusChip(status)

                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (icon != null) {

                    Spacer(Modifier.width(8.dp))

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(CMUi.LargeIcon),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!subtitle.isNullOrBlank()) {

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CMPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 9.dp
        )
    ) {

        if (icon != null) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(7.dp))
        }

        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ---------------------------------------------------------
 * Date selector
 * Existing component - compact redesign
 * --------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateSelector(
    label: String,
    value: Long?,
    allowClear: Boolean = false,
    onSelected: (Long?) -> Unit
) {

    var showPicker by remember {
        mutableStateOf(false)
    }

    ElevatedCard(
        onClick = {
            showPicker = true
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CMUi.CardRadius),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (allowClear && value != null) {

                IconButton(
                    onClick = {
                        onSelected(null)
                    },
                    modifier = Modifier.size(36.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "مسح التاريخ",
                        modifier = Modifier.size(18.dp)
                    )
                }

            } else {

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {

                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End
            ) {

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(1.dp))

                Text(
                    text = value?.let(::formatDate) ?: "غير محدد",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showPicker) {

        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis =
                value ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = {
                showPicker = false
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        onSelected(
                            pickerState.selectedDateMillis
                        )

                        showPicker = false
                    }
                ) {
                    Text("اختيار")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        showPicker = false
                    }
                ) {
                    Text("إلغاء")
                }
            }
        ) {

            DatePicker(
                state = pickerState
            )
        }
    }
}
