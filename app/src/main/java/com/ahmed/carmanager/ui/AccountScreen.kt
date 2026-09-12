@file:Suppress("DEPRECATION")

package com.ahmed.carmanager.ui

import androidx.compose.material.icons.automirrored.filled.Logout

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ahmed.carmanager.R
import com.ahmed.carmanager.data.auth.AccountUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private enum class AuthMode { SIGN_IN, CREATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    busy: Boolean,
    onSignInEmail: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onGoogleToken: (String) -> Unit,
    onResetPassword: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val googleOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, googleOptions) }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) onMessage("تعذر الحصول على رمز تسجيل الدخول من Google.") else onGoogleToken(token)
        } catch (_: Throwable) {
            onMessage("لم يكتمل تسجيل الدخول بحساب Google.")
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(108.dp).clip(RoundedCornerShape(31.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF081A21), Color(0xFF0D3640))))
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_premium),
                    contentDescription = "CarManager",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("CarManager", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Premium Automotive", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("ملف مركبتك الذكي للصيانة والتشخيص والوقود والرحلات والتكلفة في تجربة واحدة.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = mode == AuthMode.SIGN_IN, onClick = { mode = AuthMode.SIGN_IN }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("تسجيل الدخول") }
                        SegmentedButton(selected = mode == AuthMode.CREATE, onClick = { mode = AuthMode.CREATE }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("حساب جديد") }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trimStart() },
                        label = { Text("البريد الإلكتروني") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == AuthMode.SIGN_IN) {
                        TextButton(onClick = { onResetPassword(email) }, enabled = !busy, modifier = Modifier.align(Alignment.Start)) { Text("نسيت كلمة المرور؟") }
                    } else {
                        Text("استخدم 6 أحرف على الأقل. سنرسل رسالة تحقق إلى بريدك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || !email.contains('@')) onMessage("اكتب بريدًا إلكترونيًا صحيحًا.")
                            else if (password.length < 6) onMessage("كلمة المرور يجب أن تكون 6 أحرف على الأقل.")
                            else if (mode == AuthMode.SIGN_IN) onSignInEmail(email, password) else onCreateAccount(email, password)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text(if (mode == AuthMode.SIGN_IN) "دخول إلى سياراتي" else "إنشاء الحساب", fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f)); Text("  أو  ", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { googleClient.signOut().addOnCompleteListener { googleLauncher.launch(googleClient.signInIntent) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(10.dp)); Text("المتابعة باستخدام Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("بيانات كل حساب معزولة عن الحسابات الأخرى. التطبيق يعمل محليًا دون اتصال ويزامن النسخة السحابية عند توفر الإنترنت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AccountSheet(
    user: AccountUser,
    vehicleCount: Int,
    hasUnclaimedVehicles: Boolean,
    cloudBusy: Boolean,
    accountBusy: Boolean,
    onUpdateDisplayName: (String) -> Unit,
    onSendVerification: () -> Unit,
    onRefreshAccount: () -> Unit,
    onResetPassword: () -> Unit,
    onClaimLegacy: () -> Unit,
    onSyncCloud: () -> Unit,
    onRestoreCloud: () -> Unit,
    onOpenBackup: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmRestore by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
    ) {
        Box(Modifier.fillMaxWidth()) {
            PremiumGradientHeader(
                title = "الحساب والمزامنة",
                subtitle = "الملف الشخصي والأمان والنسخ السحابية في مكان واحد",
                icon = Icons.Rounded.AccountCircle
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(36.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = .10f)
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "إغلاق", Modifier.size(18.dp), tint = Color.White) }
            }
        }
        Spacer(Modifier.height(14.dp))

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
            Column(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF081A21), Color(0xFF10343D), Color(0xFF0A222A)))).padding(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!user.photoUrl.isNullOrBlank()) {
                        AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                    } else {
                        Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, modifier = Modifier.size(34.dp), tint = Color(0xFF6CE2E9)) }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(user.displayName ?: "حساب CarManager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                        Text(user.email.orEmpty(), color = Color.White.copy(alpha = .68f))
                        Spacer(Modifier.height(5.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(if (user.emailVerified) "البريد موثق" else "يحتاج توثيق") },
                            leadingIcon = { Icon(if (user.emailVerified) Icons.Default.Verified else Icons.Default.MarkEmailUnread, null, Modifier.size(16.dp)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { editName = true }, enabled = !accountBusy, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .10f), contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("تعديل الاسم الظاهر")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AutomotiveMetricCard("المركبات", vehicleCount.toString(), CMIcons.Garage, AutoTone.TEAL, Modifier.weight(1f))
            AutomotiveMetricCard("السحابة", if (cloudBusy) "جارٍ..." else "متصلة", CMIcons.Cloud, AutoTone.GREEN, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("الأمان والحساب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text("البريد الإلكتروني", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.email.orEmpty(), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (!user.emailVerified) {
                    Button(onClick = onSendVerification, enabled = !accountBusy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.MarkEmailRead, null); Spacer(Modifier.width(8.dp)); Text("إرسال رسالة التحقق")
                    }
                    TextButton(onClick = onRefreshAccount, enabled = !accountBusy, modifier = Modifier.fillMaxWidth()) { Text("حدّث حالة التحقق بعد فتح الرسالة") }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedButton(onClick = onResetPassword, enabled = !accountBusy && !user.email.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text("إرسال رابط تغيير كلمة المرور")
                }
                Text("تغيير البريد الإلكتروني نفسه يحتاج إعادة تحقق حديثة من الهوية، لذلك لا يتم تنفيذه تلقائيًا من هذه الشاشة حفاظًا على الحساب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("المزامنة السحابية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                }
                Text("يحفظ التطبيق أحدث نسخة كاملة خاصة بهذا الحساب، ويستعيدها على جهاز جديد عندما تكون بيانات الحساب المحلية فارغة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmRestore = true }, enabled = !cloudBusy, modifier = Modifier.weight(1f)) { Text("استعادة") }
                    Button(onClick = onSyncCloud, enabled = !cloudBusy, modifier = Modifier.weight(1f)) {
                        if (cloudBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(6.dp)); Text("مزامنة الآن")
                    }
                }
            }
        }

        if (hasUnclaimedVehicles) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بيانات من النسخة السابقة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Icon(Icons.Default.Security, null)
                    }
                    Text("وجدنا سيارات محفوظة قبل إضافة الحسابات. اربطها بهذا الحساب مرة واحدة لتظل خاصة بك.", modifier = Modifier.padding(vertical = 8.dp))
                    Button(onClick = onClaimLegacy, modifier = Modifier.fillMaxWidth()) { Text("ربط السيارات الحالية بحسابي") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("نسخة محلية قابلة للتصدير والاستعادة")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("تسجيل الخروج")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editName) {
        var name by remember(user.displayName) { mutableStateOf(user.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editName = false },
            icon = { Icon(Icons.Default.Badge, null) },
            title = { Text("تعديل الاسم الظاهر") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(enabled = name.isNotBlank() && !accountBusy, onClick = { onUpdateDisplayName(name); editName = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("إلغاء") } }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(Icons.Default.CloudDownload, null) },
            title = { Text("استعادة آخر نسخة سحابية؟") },
            text = { Text("سيتم استبدال بيانات هذا الحساب الموجودة على الهاتف بآخر نسخة محفوظة لهذا الحساب فقط. بيانات الحسابات الأخرى لن تتأثر.") },
            confirmButton = { Button(onClick = { confirmRestore = false; onRestoreCloud() }) { Text("استعادة") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun AccountStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
