package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Hướng Dẫn & Hạn Chế")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hướng dẫn cấp quyền
                Text("1. Hướng Dẫn Cấp Quyền Hoạt Động", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                GuideStep(
                    number = "1",
                    title = "Dịch vụ Hỗ trợ tiếp cận (Accessibility)",
                    desc = "Vào Cài đặt hệ thống -> Hỗ trợ tiếp cận (Accessibility) -> Ứng dụng đã cài đặt -> Tìm 'Smart Auto Clicker Service' và Gạt Bật."
                )

                GuideStep(
                    number = "2",
                    title = "Cửa sổ nổi (Overlay)",
                    desc = "Bật quyền 'Xuất hiện trên cùng' / 'Hiển thị trên ứng dụng khác' để bong bóng nổi Play/Stop và tâm ngắm có thể hiển thị trên mọi ứng dụng."
                )

                GuideStep(
                    number = "3",
                    title = "Chụp màn hình (MediaProjection)",
                    desc = "Nhấn 'Cấp quyền chụp' trên màn hình chính và chọn 'Bắt đầu ngay'. Hệ thống sẽ tạo dịch vụ Foreground để OCR quét chữ."
                )

                GuideStep(
                    number = "4",
                    title = "Tắt Tối ưu hóa pin (Battery Optimization)",
                    desc = "Trên Xiaomi (MIUI/HyperOS), Oppo, Vivo, Samsung, hãy vào Cài đặt ứng dụng -> Tiết kiệm pin -> Đặt thành 'Không hạn chế' và bật 'Tự khởi chạy' để app không bị tắt đột ngột."
                )

                Divider()

                // Hướng dẫn sử dụng nút nổi Play/Stop
                Text("2. Nút Điều Khiển Nổi Play/Stop", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = "• Bong bóng tròn Play/Stop: Chạm 1 lần để Chạy/Dừng tác vụ gần nhất tức thì (có rung phản hồi).\n" +
                            "• Đổi trạng thái trực quan: ▶ Viền xanh khi sẵn sàng, ⏹ Viền đỏ khi đang chạy, ⏸ Viền vàng khi tạm dừng.\n" +
                            "• Kéo & Tự hít viền: Kéo bong bóng tới bất kỳ đâu trên màn hình, thả ra sẽ tự động hít gọn gàng vào mép trái/phải và ghi nhớ vị trí.\n" +
                            "• Mở rộng thanh công cụ: Chạm giữ lâu vào bong bóng tròn để bung ra thanh công cụ (Tạm dừng, Chọn điểm, Chọn vùng OCR, Mở app, Đóng nút nổi).\n" +
                            "• Bộ đếm thông minh: Badge góc trên hiển thị số vòng lặp (x3) hoặc thời gian (01:25), chạm vào badge để chuyển đổi.\n" +
                            "• An toàn OCR & Auto-click: Bong bóng tự động ẩn tạm thời (alpha = 0) khi chụp màn hình và tự động chặn auto-click đè lên chính nó!",
                    style = MaterialTheme.typography.bodySmall
                )

                Divider()

                // Phím tắt khẩn cấp
                Text("3. Phím Tắt Khẩn Cấp (Failsafe)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "• Bấm phím Tăng/Giảm Âm lượng bất kỳ lúc nào để DỪNG NGAY LẬP TỨC toàn bộ macro và script.\n• Nhấn nút '⏹ Dừng' đỏ trên nút nổi, trên màn hình chính hoặc trên thanh thông báo hệ thống.",
                    style = MaterialTheme.typography.bodySmall
                )

                Divider()

                // Danh sách hạn chế đã biết
                Text("4. Danh Sách Hạn Chế Đã Biết", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)

                LimitationItem(
                    title = "Màn hình bảo mật FLAG_SECURE",
                    desc = "Các ứng dụng ngân hàng, ví điện tử (MoMo, ZaloPay), app truyền hình có bản quyền (Netflix) hoặc tab ẩn danh của trình duyệt sẽ làm màn hình đen khi chụp, do đó tính năng OCR không thể đọc chữ."
                )

                LimitationItem(
                    title = "Ứng dụng hoặc Game chặn Accessibility",
                    desc = "Một số game trực tuyến có hệ thống chống auto-click (Anti-cheat) có thể can thiệp bỏ qua các cử chỉ dispatchGesture hoặc chặn lấy tiêu điểm AccessibilityNodeInfo."
                )

                LimitationItem(
                    title = "Bàn phím ảo che khuất vùng chữ",
                    desc = "Khi ô nhập văn bản được bấm, bàn phím ảo đẩy nội dung lên có thể làm dịch chuyển tọa độ các nút cần bấm tiếp theo."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đã hiểu")
            }
        }
    )
}

@Composable
private fun GuideStep(number: String, title: String, desc: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LimitationItem(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
