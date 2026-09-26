package com.example.engine

import com.example.data.model.ScriptEntity

object SampleScripts {

    val script1SkipButton = ScriptEntity(
        name = "1. Tự động bấm 'Skip / Bỏ qua'",
        description = "Liên tục quét toàn màn hình hoặc vùng chọn, khi thấy chữ 'Skip', 'Bỏ qua', 'Đóng' hoặc 'X' thì tự click vào tâm nút.",
        isPreset = true,
        code = """
// Script: Tự động bấm Skip / Bỏ qua quảng cáo hoặc màn hình chờ
log("Bắt đầu giám sát nút Skip / Bỏ qua...");
toast("Đang tìm nút Bỏ qua...");

var loopCount = 0;
var maxLoops = 100; // Giới hạn kiểm tra tránh lặp vô tận

while (loopCount < maxLoops) {
    // Tìm các từ khóa phổ biến: 'Bỏ qua', 'Skip', 'Đóng', 'Close'
    var btn = findText("Bỏ qua") || findText("Skip") || findText("Đóng") || findText("Close");
    
    if (btn) {
        log("=> Tìm thấy nút: '" + btn.text + "' tại (" + btn.centerX + ", " + btn.centerY + ")");
        // Thêm độ lệch ngẫu nhiên nhỏ và bấm
        click(btn.centerX, btn.centerY);
        toast("Đã bấm " + btn.text);
        sleep(1000);
    } else {
        log("Chưa thấy nút Skip, đang quét lại (lần " + (loopCount + 1) + ")");
    }
    
    loopCount++;
    sleep(1500); // Nghỉ 1.5 giây giữa các lần quét
}

log("Hoàn thành chu kỳ giám sát.");
""".trimIndent()
    )

    val script2AutoPasteAndSend = ScriptEntity(
        name = "2. Tự dán văn bản và bấm 'Gửi'",
        description = "Đặt nội dung clipboard, chạm vào ô nhập tin nhắn để focus, dán nội dung rồi tự tìm nút 'Gửi' / 'Send' để bấm.",
        isPreset = true,
        code = """
// Script: Tự động dán văn bản vào ô chat và bấm Gửi
log("Bắt đầu kịch bản Dán & Gửi tin nhắn...");

// 1. Chuẩn bị nội dung cần dán
var message = "Xin chào! Đây là tin nhắn gửi tự động từ Smart Auto Clicker. (" + new Date().toLocaleTimeString() + ")";
setClipboard(message);
log("Đã cập nhật clipboard: " + message);

// 2. Tìm ô nhập văn bản (thường chứa 'Nhập', 'Nhắn tin', 'Type', 'Message')
var inputField = findText("Nhập") || findText("Nhắn tin") || findText("Type") || findText("Message");

if (inputField) {
    log("Tìm thấy ô nhập tại: (" + inputField.centerX + ", " + inputField.centerY + ")");
    // Chạm vào ô nhập và thực hiện dán
    pasteClipboard(inputField.centerX, inputField.centerY);
    sleep(800);
} else {
    // Nếu không tìm thấy bằng OCR, dán tại vị trí ước lượng đáy màn hình
    var size = screenSize();
    var defaultX = size.width / 2;
    var defaultY = size.height - 150;
    log("Không thấy ô nhập qua OCR, thử dán tại vị trí mặc định (" + defaultX + ", " + defaultY + ")");
    pasteClipboard(defaultX, defaultY);
    sleep(800);
}

// 3. Tìm nút 'Gửi' hoặc 'Send'
var sendBtn = findText("Gửi") || findText("Send");
if (sendBtn) {
    log("Tìm thấy nút Gửi tại (" + sendBtn.centerX + ", " + sendBtn.centerY + ")");
    click(sendBtn.centerX, sendBtn.centerY);
    toast("Đã gửi tin nhắn thành công!");
} else {
    log("Không thấy nút Gửi, bạn có thể bổ sung tọa độ cố định nút gửi.");
}
""".trimIndent()
    )

