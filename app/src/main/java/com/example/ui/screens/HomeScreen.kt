package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppRepository
import com.example.data.model.ScriptEntity
import com.example.engine.ExecutionManager
import com.example.engine.LogRepository
import com.example.service.FloatingControlService
import com.example.service.ScreenCaptureService
import com.example.service.SmartAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: AppRepository,
    onNavigateToScripts: () -> Unit,
    onOpenHelpGuide: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isServiceConnected by SmartAccessibilityService.isServiceConnected.collectAsStateWithLifecycle()
    val isCapturing by ScreenCaptureService.isCapturing.collectAsStateWithLifecycle()

    val activeScript1 by ExecutionManager.activeScript.collectAsStateWithLifecycle()
    val activeScript2 by ExecutionManager.activeScript2.collectAsStateWithLifecycle()
    val button1Label by ExecutionManager.button1Label.collectAsStateWithLifecycle()
    val button2Label by ExecutionManager.button2Label.collectAsStateWithLifecycle()

    val allScripts by repository.allScripts.collectAsStateWithLifecycle(emptyList())

    var showScriptPicker1 by remember { mutableStateOf(false) }
    var showScriptPicker2 by remember { mutableStateOf(false) }

    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var floatingActive1 by remember { mutableStateOf(FloatingControlService.isRunningButton1) }
    var floatingActive2 by remember { mutableStateOf(FloatingControlService.isRunningButton2) }

    // Launcher xin quyền MediaProjection chụp màn hình
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            LogRepository.info("Permissions", "Đã cấp quyền chụp màn hình MediaProjection.")
        } else {
            LogRepository.warn("Permissions", "Người dùng từ chối quyền chụp màn hình.")
        }
    }

    // Launcher xin quyền Notification (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ==================== KHỐI LIÊN KẾT 2 NÚT NỔI CHẠY SONG SONG ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Chế độ 2 Nút Nổi Song Song",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Bật cả 2 nút nổi cùng lúc, chạy 2 script độc lập song song",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = floatingActive1 && floatingActive2,
                        onCheckedChange = { checked ->
                            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                            if (checked) {
                                if (!hasOverlayPermission) {
                                    Toast.makeText(context, "Vui lòng cấp quyền Cửa sổ nổi", Toast.LENGTH_SHORT).show()
                                    openOverlaySettings(context)
                                } else {
                                    val intent = Intent(context, FloatingControlService::class.java).apply {
                                        action = FloatingControlService.ACTION_START_BOTH
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    floatingActive1 = true
                                    floatingActive2 = true
                                    Toast.makeText(context, "Đã bật 2 nút nổi chạy song song!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val intent = Intent(context, FloatingControlService::class.java).apply {
                                    action = FloatingControlService.ACTION_STOP_BOTH
                                }
                                context.startService(intent)
                                floatingActive1 = false
                                floatingActive2 = false
                            }
                        },
                        modifier = Modifier.testTag("toggle_both_overlay_switch")
                    )
                }
            }
        }

        // ==================== KHỐI NÚT ĐIỀU KHIỂN NỔI 1 ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header & Toggle Bật/Tắt Nút 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = button1Label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "Nút điều khiển nổi 1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = floatingActive1,
                        onCheckedChange = { checked ->
                            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                            if (checked) {
                                if (!hasOverlayPermission) {
                                    Toast.makeText(context, "Vui lòng cấp quyền Cửa sổ nổi", Toast.LENGTH_SHORT).show()
                                    openOverlaySettings(context)
                                } else {
                                    val intent = Intent(context, FloatingControlService::class.java).apply {
                                        action = FloatingControlService.ACTION_START_BUTTON_1
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    floatingActive1 = true
                                }
                            } else {
                                val intent = Intent(context, FloatingControlService::class.java).apply {
                                    action = FloatingControlService.ACTION_STOP_BUTTON_1
                                }
                                context.startService(intent)
                                floatingActive1 = false
                            }
                        },
                        modifier = Modifier.testTag("toggle_overlay_switch_1")
                    )
                }

                Divider()

                // Script gán cho Nút 1 & Nhãn hiển thị
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Script 1: ${activeScript1?.name ?: "Chưa chọn script nào"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeScript1 != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = activeScript1?.description?.ifEmpty { "Không có mô tả" } ?: "Chạm 'Đổi' để gán kịch bản cho Nút 1",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showScriptPicker1 = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đổi")
                    }
                }
            }
        }

        // ==================== KHỐI NÚT ĐIỀU KHIỂN NỔI 2 (MỚI) ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header & Toggle Bật/Tắt Nút 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = button2Label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Text(
                            text = "Nút điều khiển nổi 2",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = floatingActive2,
                        onCheckedChange = { checked ->
                            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                            if (checked) {
                                if (!hasOverlayPermission) {
                                    Toast.makeText(context, "Vui lòng cấp quyền Cửa sổ nổi", Toast.LENGTH_SHORT).show()
                                    openOverlaySettings(context)
                                } else {
                                    val intent = Intent(context, FloatingControlService::class.java).apply {
                                        action = FloatingControlService.ACTION_START_BUTTON_2
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    floatingActive2 = true
                                }
                            } else {
                                val intent = Intent(context, FloatingControlService::class.java).apply {
                                    action = FloatingControlService.ACTION_STOP_BUTTON_2
                                }
                                context.startService(intent)
                                floatingActive2 = false
                            }
                        },
                        modifier = Modifier.testTag("toggle_overlay_switch_2")
                    )
                }

                Divider()

                // Script gán cho Nút 2 & Nhãn hiển thị
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Script 2: ${activeScript2?.name ?: "Chưa chọn script nào"}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeScript2 != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = activeScript2?.description?.ifEmpty { "Không có mô tả" } ?: "Chạm 'Đổi' để gán kịch bản cho Nút 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showScriptPicker2 = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đổi")
                    }
                }
            }
        }

        // ==================== KHỐI YÊU CẦU CẤP QUYỀN ====================
        Text(
            text = "Yêu cầu cấp quyền",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Accessibility Service
        PermissionItem(
            title = "1. Dịch vụ Hỗ trợ (Accessibility)",
            description = "Bắt buộc để mô phỏng cử chỉ chạm, vuốt và dán văn bản",
            isGranted = isServiceConnected,
            onActionClick = { openAccessibilitySettings(context) },
            actionText = "Bật trong Cài đặt"
        )

        // 2. Quyền Cửa sổ nổi
        PermissionItem(
            title = "2. Cửa sổ nổi (Overlay)",
            description = "Bắt buộc để hiển thị các nút tròn Play/Stop trên ứng dụng khác",
            isGranted = hasOverlayPermission,
            onActionClick = {
                openOverlaySettings(context)
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            },
            actionText = "Cấp quyền nổi"
        )

        // 3. Quyền Chụp màn hình (MediaProjection)
        PermissionItem(
            title = "3. Chụp màn hình (MediaProjection)",
            description = "Bắt buộc cho OCR on-device nhận diện chữ và tìm vị trí nút",
            isGranted = isCapturing,
            onActionClick = {
                val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
            },
            actionText = "Cấp quyền chụp"
        )

        // 4. Quyền Thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem(
                title = "4. Thông báo Foreground Service",
                description = "Giữ dịch vụ không bị hệ thống tắt khi chạy trong nền",
                isGranted = hasNotificationPermission,
                onActionClick = {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                actionText = "Cho phép"
            )
        }

        // ==================== DIALOG CHỌN SCRIPT 1 ====================
        if (showScriptPicker1) {
            ScriptPickerDialog(
                title = "Chọn Script cho Nút Nổi 1",
                allScripts = allScripts,
                selectedScript = activeScript1,
                onSelect = { script ->
                    ExecutionManager.setActiveScript1(script, context)
                    showScriptPicker1 = false
                },
                onDismiss = { showScriptPicker1 = false }
            )
        }

        // ==================== DIALOG CHỌN SCRIPT 2 ====================
        if (showScriptPicker2) {
            ScriptPickerDialog(
                title = "Chọn Script cho Nút Nổi 2",
                allScripts = allScripts,
                selectedScript = activeScript2,
                onSelect = { script ->
                    ExecutionManager.setActiveScript2(script, context)
                    showScriptPicker2 = false
                },
                onDismiss = { showScriptPicker2 = false }
            )
        }
    }
}

@Composable
fun ScriptPickerDialog(
    title: String,
    allScripts: List<ScriptEntity>,
    selectedScript: ScriptEntity?,
    onSelect: (ScriptEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allScripts.isEmpty()) {
                    Text("Chưa có script nào trong thư viện.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    allScripts.forEach { script ->
                        val isSelected = selectedScript?.id == script.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(script) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = script.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (script.description.isNotEmpty()) {
                                        Text(
                                            text = script.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Đang chọn",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onActionClick: () -> Unit,
    actionText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF34C759) else Color(0xFFFF9500),
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isGranted) {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(actionText, fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "Đã cấp",
                    color = Color(0xFF34C759),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
