package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.ChronosApp
import com.example.ui.ChronosViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ChronosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Xử lý intent share khi app mới mở
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChronosApp(viewModel = viewModel)
                }
            }
        }
    }

    // Xử lý khi app đang chạy và nhận thêm share intent mới
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Xử lý file được chia sẻ từ file manager hoặc ứng dụng khác.
     * Hỗ trợ cả ACTION_SEND (1 file) lẫn ACTION_SEND_MULTIPLE (nhiều file).
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Nhận 1 file
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    enqueueSharedFile(uri)
                    // Chuyển sang tab SEND
                    viewModel.activeTab.value = 0
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                // Nhận nhiều file — lưu vào pending, hiển thị dialog chọn Single/Multi
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    viewModel.pendingSharedUris.value = uris
                    viewModel.activeTab.value = 0
                }
            }
        }
    }

    /**
     * Đọc thông tin file từ URI và thêm vào hàng chờ upload của ViewModel.
     */
    private fun enqueueSharedFile(uri: Uri) {
        try {
            val cr = contentResolver

            val filename = cr.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) cursor.getString(nameIndex)
                else null
            } ?: "shared_file_${System.currentTimeMillis()}"

            val fileSize = cr.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
            } ?: 0L

            val mime = cr.getType(uri) ?: "application/octet-stream"

            viewModel.addUploadTask(filename, uri, fileSize, mime)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể đọc file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