    val script3LoopWithExitCondition = ScriptEntity(
        name = "3. Vòng lặp có điều kiện thoát",
        description = "Thực hiện chuỗi hành động lặp (vuốt màn hình, kiểm tra OCR), tự động dừng khi thỏa mãn điều kiện hoặc hết số lần tối đa.",
        isPreset = true,
        code = """
// Script: Lặp cuộn tin tức / trang và dừng khi gặp từ khóa mục tiêu
log("Bắt đầu vòng lặp tìm kiếm nội dung quan tâm...");

var targetWord = "Khuyến mãi";
var size = screenSize();
var startX = size.width / 2;
var startY = size.height * 0.75;
var endY = size.height * 0.25;

var maxScrolls = 20;
var found = false;

for (var i = 1; i <= maxScrolls; i++) {
    log("--- Lần duyệt thứ " + i + "/" + maxScrolls + " ---");
    
    // Kiểm tra xem từ khóa mục tiêu đã xuất hiện trên màn hình chưa
    var result = findText(targetWord);
    if (result) {
        log("🎉 ĐÃ TÌM THẤY: '" + result.text + "' tại tọa độ (" + result.centerX + ", " + result.centerY + ")");
        toast("Tìm thấy mục tiêu! Đang mở...");
        click(result.centerX, result.centerY);
        found = true;
        break; // Thoát vòng lặp
    }
    
    // Chưa thấy, thực hiện vuốt cuộn màn hình lên
    log("Chưa thấy từ khóa '" + targetWord + "', đang cuộn màn hình...");
    swipe(startX, startY, startX, endY, 450);
    sleep(1200); // Đợi màn hình ổn định sau khi cuộn
}

if (!found) {
    log("Đã đạt giới hạn " + maxScrolls + " lần cuộn mà không thấy '" + targetWord + "'. Dừng lại.");
    toast("Không tìm thấy nội dung mục tiêu.");
}
""".trimIndent()
    )

    val script4UnexpectedPopup = ScriptEntity(
        name = "4. Xử lý popup bất ngờ",
        description = "Hàm tiện ích quét và tự động đóng các hộp thoại, bảng thông báo, quyền hoặc cảnh báo bất ngờ chen ngang.",
        isPreset = true,
        code = """
// Script: Tự động nhận diện và đóng popup / thông báo bất ngờ
log("Bắt đầu kiểm tra và dọn dẹp popup bất ngờ...");

// Danh sách các từ khóa nút đóng popup phổ biến
var closeKeywords = ["Tôi đã hiểu", "Đồng ý", "Bỏ qua", "Đóng", "Không cảm ơn", "Hủy", "OK", "Dismiss"];

function checkAndDismissPopup() {
    for (var i = 0; i < closeKeywords.length; i++) {
        var keyword = closeKeywords[i];
        var btn = findText(keyword);
        if (btn) {
            log("Phát hiện popup có nút: '" + keyword + "' tại (" + btn.centerX + ", " + btn.centerY + ")");
            click(btn.centerX, btn.centerY);
            toast("Đã tắt popup: " + keyword);
            sleep(1000);
            return true;
        }
    }
    return false;
}

// Vòng lặp mô phỏng: thực hiện công việc chính xen kẽ kiểm tra popup
for (var step = 1; step <= 5; step++) {
    log("Thực hiện tác vụ chính bước " + step + "...");
    
    // Kiểm tra xem có popup che khuất không
    if (checkAndDismissPopup()) {
        log("Đã giải quyết popup, tiếp tục tác vụ...");
    }
    
    // Thao tác chính ví dụ: chạm giữa màn hình
    var size = screenSize();
    click(size.width / 2, size.height / 2);
    
    sleep(2000);
}

log("Hoàn thành tác vụ có bảo vệ chống popup.");
""".trimIndent()
    )

    val script5RetryOnMissingText = ScriptEntity(
        name = "5. Thử lại khi không thấy chữ (Retry Pattern)",
        description = "Thực hiện tìm kiếm chữ với cơ chế thử lại (Retry) có exponential backoff, giới hạn timeout và thông báo trạng thái.",
        isPreset = true,
        code = """
// Script: Thử lại (Retry) thông minh khi chờ chữ xuất hiện
log("Khởi chạy cơ chế chờ và thử lại...");

function waitForTextWithRetry(targetText, maxRetries, waitIntervalMs) {
    log("Đang đợi chữ: '" + targetText + "' (tối đa " + maxRetries + " lần thử)...");
    
    for (var attempt = 1; attempt <= maxRetries; attempt++) {
        log("Thử lần " + attempt + "/" + maxRetries + ": đang quét OCR...");
        var element = findText(targetText);
        
        if (element) {
            log("✓ Thành công tại lần thử " + attempt + "! Tìm thấy '" + element.text + "'");
            return element;
        }
        
        if (attempt < maxRetries) {
            // Chờ trước khi thử lại
            log("Chưa thấy, chờ " + waitIntervalMs + "ms để thử lại...");
            sleep(waitIntervalMs);
        }
    }
    
    log("✗ Hết số lần thử! Không tìm thấy: " + targetText);
    return null;
}

// Sử dụng hàm thử lại: tìm chữ 'Bắt đầu' hoặc 'Tiếp tục'
var button = waitForTextWithRetry("Bắt đầu", 5, 2000);

if (button) {
    click(button.centerX, button.centerY);
    toast("Đã bấm: " + button.text);
} else {
    toast("Không thấy chữ sau nhiều lần thử, an toàn dừng lại.");
    stop();
}
""".trimIndent()
    )

