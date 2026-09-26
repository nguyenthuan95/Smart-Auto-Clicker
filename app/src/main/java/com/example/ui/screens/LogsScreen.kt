package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LogLevel
import com.example.data.model.LogSession
import com.example.engine.LogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val logs by LogRepository.logs.collectAsStateWithLifecycle()
    val sessions by LogRepository.sessions.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val sessionTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var filterQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedSessionId by remember { mutableStateOf<Long?>(null) }

    val filteredLogs = remember(logs, filterQuery, selectedLevel, selectedSessionId) {
        logs.filter { entry ->
            val matchSession = (selectedSessionId == null || entry.sessionId == selectedSessionId)
            val matchLevel = (selectedLevel == null || entry.level == selectedLevel)
            val matchQuery = (filterQuery.isEmpty() || entry.message.contains(filterQuery, ignoreCase = true) || entry.tag.contains(filterQuery, ignoreCase = true))
            matchSession && matchLevel && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thanh tìm kiếm và nút hành động
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Tìm kiếm log...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("log_search_field")
            )

            // Nút Xuất Log
            IconButton(
                onClick = {
                    val logText = LogRepository.exportToString(selectedSessionId)
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, logText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Xuất Nhật Ký Chạy")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.testTag("export_logs_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Chia sẻ log")
            }

            // Nút Xóa Log
            IconButton(
                onClick = {
                    LogRepository.clear()
                    selectedSessionId = null
                },
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa toàn bộ log", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Thanh Lọc theo Phiên chạy (Session Filter)
        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phiên chạy:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FilterChip(
                    selected = selectedSessionId == null,
                    onClick = { selectedSessionId = null },
                    label = { Text("Tất cả phiên (${sessions.size})") }
                )
                sessions.forEach { sess ->
                    val isSelected = selectedSessionId == sess.id
                    val timeStr = sessionTimeFormat.format(Date(sess.startTimeMs))
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSessionId = if (isSelected) null else sess.id },
                        label = { Text("▶ [$timeStr] ${sess.name}") }
                    )
                }
            }
        }

        // Danh sách logs
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logs.isEmpty()) "Chưa có bản ghi nhật ký nào." else "Không có log khớp với bộ lọc.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (entry.level) {
                                    LogLevel.INFO -> Color(0xFF1976D2)
                                    LogLevel.ACTION -> Color(0xFF388E3C)
                                    LogLevel.OCR -> Color(0xFF7B1FA2)
                                    LogLevel.WARN -> Color(0xFFF57C00)
                                    LogLevel.ERROR -> Color(0xFFD32F2F)
                                }
                            ) {
                                Text(
                                    text = entry.level.name,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }

                            Text(
                                text = "[${entry.tag}] ${entry.message}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
