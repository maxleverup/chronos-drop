package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.BmrngFile
import com.example.api.ChronosClient
import com.example.data.AppDatabase
import com.example.data.TransferHistory
import com.example.data.TransferRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class MultiZipEntry(
    val index: Int,
    val originalName: String,
    val ext: String,
    val sizeStr: String
)

data class UploadTask(
    val id: String,
    val name: String,
    val size: Long,
    val uri: android.net.Uri,      // ← chỉ lưu Uri, KHÔNG load bytes vào RAM
    val mimeType: String,
    val progress: Float,
    val status: String, // "PENDING", "WAITING", "UPLOADING", "COMPLETED", "FAILED"
    val code: String,
    // Fields có default phải đặt cuối
    val pendingFilesInfo: List<Triple<String, android.net.Uri, Long>> = emptyList(),
    val pendingMimeTypes: List<String> = emptyList()
)

class ChronosViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ChronosViewModel"
    private val repository: TransferRepository
    val historyList: StateFlow<List<TransferHistory>>

    val activeTab = MutableStateFlow(0) // 0: Home/SEND, 1: RECEIVE, 2: History, 3: Settings

    // Pending shared URIs chờ user chọn chế độ Single / Multi
    val pendingSharedUris = MutableStateFlow<List<android.net.Uri>>(emptyList())

    // Active upload queue
    val uploadQueue = MutableStateFlow<List<UploadTask>>(emptyList())

    // Active upload state
    val isUploading = MutableStateFlow(false)
    val uploadProgress = MutableStateFlow(0f)
    val currentUploadFileName = MutableStateFlow("")
    val currentUploadFileSize = MutableStateFlow(0L)
    val currentGeneratedCode = MutableStateFlow("")

    // Active receive state
    val isReceiving = MutableStateFlow(false)
    val receiveCodeInput = MutableStateFlow("")
    val resolvedFiles = MutableStateFlow<List<BmrngFile>>(emptyList())
    val showReceiveSpace = MutableStateFlow(true) // Show by default in tab

    // Active download state
    val isDownloading = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow(0f)
    val currentDownloadFileName = MutableStateFlow("")
    val currentDownloadFileSize = MutableStateFlow(0L)
    
    // Preview Content Dialog State
    val previewFileContent = MutableStateFlow<String?>(null)
    val previewFileName = MutableStateFlow("")
    val isDownloadingPayload = MutableStateFlow(false)

    // Notification Alerts Flow
    private val _statusAlert = MutableSharedFlow<String>()
    val statusAlert: SharedFlow<String> = _statusAlert.asSharedFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransferRepository(database.transferHistoryDao())
        
        historyList = repository.allTransfers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    /**
     * Helper to show alerts
     */
    private fun notify(message: String) {
        viewModelScope.launch {
            _statusAlert.emit(message)
        }
    }

    /**
     * Kiểm tra xem tên file có phải là multi-zip theo cú pháp không.
     * Ví dụ: "1_lostmusic_mp3_35mb__2_highlight_mp4_1mb.zip"
     */
    companion object {
        fun isMultiZipFilename(filename: String): Boolean {
            if (!filename.endsWith(".zip", ignoreCase = true)) return false
            val base = filename.removeSuffix(".zip")
            // Phải có ít nhất 1 entry dạng: <số>_<tên>_<ext>_<size>
            return base.contains(Regex("^\\d+_.+_.+_.+"))
        }

        /**
         * Parse tên file multi-zip thành danh sách (index, tên gốc, ext, size string)
         * Ví dụ: "1_lostmusic_mp3_35mb__2_highlight_mp4_1mb.zip"
         *   → [(1, "lostmusic", "mp3", "35mb"), (2, "highlight", "mp4", "1mb")]
         */
        fun parseMultiZipFilename(filename: String): List<MultiZipEntry> {
            val base = filename.removeSuffix(".zip")
            // Split by "__" to get each file block
            val blocks = base.split("__")
            val result = mutableListOf<MultiZipEntry>()
            for (block in blocks) {
                // Block format: "<index>_<name>_<ext>_<size>"
                val parts = block.split("_")
                if (parts.size >= 4) {
                    val index = parts[0].toIntOrNull() ?: continue
                    val ext = parts[parts.size - 2]
                    val sizeStr = parts[parts.size - 1]
                    // tên file là phần ở giữa: từ parts[1] đến parts[size-3]
                    val nameParts = parts.subList(1, parts.size - 2)
                    val name = nameParts.joinToString("_")
                    result.add(MultiZipEntry(index, "$name.$ext", ext, sizeStr))
                }
            }
            return result
        }

        fun parseSizeStr(sizeStr: String): Long {
            val s = sizeStr.lowercase().trim()
            return when {
                s.endsWith("gb") -> (s.removeSuffix("gb").toLongOrNull() ?: 0L) * 1_073_741_824L
                s.endsWith("mb") -> (s.removeSuffix("mb").toLongOrNull() ?: 0L) * 1_048_576L
                s.endsWith("kb") -> (s.removeSuffix("kb").toLongOrNull() ?: 0L) * 1024L
                s.endsWith("b")  -> s.removeSuffix("b").toLongOrNull() ?: 0L
                else -> s.toLongOrNull() ?: 0L
            }
        }
    }

    /**
     * Multi-file upload mới: nén tất cả files thành 1 ZIP, đặt tên theo cú pháp,
     * rồi upload như 1 file đơn thông thường.
     * Tên ZIP: "1_name1_ext1_size1__2_name2_ext2_size2.zip"
     */
    fun addMultiUploadTask(filesInfo: List<Triple<String, Uri, Long>>, mimeTypes: List<String>) {
        if (filesInfo.isEmpty()) return
        if (filesInfo.sumOf { it.third } <= 0L) {
            notify("Không có file hợp lệ để gửi!")
            return
        }

        val taskId = java.util.UUID.randomUUID().toString()

        // Tạo tên zip theo cú pháp
        val zipName = buildMultiZipName(filesInfo)

        // Placeholder task để hiển thị trong queue — PENDING (chờ xác nhận)
        val zipTask = UploadTask(
            id = taskId,
            name = zipName,
            size = filesInfo.sumOf { it.third },
            uri = filesInfo[0].second,
            mimeType = "application/zip",
            progress = 0f,
            status = "PENDING",
            code = "",
            pendingFilesInfo = filesInfo,
            pendingMimeTypes = mimeTypes
        )
        uploadQueue.value = uploadQueue.value + zipTask
        // Không upload ngay — chờ user bấm nút Upload
    }

    /**
     * Xác nhận upload multi-file task đang PENDING.
     * Thực hiện nén + upload sau khi user bấm Upload.
     */
    fun confirmMultiUploadTask(taskId: String) {
        val task = uploadQueue.value.firstOrNull { it.id == taskId && it.status == "PENDING" } ?: return
        val filesInfo = task.pendingFilesInfo
        val mimeTypes = task.pendingMimeTypes

        viewModelScope.launch {
            val appContext = getApplication<Application>()
            val zipName = task.name

            try {
                updateTaskStatus(taskId, "UPLOADING")
                isUploading.value = true
                currentUploadFileName.value = zipName
                currentUploadFileSize.value = filesInfo.sumOf { it.third }

                // Bước 1: Tạo file zip tạm trong cache
                notify("Đang nén ${filesInfo.size} file...")
                val cacheDir = File(appContext.cacheDir, "chronos_multizip")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tempZipFile = File(cacheDir, "temp_multi_${System.currentTimeMillis()}.zip")

                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                        filesInfo.forEach { (name, uri, _) ->
                            val entry = ZipEntry(name)
                            zos.putNextEntry(entry)
                            appContext.contentResolver.openInputStream(uri)?.use { input ->
                                input.copyTo(zos, bufferSize = 65536)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                // Bước 2: Đổi tên file zip thành tên cú pháp
                val finalZipFile = File(cacheDir, zipName)
                if (finalZipFile.exists()) finalZipFile.delete()
                tempZipFile.renameTo(finalZipFile)

                val zipSize = finalZipFile.length()
                currentUploadFileSize.value = zipSize

                // Bước 3: Upload zip như file đơn
                val result = com.example.api.ChronosClient.uploadAndRegisterFile(
                    filename = zipName,
                    fileSize = zipSize,
                    openStream = { finalZipFile.inputStream() },
                    mimeType = "application/zip",
                    onProgress = { progress ->
                        updateTaskProgress(taskId, progress)
                        uploadProgress.value = progress
                    }
                )

                val generatedCode = result.first
                val spaceId = result.second

                updateTaskCompleted(taskId, generatedCode, "COMPLETED")
                currentGeneratedCode.value = generatedCode

                // Lưu history cho từng file gốc (không phải tên zip)
                filesInfo.forEach { (name, _, size) ->
                    repository.insertTransfer(
                        com.example.data.TransferHistory(
                            fileName = name,
                            fileSize = size,
                            code = generatedCode,
                            spaceId = spaceId,
                            type = "SEND",
                            status = "Completed"
                        )
                    )
                }

                // Dọn dẹp file tạm
                finalZipFile.delete()

                notify("Đã gửi ${filesInfo.size} file! Code: $generatedCode")
            } catch (e: Exception) {
                android.util.Log.e("ChronosViewModel", "Multi-upload failed: ", e)
                updateTaskCompleted(taskId, "", "FAILED")
                notify("Upload thất bại: ${e.localizedMessage}")
            } finally {
                isUploading.value = false
            }
        }
    }

    /**
     * Tạo tên ZIP theo cú pháp: "1_name_ext_size__2_name2_ext2_size2.zip"
     */
    private fun buildMultiZipName(filesInfo: List<Triple<String, Uri, Long>>): String {
        val parts = filesInfo.mapIndexed { i, (name, _, size) ->
            val dotIndex = name.lastIndexOf('.')
            val baseName = if (dotIndex >= 0) name.substring(0, dotIndex) else name
            val ext = if (dotIndex >= 0) name.substring(dotIndex + 1) else "bin"
            val sizeStr = formatBytesShort(size)
            "${i + 1}_${baseName}_${ext}_${sizeStr}"
        }
        return parts.joinToString("__") + ".zip"
    }

    private fun formatBytesShort(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "${bytes / 1_073_741_824L}gb"
            bytes >= 1_048_576L -> "${bytes / 1_048_576L}mb"
            bytes >= 1024L -> "${bytes / 1024L}kb"
            else -> "${bytes}b"
        }
    }

    /**
     * Tải file zip multi và giải nén trực tiếp các file gốc vào Download/Chronos Drop
     */
    fun downloadAndExtractMultiZip(file: com.example.api.BmrngFile, context: Context) {
        viewModelScope.launch {
            try {
                isDownloading.value = true
                currentDownloadFileName.value = file.filename
                currentDownloadFileSize.value = file.size
                downloadProgress.value = 0f


                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val chronosDropDir = File(downloadsDir, "Chronos Drop")
                if (!chronosDropDir.exists()) chronosDropDir.mkdirs()

                // Tải zip về cache tạm
                val cacheDir = File(context.cacheDir, "chronos_multizip")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val tempZip = File(cacheDir, "recv_${System.currentTimeMillis()}.zip")

                ChronosClient.downloadFileToLocalFile(
                    downloadUrl = file.downloadUrl,
                    destinationFile = tempZip,
                    onProgress = { progress ->
                        downloadProgress.value = progress * 0.8f // 80% cho download
                    }
                )

                // Giải nén tất cả files ra Chronos Drop
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ZipInputStream(tempZip.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val outFile = File(chronosDropDir, entry.name)
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos, bufferSize = 65536)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                downloadProgress.value = 1f

                tempZip.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Multi-zip download failed: ", e)
                notify("Tải thất bại: ${e.localizedMessage ?: "Lỗi không xác định"}")
            } finally {
                isDownloading.value = false
            }
        }
    }

    /**
     * Send Tab Logic: Add file to upload queue using Uri (no bytes loaded into RAM)
     */
    fun addUploadTask(fileName: String, uri: android.net.Uri, fileSize: Long, mimeType: String) {
        if (fileSize <= 0L) {
            notify("Cannot transfer empty file!")
            return
        }
        val newTask = UploadTask(
            id = java.util.UUID.randomUUID().toString(),
            name = fileName,
            size = fileSize,
            uri = uri,
            mimeType = mimeType,
            progress = 0f,
            status = "PENDING",
            code = ""
        )
        uploadQueue.value = uploadQueue.value + newTask
        // Không gọi processQueue() — chờ user bấm Upload
    }

    /**
     * Xác nhận upload một task đang PENDING (single file).
     * Đổi trạng thái sang WAITING rồi kích hoạt queue.
     */
    fun confirmUploadTask(taskId: String) {
        uploadQueue.value = uploadQueue.value.map {
            if (it.id == taskId && it.status == "PENDING") it.copy(status = "WAITING") else it
        }
        processQueue()
    }

    /**
     * Xóa một task đang PENDING khỏi queue (user bấm Remove).
     */
    fun removePendingTask(taskId: String) {
        uploadQueue.value = uploadQueue.value.filter { it.id != taskId }
    }

    private var isProcessingQueue = false

    private fun processQueue() {
        if (isProcessingQueue) return
        isProcessingQueue = true
        
        viewModelScope.launch {
            try {
                while (true) {
                    val queue = uploadQueue.value
                    val nextTask = queue.firstOrNull { it.status == "WAITING" }
                    if (nextTask == null) {
                        break
                    }
                    
                    updateTaskStatus(nextTask.id, "UPLOADING")
                    
                    // Update legacy active upload attributes as well
                    isUploading.value = true
                    currentUploadFileName.value = nextTask.name
                    currentUploadFileSize.value = nextTask.size
                    uploadProgress.value = 0f
                    currentGeneratedCode.value = ""

                    Log.d(TAG, "Starting sequential transmission of ${nextTask.name}...")
                    
                    try {
                        val appContext = getApplication<Application>()
                        val result = ChronosClient.uploadAndRegisterFile(
                            filename = nextTask.name,
                            fileSize = nextTask.size,
                            openStream = {
                                // Opens a fresh InputStream each time — never holds whole file in RAM
                                appContext.contentResolver.openInputStream(nextTask.uri)
                                    ?: throw IllegalStateException("Cannot open file stream")
                            },
                            mimeType = nextTask.mimeType,
                            onProgress = { progress ->
                                updateTaskProgress(nextTask.id, progress)
                                uploadProgress.value = progress
                            }
                        )

                        val generatedCode = result.first
                        val spaceId = result.second

                        updateTaskCompleted(nextTask.id, generatedCode, "COMPLETED")
                        currentGeneratedCode.value = generatedCode

                        // Insert into local historical database
                        repository.insertTransfer(
                            TransferHistory(
                                fileName = nextTask.name,
                                fileSize = nextTask.size,
                                code = generatedCode,
                                spaceId = spaceId,
                                type = "SEND",
                                status = "Completed"
                            )
                        )

                        notify("Uploaded ${nextTask.name} successfully! Code: $generatedCode")
                    } catch (e: Exception) {
                        Log.e(TAG, "Upload failed for ${nextTask.name}: ", e)
                        updateTaskCompleted(nextTask.id, "", "FAILED")
                        notify("Upload failed for ${nextTask.name}: ${e.localizedMessage}")
                        
                        repository.insertTransfer(
                            TransferHistory(
                                fileName = nextTask.name,
                                fileSize = nextTask.size,
                                code = "Failed",
                                spaceId = "Failed",
                                type = "SEND",
                                status = "Failed"
                            )
                        )
                    }
                }
            } finally {
                isUploading.value = false
                isProcessingQueue = false
            }
        }
    }

    private fun updateTaskStatus(id: String, status: String) {
        uploadQueue.value = uploadQueue.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    private fun updateTaskProgress(id: String, progress: Float) {
        uploadQueue.value = uploadQueue.value.map {
            if (it.id == id) it.copy(progress = progress) else it
        }
    }

    private fun updateTaskCompleted(id: String, code: String, status: String) {
        uploadQueue.value = uploadQueue.value.map {
            if (it.id == id) it.copy(status = status, code = code, progress = 1f) else it
        }
    }

    /**
     * Receive Tab Logic: Resolve 8-digit access code and retrieve file details
     */
    fun resolveReceiveCode(code: String) {
        val codeClean = code.trim().replace(" ", "")
        if (codeClean.length < 4) {
            notify("Please enter a valid access code (typically 8 digits)")
            return
        }
        viewModelScope.launch {
            try {
                isReceiving.value = true
                resolvedFiles.value = emptyList()

                Log.d(TAG, "Resolving transfer code: $codeClean")
                val spaceId = ChronosClient.resolveCodeToSpaceId(codeClean)
                if (spaceId == null) {
                    notify("Invalid, expired, or deactivated access code!")
                    return@launch
                }

                // Filter out zero-byte or empty files
                val files = ChronosClient.getSpaceDetails(spaceId).filter {
                    it.filename.isNotBlank() && it.downloadUrl.isNotBlank() && it.size > 0L
                }
                if (files.isEmpty()) {
                    notify("No active files found in this transfer space!")
                    return@launch
                }

                resolvedFiles.value = files
                notify("Founded file from code $codeClean!")

                // Insert into historical database
                for (file in files) {
                    if (isMultiZipFilename(file.filename)) {
                        // Lưu từng file gốc thay vì tên zip
                        val entries = parseMultiZipFilename(file.filename)
                        if (entries.isNotEmpty()) {
                            for (entry in entries) {
                                // Ước tính size từ sizeStr (ví dụ "35mb", "1kb")
                                val estimatedSize = parseSizeStr(entry.sizeStr)
                                repository.insertTransfer(
                                    TransferHistory(
                                        fileName = entry.originalName,
                                        fileSize = estimatedSize,
                                        code = codeClean,
                                        spaceId = spaceId,
                                        type = "RECEIVE",
                                        status = "Completed"
                                    )
                                )
                            }
                        } else {
                            repository.insertTransfer(
                                TransferHistory(
                                    fileName = file.filename,
                                    fileSize = file.size,
                                    code = codeClean,
                                    spaceId = spaceId,
                                    type = "RECEIVE",
                                    status = "Completed"
                                )
                            )
                        }
                    } else {
                        repository.insertTransfer(
                            TransferHistory(
                                fileName = file.filename,
                                fileSize = file.size,
                                code = codeClean,
                                spaceId = spaceId,
                                type = "RECEIVE",
                                status = "Completed"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ", e)
                notify("Resolution failed: ${e.localizedMessage ?: "Connection error"}")
            } finally {
                isReceiving.value = false
            }
        }
    }

    /**
     * Download a specific file to public Downloads/Chronos Drop directory with progress
     */
    fun downloadFile(file: BmrngFile, context: Context) {
        viewModelScope.launch {
            try {
                isDownloading.value = true
                currentDownloadFileName.value = file.filename
                currentDownloadFileSize.value = file.size
                downloadProgress.value = 0f

                notify("Starting download of ${file.filename}...")

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val chronosDropDir = File(downloadsDir, "Chronos Drop")
                if (!chronosDropDir.exists()) {
                    val created = chronosDropDir.mkdirs()
                    Log.d(TAG, "Created Chronos Drop folder: $created")
                }
                
                val destinationFile = File(chronosDropDir, file.filename)

                ChronosClient.downloadFileToLocalFile(
                    downloadUrl = file.downloadUrl,
                    destinationFile = destinationFile,
                    onProgress = { progress ->
                        downloadProgress.value = progress
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ", e)
                notify("Download failed: ${e.localizedMessage ?: "Unknown download error"}")
            } finally {
                isDownloading.value = false
            }
        }
    }

    /**
     * Download or Preview a specific file
     */
    fun handleFileAction(file: BmrngFile, context: Context, previewOnly: Boolean) {
        viewModelScope.launch {
            try {
                isDownloadingPayload.value = true
                notify("Fetching ${file.filename}...")
                
                val bytes = ChronosClient.downloadFileBytes(file.downloadUrl)
                if (bytes.isEmpty()) {
                    notify("Downloaded content is empty!")
                    return@launch
                }

                if (previewOnly) {
                    // Try to decode text content to show on modal
                    val text = try {
                        val decoded = bytes.toString(Charsets.UTF_8)
                        // Verify printable characters
                        if (decoded.none { it.code < 9 && it.code != 10 && it.code != 13 }) {
                            decoded
                        } else {
                            "Binary files (MIME/Type images/non-text) cannot be viewed in text preview. Please use Share File to open other apps!"
                        }
                    } catch (ex: Exception) {
                        "File contents could not be previewed."
                    }
                    previewFileName.value = file.filename
                    previewFileContent.value = text
                } else {
                    // Save to Cache directory to issue android native share sharesheet (safe, no permissions required)
                    val cacheDir = File(context.cacheDir, "chronos_transfers")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    val cachedFile = File(cacheDir, file.filename)
                    FileOutputStream(cachedFile).use { fos ->
                        fos.write(bytes)
                    }

                    // Native Android File Share ShareSheet
                    val authority = "${context.packageName}.fileprovider"
                    val fileUri = FileProvider.getUriForFile(context, authority, cachedFile)
                    
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(cachedFile.extension) ?: "application/octet-stream"

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Chronos Drop Transfer: ${file.filename}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Save or Open File")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    notify("Sharing file ${file.filename}!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download action failed: ", e)
                notify("Download failed: ${e.localizedMessage ?: "Unknown download error"}")
            } finally {
                isDownloadingPayload.value = false
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteTransferById(id)
            notify("Deleted history record.")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            notify("All history cleared!")
        }
    }

    fun copyToClipboard(text: String, label: String, context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard copy failed", e)
        }
    }
}
