package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.BackupData
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SmartClickerApp
        val repository = app.repository

        setContent {
            MyApplicationTheme {
                MainAppScreen(repository = repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(repository: com.example.data.database.AppRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(0) }
    var showHelpGuide by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Smart Auto Clicker",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.testTag("backup_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sao lưu & Khôi phục")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Trang chủ") },
                    label = { Text("Trang chủ") },
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Code, contentDescription = "Script") },
                    label = { Text("Script") },
                    modifier = Modifier.testTag("nav_scripts")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Article, contentDescription = "Nhật ký") },
                    label = { Text("Nhật ký") },
                    modifier = Modifier.testTag("nav_logs")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeScreen(
                    repository = repository,
                    onNavigateToScripts = { currentTab = 1 },
                    onOpenHelpGuide = { showHelpGuide = true }
                )
                1 -> ScriptEditorScreen(repository = repository)
                2 -> LogsScreen()
            }
        }
    }

    // Dialog Hướng dẫn & Hạn chế
    if (showHelpGuide) {
        HelpGuideDialog(onDismiss = { showHelpGuide = false })
    }

    // Dialog Sao lưu & Khôi phục JSON
    if (showBackupDialog) {
        var importJsonText by remember { mutableStateOf("") }
        var isImportMode by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(if (!isImportMode) "Sao Lưu Dữ Liệu JSON" else "Khôi Phục Từ JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isImportMode) {
                        Text(
                            text = "Xuất toàn bộ Tọa độ, Vùng OCR, Chuỗi Macro và Scripts đã lưu thành file JSON để sao lưu hoặc chia sẻ sang thiết bị khác.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val points = repository.allPoints.first()
                                    val rois = repository.allRois.first()
                                    val macros = repository.allMacros.first()
                                    val scripts = repository.allScripts.first()

                                    val backup = BackupData(
                                        points = points,
                                        rois = rois,
                                        macros = macros,
                                        scripts = scripts
                                    )
                                    val json = repository.exportToJson(backup)

                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("SmartClickerBackup", json))

                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        type = "application/json"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Xuất cấu hình JSON"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Xuất & Chia sẻ file JSON")
                        }

                        OutlinedButton(
                            onClick = { isImportMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Nhập cấu hình từ JSON")
                        }
                    } else {
                        Text("Dán chuỗi JSON đã xuất vào ô bên dưới:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = { importJsonText = it },
                            placeholder = { Text("{\"points\": [...], \"macros\": [...]}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (isImportMode) {
                    Button(
                        onClick = {
                            if (importJsonText.isNotBlank()) {
                                coroutineScope.launch {
                                    val backup = repository.importFromJson(importJsonText)
                                    if (backup != null) {
                                        backup.points.forEach { repository.savePoint(it.copy(id = 0)) }
                                        backup.rois.forEach { repository.saveRoi(it.copy(id = 0)) }
                                        backup.macros.forEach { repository.saveMacro(it.copy(id = 0)) }
                                        backup.scripts.forEach { repository.saveScript(it.copy(id = 0)) }
                                        Toast.makeText(context, "Khôi phục dữ liệu thành công!", Toast.LENGTH_SHORT).show()
                                        showBackupDialog = false
                                    } else {
                                        Toast.makeText(context, "Dữ liệu JSON không hợp lệ!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Nhập Dữ Liệu")
                    }
                } else {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("Đóng")
                    }
                }
            },
            dismissButton = {
                if (isImportMode) {
                    TextButton(onClick = { isImportMode = false }) {
                        Text("Quay lại")
                    }
                }
            }
        )
    }
}