    val scriptSpxSingleCod = ScriptEntity(
        name = "Đơn lẻ thu COD (SPX - 8 Bước)",
        description = "Quy trình 8 bước tự động cho shipper: bỏ qua kiểm hàng, copy mã, đã giao hàng, nhập tay, dán mã, chọn tiền mặt, người nhận và mở máy ảnh.",
        isPreset = true,
        code = """
// ============================================================
// Kịch bản: Giao Đơn Lẻ Thu COD (8 Bước Shopee Express)
// Tọa độ căn chỉnh chuẩn xác theo Pointer Location
// ============================================================

log("🚀 Bắt đầu quy trình Giao đơn lẻ thu COD...");
toast("Bắt đầu quy trình 8 bước SPX");

var size = screenSize();
var W = size.width;
var H = size.height;

// BƯỚC 1 (Ảnh 1): Bấm vào dòng chữ "Người nhận không muốn kiểm hàng?"
log("👉 Bước 1: Bấm 'Người nhận không muốn kiểm hàng?'");
click(540, 2596);
sleep(650);

// BƯỚC 2 (Ảnh 2): Chạm copy mã đơn hàng & Bấm nút "Đã giao hàng"
log("👉 Bước 2.1: Chạm icon Copy mã đơn hàng");
click(540, 360);
sleep(400);

log("👉 Bước 2.2: Bấm nút 'Đã giao hàng'");
click(778, 2579);
sleep(850);

// BƯỚC 3 (Ảnh 3.1): Bấm vào icon cây bút (Nhập tay) ở góc trên camera quét
log("👉 Bước 3: Bấm icon Nhập tay (cây bút)");
click(1014, 200);
sleep(800);

// BƯỚC 4 (Ảnh 3): Dán mã vận đơn từ gợi ý clipboard trên bàn phím
log("👉 Bước 4: Dán mã vận đơn từ clipboard bàn phím");
click(W * 0.5, H * 0.69, 0);
sleep(600);

// BƯỚC 5 (Ảnh 4): Bấm nút "XÁC NHẬN" trên ô nhập tay
log("👉 Bước 5: Bấm nút 'XÁC NHẬN'");
click(860, 1702);
sleep(850);

// BƯỚC 6 (Ảnh 5): Chọn phương thức "Tiền mặt" & Bấm "Tiếp theo"
log("👉 Bước 6.1: Chọn 'Tiền mặt'");
click(312, 908);
sleep(450);

log("👉 Bước 6.2: Bấm nút 'Tiếp theo'");
click(540, 2580);
sleep(800);

// BƯỚC 7 (Ảnh 6): Chọn kiểu người nhận "Người nhận" & Bấm "Tiếp theo"
log("👉 Bước 7.1: Chọn kiểu 'Người nhận'");
click(183, 460);
sleep(450);

log("👉 Bước 7.2: Bấm nút 'Tiếp theo'");
click(540, 2580);
sleep(850);

// BƯỚC 8 (Ảnh 7): Màn hình Biên bản giao hàng, bấm vào ô Máy ảnh
log("👉 Bước 8: Bấm mở camera chụp hình bằng chứng");
click(145, 961);

log("✅ Hoàn thành 8 bước đơn lẻ COD!");
toast("Đã hoàn tất 8 bước SPX! Chụp hình để kết thúc.");
""".trimIndent()
    )

    val allPresets = listOf(
        scriptSpxSingleCod,
        script1SkipButton,
        script2AutoPasteAndSend,
        script3LoopWithExitCondition,
        script4UnexpectedPopup,
        script5RetryOnMissingText
    )
}
