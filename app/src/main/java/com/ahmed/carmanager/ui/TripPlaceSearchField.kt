package com.ahmed.carmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Search + recent places + current location entry point used for both route endpoints. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripPlaceSearchField(
    label: String,
    value: String,
    coordinate: LatLng?,
    nearBias: LatLng?,
    onRawValueChange: (String) -> Unit,
    onPlaceSelected: (TripPlaceSelection) -> Unit,
    onOpenMap: () -> Unit,
    allowCurrentLocation: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<TripPlaceSuggestion>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var sessionToken by remember { mutableStateOf(TripPlaceSearchEngine.newSessionToken()) }
    var selectingProgrammatically by remember { mutableStateOf(false) }

    fun loadCurrentLocation() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    ?: client.lastLocation.await()
                    ?: error("تعذر تحديد الموقع الحالي")
                val point = LatLng(location.latitude, location.longitude)
                val text = TripRoutePlanner.reverseGeocode(context, point) ?: "موقعي الحالي"
                TripPlaceSelection(text, point, "GPS")
            }.onSuccess {
                selectingProgrammatically = true
                TripPlaceHistoryStore.remember(context, it)
                onPlaceSelected(it)
                suggestions = emptyList()
                sessionToken = TripPlaceSearchEngine.newSessionToken()
            }.onFailure { error = it.message ?: "تعذر تحديد الموقع الحالي" }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadCurrentLocation() else error = "يلزم السماح بالموقع لاستخدام موقعي الحالي"
    }

    LaunchedEffect(value, focused, nearBias) {
        if (selectingProgrammatically) {
            selectingProgrammatically = false
            return@LaunchedEffect
        }
        if (!focused) return@LaunchedEffect
        val query = value.trim()
        if (query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        runCatching { TripPlaceSearchEngine.search(context, query, sessionToken, nearBias) }
            .onSuccess { suggestions = it }
            .onFailure { error = "تعذر جلب اقتراحات الأماكن. يمكنك الكتابة أو الاختيار من الخريطة." }
        loading = false
    }

    val recent = remember(focused, value) {
        if (focused && value.isBlank()) TripPlaceHistoryStore.recent(context).take(5) else emptyList()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onRawValueChange(it)
                error = null
            },
            label = { Text(label) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    if (value.isNotBlank()) {
                        IconButton(onClick = { onRawValueChange(""); suggestions = emptyList() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Clear, "مسح")
                        }
                    }
                }
            },
            supportingText = {
                when {
                    coordinate != null -> Text("تم تحديد الموقع بدقة — سيُستخدم لحساب الطريق")
                    value.isNotBlank() -> Text("اختر نتيجة من القائمة لضمان حساب المسافة تلقائيًا")
                    else -> Text("ابحث باسم المنطقة أو الشارع أو المكان")
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )

        if (focused && (suggestions.isNotEmpty() || recent.isNotEmpty())) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    if (recent.isNotEmpty()) {
                        Text("الأماكن الأخيرة", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), textAlign = TextAlign.End)
                        recent.forEach { place ->
                            PlaceRow(Icons.Default.History, place.label, "من السجل") {
                                selectingProgrammatically = true
                                TripPlaceHistoryStore.remember(context, place)
                                onPlaceSelected(place)
                                suggestions = emptyList()
                                sessionToken = TripPlaceSearchEngine.newSessionToken()
                                focused = false
                            }
                        }
                    }
                    suggestions.forEach { suggestion ->
                        PlaceRow(Icons.Default.LocationOn, suggestion.primaryText, suggestion.secondaryText.ifBlank { suggestion.provider }) {
                            scope.launch {
                                loading = true
                                error = null
                                val resolved = TripPlaceSearchEngine.resolve(context, suggestion, sessionToken)
                                if (resolved != null) {
                                    selectingProgrammatically = true
                                    TripPlaceHistoryStore.remember(context, resolved)
                                    onPlaceSelected(resolved)
                                    suggestions = emptyList()
                                    sessionToken = TripPlaceSearchEngine.newSessionToken()
                                    focused = false
                                } else error = "تعذر تحديد إحداثيات هذا المكان؛ اختره من الخريطة."
                                loading = false
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenMap, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("الخريطة")
            }
            if (allowCurrentLocation) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) loadCurrentLocation()
                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("موقعي الحالي")
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PlaceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}
