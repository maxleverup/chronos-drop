package com.example.ui

import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.BmrngFile
import com.example.data.TransferHistory
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Geometric Balance Theme Colors
val ThemeBackground = Color(0xFFF7FBF2)
val ThemeTextDark = Color(0xFF191D17)
val ThemeHeaderTitle = Color(0xFF002204)
val ThemeMintActive = Color(0xFFB1F3AD)
val ThemeForestGreen = Color(0xFF2E6C31)
val ThemeDeepDarkGreen = Color(0xFF00390A)
val ThemeSubtleBorder = Color(0xFFE0E4DB)
val ThemeSubtleBg = Color(0xFFD8E7D3)
val ThemeLightGreenBadgeText = Color(0xFF111F0E)
val ThemeMutedText = Color(0xFF5C6359)

@Composable
fun ChronosLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centerPoint = center
        val radius = size.minDimension / 2f * 0.72f

        // Draw clock circle outline
        drawCircle(
            color = ThemeMintActive,
            radius = radius,
            center = centerPoint,
            style = Stroke(width = 3f.dp.toPx())
        )

        // Draw Clock Central dot
        drawCircle(
            color = ThemeMintActive,
            radius = 3f.dp.toPx(),
            center = centerPoint
        )

        // Draw clock hands
        drawLine(
            color = ThemeMintActive,
            start = centerPoint,
            end = Offset(centerPoint.x, centerPoint.y - radius * 0.48f),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = ThemeMintActive,
            start = centerPoint,
            end = Offset(centerPoint.x + radius * 0.38f, centerPoint.y),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round
        )

        // DRAW SHARP GREEN ARROW PIERCING FROM BOTTOM-LEFT TO TOP-RIGHT
        val startArrow = Offset(centerPoint.x - radius * 1.25f, centerPoint.y + radius * 1.25f)
        val endArrow = Offset(centerPoint.x + radius * 1.25f, centerPoint.y - radius * 1.25f)

        // Main line of arrow
        drawLine(
            color = Color(0xFF2E6C31),
            start = startArrow,
            end = endArrow,
            strokeWidth = 4.5f.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Draw Arrow Head (diagonally pointing up-right)
        val tipLength = 10f.dp.toPx()
        drawLine(
            color = Color(0xFF2E6C31),
            start = endArrow,
            end = Offset(endArrow.x - tipLength, endArrow.y),
            strokeWidth = 4.5f.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF2E6C31),
            start = endArrow,
            end = Offset(endArrow.x, endArrow.y + tipLength),
            strokeWidth = 4.5f.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChronosApp(viewModel: ChronosViewModel) {
    val context = LocalContext.current
    val activeTabState by viewModel.activeTab.collectAsStateWithLifecycle()
    val statusAlert by viewModel.statusAlert.collectAsState(initial = "")
    val snackbarHostState = remember { SnackbarHostState() }

    // Launch snackbars on status Alerts
    LaunchedEffect(statusAlert) {
        if (statusAlert.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = statusAlert,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = ThemeBackground,
        topBar = {
            // Elegant styled header matching mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.5f))
                    .drawBehind {
                        val strokeWidth = 1f.dp.toPx()
                        val y = size.height - strokeWidth / 2f
                        drawLine(
                            color = ThemeSubtleBorder,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Custom vector app icon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThemeDeepDarkGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            ChronosLogo(modifier = Modifier.fillMaxSize().padding(2.dp))
                        }

                        Column {
                            Text(
                                text = "Chronos Drop",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    color = ThemeHeaderTitle
                                )
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // High fidelity styled bottom navigation matching mockup exactly
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .drawBehind {
                        val strokeWidth = 1f.dp.toPx()
                        drawLine(
                            color = ThemeSubtleBorder,
                            start = Offset(0f, strokeWidth / 2f),
                            end = Offset(size.width, strokeWidth / 2f),
                            strokeWidth = strokeWidth
                        )
                    }
                    .navigationBarsPadding(),
                windowInsets = WindowInsets.navigationBars
            ) {
                val tabs = listOf(
                    Triple(0, "SEND", Icons.Default.ArrowUpward),
                    Triple(1, "RECEIVE", Icons.Default.ArrowDownward),
                    Triple(2, "History", Icons.Default.List),
                    Triple(3, "Settings", Icons.Default.Settings)
                )

                tabs.forEach { (index, title, icon) ->
                    val isSelected = activeTabState == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.activeTab.value = index },
                        modifier = Modifier.testTag("nav_tab_$index"),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) ThemeMintActive else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) ThemeDeepDarkGreen else ThemeMutedText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = title,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) ThemeHeaderTitle else ThemeMutedText
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        
        AnimatedContent(
            targetState = activeTabState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            label = "tab_animation"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> SendScreen(viewModel)
                1 -> ReceiveScreen(viewModel)
                2 -> HistoryScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }
        }
    }
}

/**
 * 8-Digit Access Code Box Grid layout
 */
@Composable
fun CodeDisplayRow(
    code: String,
    modifier: Modifier = Modifier,
    onCodeClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Label "ACCESS CODE" căn giữa
        Text(
            text = "ACCESS CODE",
            style = TextStyle(
                fontSize = 11.sp,
                color = ThemeMutedText,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2f.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Ô chứa 8 ký tự code dạng dài, dồn trái, scale tự động
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(ThemeBackground, shape = RoundedCornerShape(12.dp))
                    .border(1.dp, ThemeSubtleBg, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val displayedCode = if (code.isEmpty() || code == "Generating...") "- - - - - - - -" else code.padEnd(8).take(8).uppercase()
                Text(
                    text = displayedCode.toCharArray().joinToString("  "),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (code.isEmpty() || code == "Generating...") ThemeMutedText else ThemeHeaderTitle
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    fontSize = 18.sp
                )
            }

            // 2 nút Copy và Share ở bên phải
            if (code.isNotEmpty() && code != "Generating...") {
                Spacer(modifier = Modifier.width(10.dp))
                // Nút Copy
                IconButton(
                    onClick = { onCodeClick?.invoke() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(ThemeSubtleBg, shape = RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = ThemeForestGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                // Nút Share
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Chronos Drop Access Code: $code")
                            putExtra(Intent.EXTRA_SUBJECT, "Chronos Drop Access Code")
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, "Chia sẻ mã truy cập").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(ThemeSubtleBg, shape = RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share code",
                        tint = ThemeForestGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxChar = 8
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { input ->
                val filtered = input.filter { it.isLetterOrDigit() }
                if (filtered.length <= maxChar) {
                    onValueChange(filtered.uppercase(java.util.Locale.getDefault()))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("access_code_input_field")
                .alpha(0.01f),
            singleLine = true
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in 0 until maxChar) {
                val char = value.getOrNull(i)?.toString() ?: ""
                val isFocused = value.length == i || (value.length == maxChar && i == maxChar - 1)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(
                            color = if (isFocused) ThemeMintActive.copy(alpha = 0.25f) else ThemeBackground,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) ThemeForestGreen else ThemeSubtleBorder,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeHeaderTitle,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SendScreen(viewModel: ChronosViewModel) {
    val context = LocalContext.current
    val uploadQueue by viewModel.uploadQueue.collectAsStateWithLifecycle()
    val pendingSharedUris by viewModel.pendingSharedUris.collectAsStateWithLifecycle()

    // Dialog chọn chế độ Single / Multi khi share nhiều file vào app
    if (pendingSharedUris.size > 1) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.pendingSharedUris.value = emptyList() },
            containerColor = ThemeBackground,
            title = {
                androidx.compose.material3.Text(
                    text = "${pendingSharedUris.size} files shared",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ThemeTextDark
                    )
                )
            },
            text = {
                androidx.compose.material3.Text(
                    text = "How would you like to send them?",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = ThemeMutedText
                    )
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Multi file button
                    androidx.compose.material3.Button(
                        onClick = {
                            val uris = pendingSharedUris
                            viewModel.pendingSharedUris.value = emptyList()
                            val cr = context.contentResolver
                            val filesInfo = uris.mapNotNull { uri ->
                                try {
                                    val filename = cr.query(uri, null, null, null, null)?.use { cursor ->
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
                                    } ?: return@mapNotNull null
                                    val fileSize = cr.query(uri, null, null, null, null)?.use { cursor ->
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                        if (cursor.moveToFirst() && idx != -1) cursor.getLong(idx) else 0L
                                    } ?: 0L
                                    Triple(filename, uri, fileSize)
                                } catch (e: Exception) { null }
                            }
                            val mimes = uris.map { cr.getType(it) ?: "application/octet-stream" }
                            viewModel.addMultiUploadTask(filesInfo, mimes)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("Multi File", color = Color.White)
                    }
                    // Single file button
                    androidx.compose.material3.Button(
                        onClick = {
                            val uris = pendingSharedUris
                            viewModel.pendingSharedUris.value = emptyList()
                            val cr = context.contentResolver
                            uris.forEach { uri ->
                                try {
                                    val filename = cr.query(uri, null, null, null, null)?.use { cursor ->
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
                                    } ?: "shared_file_${System.currentTimeMillis()}"
                                    val fileSize = cr.query(uri, null, null, null, null)?.use { cursor ->
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                        if (cursor.moveToFirst() && idx != -1) cursor.getLong(idx) else 0L
                                    } ?: 0L
                                    val mime = cr.getType(uri) ?: "application/octet-stream"
                                    viewModel.addUploadTask(filename, uri, fileSize, mime)
                                } catch (e: Exception) { }
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ThemeMintActive),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("Single File (one by one)", color = ThemeDeepDarkGreen)
                    }
                    // Cancel
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.pendingSharedUris.value = emptyList() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("Cancel", color = ThemeMutedText)
                    }
                }
            },
            dismissButton = {}
        )
    }

    // Launcher chọn 1 file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val cr = context.contentResolver
                val filename = cr.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "transfer_file_${System.currentTimeMillis()}"
                val fileSize = cr.query(it, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                } ?: 0L
                val mime = cr.getType(it) ?: "application/octet-stream"
                viewModel.addUploadTask(filename, it, fileSize, mime)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher chọn nhiều file
    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        try {
            val cr = context.contentResolver
            val filesInfo = mutableListOf<Triple<String, Uri, Long>>()
            val mimeTypes = mutableListOf<String>()
            for (uri in uris) {
                val filename = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "file_${System.currentTimeMillis()}"
                val fileSize = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                } ?: 0L
                val mime = cr.getType(uri) ?: "application/octet-stream"
                if (fileSize > 0L) {
                    filesInfo.add(Triple(filename, uri, fileSize))
                    mimeTypes.add(mime)
                }
            }
            if (filesInfo.isEmpty()) {
                Toast.makeText(context, "Không có file hợp lệ được chọn!", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addMultiUploadTask(filesInfo, mimeTypes)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi đọc file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header — chỉ hiển thị label SEND, không có nút nhỏ góc phải
        Text(
            text = "SEND",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeMutedText,
                letterSpacing = 1.5f.sp
            )
        )

        if (uploadQueue.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ThemeSubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(ThemeMintActive.copy(alpha = 0.3f), shape = RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⬆", fontSize = 28.sp, color = ThemeForestGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No files in upload queue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ThemeHeaderTitle
                        )
                        Text(
                            text = "Add files below to begin secure sequential transfers.",
                            fontSize = 12.sp,
                            color = ThemeMutedText,
                            textAlign = TextAlign.Center
                        )
                    }
                    // Nút Add File (1 file)
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_add_file_empty")
                    ) {
                        Text("Add File", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Nút Add Multi File
                    OutlinedButton(
                        onClick = { multiFilePickerLauncher.launch("*/*") },
                        border = BorderStroke(1.5.dp, ThemeForestGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_add_multi_file_empty")
                    ) {
                        Text("Add Multi File", color = ThemeForestGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Text(
                text = "UPLOAD PROCESSES",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeMutedText,
                    letterSpacing = 1.5f.sp
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uploadQueue.forEach { task ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ThemeSubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Header: tên/danh sách + badge trạng thái
                                val isTaskMultiZip = ChronosViewModel.isMultiZipFilename(task.name)
                                val taskEntries = if (isTaskMultiZip) ChronosViewModel.parseMultiZipFilename(task.name) else emptyList()

                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    if (isTaskMultiZip && taskEntries.isNotEmpty()) {
                                        // Hiển thị số lượng file thay vì tên zip
                                        Text(
                                            text = "${taskEntries.size} files",
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ThemeTextDark
                                            )
                                        )
                                        Text(
                                            text = formatBytes(task.size),
                                            fontSize = 11.sp,
                                            color = ThemeMutedText
                                        )
                                    } else {
                                        Text(
                                            text = task.name,
                                            style = TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ThemeTextDark
                                            )
                                        )
                                        Text(
                                            text = formatBytes(task.size),
                                            fontSize = 11.sp,
                                            color = ThemeMutedText
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(
                                            when (task.status) {
                                                "PENDING" -> Color(0xFFFFF8E1)
                                                "UPLOADING" -> ThemeMintActive
                                                "WAITING" -> ThemeSubtleBg
                                                "COMPLETED" -> ThemeSubtleBg
                                                else -> Color(0xFFFFECEB)
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = task.status,
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (task.status) {
                                                "PENDING" -> Color(0xFFE65100)
                                                "UPLOADING" -> ThemeForestGreen
                                                "WAITING" -> ThemeMutedText
                                                "COMPLETED" -> ThemeLightGreenBadgeText
                                                else -> Color(0xFFC62828)
                                            }
                                        )
                                    )
                                }
                            }

                            // Danh sách file nếu là multi-zip
                            val isTaskMultiZipBody = ChronosViewModel.isMultiZipFilename(task.name)
                            val taskEntriesBody = if (isTaskMultiZipBody) ChronosViewModel.parseMultiZipFilename(task.name) else emptyList()
                            if (isTaskMultiZipBody && taskEntriesBody.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    taskEntriesBody.forEach { entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ThemeBackground, shape = RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "${entry.index}",
                                                    style = TextStyle(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ThemeForestGreen
                                                    ),
                                                    modifier = Modifier
                                                        .background(ThemeMintActive, shape = RoundedCornerShape(5.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                                Text(
                                                    text = entry.originalName,
                                                    style = TextStyle(
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ThemeTextDark
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = entry.sizeStr.uppercase(),
                                                fontSize = 10.sp,
                                                color = ThemeMutedText
                                            )
                                        }
                                    }
                                }
                            }

                            // Nút Upload / Remove cho task đang PENDING (chưa upload)
                            if (task.status == "PENDING") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Nút Upload — nền xanh lá
                                    Button(
                                        onClick = {
                                            if (task.pendingFilesInfo.isNotEmpty()) {
                                                viewModel.confirmMultiUploadTask(task.id)
                                            } else {
                                                viewModel.confirmUploadTask(task.id)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Text(
                                            "Upload",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    // Nút Remove — viền đỏ
                                    OutlinedButton(
                                        onClick = { viewModel.removePendingTask(task.id) },
                                        border = BorderStroke(1.5.dp, Color(0xFFC62828)),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFC62828)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Text(
                                            "Remove",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            if (task.status == "UPLOADING") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(ThemeSubtleBorder, shape = RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = task.progress)
                                            .background(ThemeForestGreen, shape = RoundedCornerShape(2.dp))
                                    )
                                }
                                Text(
                                    text = "Uploading... ${(task.progress * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    color = ThemeMutedText
                                )
                            }

                            if (task.status == "COMPLETED" && task.code.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                CodeDisplayRow(
                                    code = task.code,
                                    onCodeClick = {
                                        viewModel.copyToClipboard(task.code, "Access Code", context)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Nút Add File (1 file)
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("send_add_file_below")
            ) {
                Text("Add File", color = Color.White, fontWeight = FontWeight.Bold)
            }
            // Nút Add Multi File
            OutlinedButton(
                onClick = { multiFilePickerLauncher.launch("*/*") },
                border = BorderStroke(1.5.dp, ThemeForestGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("send_add_multi_file_below")
            ) {
                Text("Add Multi File", color = ThemeForestGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReceiveScreen(viewModel: ChronosViewModel) {
    val context = LocalContext.current
    val isReceiving by viewModel.isReceiving.collectAsStateWithLifecycle()
    val receiveCodeInput by viewModel.receiveCodeInput.collectAsStateWithLifecycle()
    val resolvedFiles by viewModel.resolvedFiles.collectAsStateWithLifecycle()

    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val currentDownloadFileName by viewModel.currentDownloadFileName.collectAsStateWithLifecycle()
    val currentDownloadFileSize by viewModel.currentDownloadFileSize.collectAsStateWithLifecycle()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingDownloadFile by remember { mutableStateOf<BmrngFile?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        if (granted) {
            pendingDownloadFile?.let { file ->
                if (ChronosViewModel.isMultiZipFilename(file.filename)) {
                    viewModel.downloadAndExtractMultiZip(file, context)
                } else {
                    viewModel.downloadFile(file, context)
                }
            }
        } else {
            Toast.makeText(context, "Quyền ghi bộ nhớ bị từ chối/bị hạn chế!", Toast.LENGTH_SHORT).show()
        }
        pendingDownloadFile = null
    }

    val checkAndDownload: (BmrngFile, Boolean) -> Unit = { file, isMultiZip ->
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            if (isMultiZip) {
                viewModel.downloadAndExtractMultiZip(file, context)
            } else {
                viewModel.downloadFile(file, context)
            }
        } else {
            pendingDownloadFile = file
            showPermissionDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        Text(
            text = "RECEIVE",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ThemeMutedText,
                letterSpacing = 1.5f.sp
            )
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, ThemeSubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "RECEIVE TRANSFER SPACE",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeMutedText,
                        letterSpacing = 1.5f.sp
                    )
                )

                CodeInputField(
                    value = receiveCodeInput,
                    onValueChange = { newValue ->
                        viewModel.receiveCodeInput.value = newValue
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Nút Get Files - chiếm 2/3
                    Button(
                        onClick = { viewModel.resolveReceiveCode(receiveCodeInput) },
                        enabled = receiveCodeInput.length >= 4 && !isReceiving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeDeepDarkGreen,
                            disabledContainerColor = ThemeSubtleBorder
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp)
                            .testTag("fetch_space_button")
                    ) {
                        if (isReceiving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Get Files", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Nút Reset - chiếm 1/3, màu đảo ngược (nền sáng, chữ tối)
                    Button(
                        onClick = { viewModel.receiveCodeInput.value = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ThemeSubtleBg,
                            disabledContainerColor = ThemeSubtleBorder
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Reset", color = ThemeDeepDarkGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isDownloading,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, ThemeSubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE DOWNLOADS",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeMutedText,
                                letterSpacing = 1.5f.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .background(ThemeSubtleBg, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "DOWNLOADING",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeLightGreenBadgeText
                                )
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDownloadFileName,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTextDark
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}% / ${formatBytes(currentDownloadFileSize)}",
                                style = TextStyle(fontSize = 12.sp, color = ThemeMutedText)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(ThemeSubtleBorder, shape = RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = downloadProgress.coerceAtLeast(0f).coerceAtMost(1f))
                                    .background(ThemeForestGreen, shape = RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }

        if (resolvedFiles.isNotEmpty()) {
            resolvedFiles.forEach { file ->
                val isMultiZip = ChronosViewModel.isMultiZipFilename(file.filename)
                val multiEntries = if (isMultiZip) ChronosViewModel.parseMultiZipFilename(file.filename) else emptyList()

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, ThemeSubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMultiZip) "MULTI-FILE PACKAGE" else "FILES DISCOVERED",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeMutedText,
                                    letterSpacing = 1.5f.sp
                                )
                            )
                            Text(
                                text = if (isMultiZip) "${multiEntries.size} file(s)" else formatBytes(file.size),
                                fontSize = 11.sp,
                                color = ThemeForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isMultiZip && multiEntries.isNotEmpty()) {
                            // Nút tải tất cả - giải nén trực tiếp
                            val zipExists = remember(file.filename, isDownloading) {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val chronosDropDir = java.io.File(downloadsDir, "Chronos Drop")
                                    multiEntries.all { entry ->
                                        java.io.File(chronosDropDir, entry.originalName).exists()
                                    }
                                } catch (e: Exception) { false }
                            }

                            // Danh sách file duy nhất — nút open/folder hiện sau khi tải xong
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                multiEntries.forEach { entry ->
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val chronosDropDir = java.io.File(downloadsDir, "Chronos Drop")
                                    val fileOnDisk = java.io.File(chronosDropDir, entry.originalName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ThemeBackground, shape = RoundedCornerShape(12.dp))
                                            .border(1.dp, ThemeSubtleBorder, shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${entry.index}",
                                                style = TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ThemeForestGreen
                                                ),
                                                modifier = Modifier
                                                    .background(ThemeMintActive, shape = RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.originalName,
                                                    style = TextStyle(
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = ThemeTextDark
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = entry.sizeStr.uppercase(),
                                                    fontSize = 11.sp,
                                                    color = ThemeMutedText
                                                )
                                            }
                                        }
                                        if (zipExists) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(ThemeForestGreen, shape = RoundedCornerShape(18.dp))
                                                        .clickable { openFileWith(context, fileOnDisk) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.OpenInNew,
                                                        contentDescription = "Open File",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(ThemeMintActive, shape = RoundedCornerShape(18.dp))
                                                        .clickable { openChronosFolder(context) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = "Open Folder",
                                                        tint = ThemeDeepDarkGreen,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (!zipExists) {
                                Button(
                                    onClick = { checkAndDownload(file, isMultiZip) },
                                    enabled = !isDownloading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThemeDeepDarkGreen,
                                        disabledContainerColor = ThemeSubtleBorder
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Downloading...", color = Color.White, fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Download All",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                        } else {
                            // File đơn thông thường
                            val fileExists = remember(file.filename, isDownloading) {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val chronosDropDir = java.io.File(downloadsDir, "Chronos Drop")
                                    java.io.File(chronosDropDir, file.filename).exists()
                                } catch (e: Exception) { false }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ThemeBackground, shape = RoundedCornerShape(16.dp))
                                    .border(1.dp, ThemeSubtleBorder, shape = RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = file.filename,
                                        style = TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemeTextDark
                                        )
                                    )
                                    Text(
                                        text = formatBytes(file.size),
                                        fontSize = 11.sp,
                                        color = ThemeMutedText
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    if (fileExists) {
                                        IconButton(
                                            onClick = {
                                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                val chronosDropDir = java.io.File(downloadsDir, "Chronos Drop")
                                                val fileOnDisk = java.io.File(chronosDropDir, file.filename)
                                                openFileWith(context, fileOnDisk)
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(ThemeForestGreen, shape = RoundedCornerShape(18.dp))
                                                .testTag("open_file_${file.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open File With",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { openChronosFolder(context) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(ThemeMintActive, shape = RoundedCornerShape(18.dp))
                                                .testTag("open_folder_${file.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Open Chronos Drop Folder",
                                                tint = ThemeDeepDarkGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { checkAndDownload(file, false) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(ThemeForestGreen, shape = RoundedCornerShape(18.dp))
                                                .testTag("download_file_${file.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Download File",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPermissionDialog) {
            Dialog(onDismissRequest = { 
                showPermissionDialog = false 
                pendingDownloadFile = null
            }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, ThemeSubtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Yêu cầu quyền bộ nhớ",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeHeaderTitle
                            )
                        )
                        Text(
                            text = "Chronos Drop cần quyền Quản lý bộ nhớ để lưu tệp trực tiếp vào thư mục công cộng Downloads/Chronos Drop của thiết bị.",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ThemeTextDark
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { 
                                showPermissionDialog = false 
                                pendingDownloadFile = null
                            }) {
                                Text("Hủy", color = ThemeMutedText)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    showPermissionDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        requestPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            )
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cấp quyền", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: ChronosViewModel) {
    val history by viewModel.historyList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transfer Registry",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeHeaderTitle)
                )
                Text(
                    text = "Stored securely in SQLite database",
                    style = TextStyle(fontSize = 11.sp, color = ThemeMutedText)
                )
            }

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAllHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏳", fontSize = 36.sp)
                    Text(
                        text = "No previous transfers recorded",
                        style = TextStyle(fontSize = 13.sp, color = ThemeMutedText, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Sent/Received logs will populate here",
                        style = TextStyle(fontSize = 11.sp, color = ThemeMutedText)
                    )
                }
            }
        } else {
            // Gộp các item cùng code+type+phút thành một nhóm, sắp xếp theo timestamp mới nhất
            val groupedHistory = remember(history) {
                // Group by code + type + exact minute (HH:mm) so 21:52 and 21:53 are always separate
                val cal = java.util.Calendar.getInstance()
                history
                    .groupBy { item ->
                        cal.timeInMillis = item.timestamp
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        val minute = cal.get(java.util.Calendar.MINUTE)
                        val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
                        val year = cal.get(java.util.Calendar.YEAR)
                        "${item.code}_${item.type}_${year}_${day}_${hour}_${minute}"
                    }
                    .map { (_, items) -> items.sortedByDescending { it.timestamp } }
                    .sortedByDescending { it.first().timestamp }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_list")
            ) {
                items(groupedHistory, key = { it.first().code + "_" + it.first().type + "_" + it.first().timestamp }) { group ->
                    val representative = group.first()
                    val isMulti = group.size > 1

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ThemeSubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header row: type badge + file count + delete button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (representative.type == "SEND") ThemeMintActive else ThemeSubtleBg,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = representative.type,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemeLightGreenBadgeText
                                        )
                                    }
                                    if (isMulti) {
                                        Text(
                                            text = "${group.size} files",
                                            fontSize = 10.sp,
                                            color = ThemeMutedText,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { group.forEach { viewModel.deleteHistoryItem(it.id) } },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("delete_history_${representative.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Danh sách từng file trong nhóm
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                group.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ThemeBackground, shape = RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.fileName,
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ThemeTextDark
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = formatBytes(item.fileSize),
                                            fontSize = 11.sp,
                                            color = ThemeMutedText
                                        )
                                    }
                                }
                            }

                            Divider(
                                color = ThemeSubtleBorder,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ACCESS CODE",
                                        fontSize = 8.sp,
                                        color = ThemeMutedText,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = representative.code,
                                            style = TextStyle(
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = ThemeDeepDarkGreen
                                            ),
                                            modifier = Modifier.clickable {
                                                if (representative.code.isNotBlank() && representative.code != "Failed") {
                                                    viewModel.copyToClipboard(representative.code, "Access Code", context)
                                                }
                                            }
                                        )
                                        if (representative.code.isNotBlank() && representative.code != "Failed") {
                                            IconButton(
                                                onClick = { viewModel.copyToClipboard(representative.code, "Access Code", context) },
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .testTag("copy_history_code_${representative.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Access Code",
                                                    tint = ThemeForestGreen,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "TRANSFERRED",
                                        fontSize = 8.sp,
                                        color = ThemeMutedText,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatDate(representative.timestamp),
                                        fontSize = 11.sp,
                                        color = ThemeTextDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ChronosViewModel) {
    val history by viewModel.historyList.collectAsStateWithLifecycle()

    val totalSent = history.filter { it.type == "SEND" }.size
    val totalReceived = history.filter { it.type == "RECEIVE" }.size
    val totalSize = history.sumOf { it.fileSize }

    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Chronos Analytics",
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeHeaderTitle)
        )

        // Stats Card grid
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, ThemeSubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "TRANSMISSION STATISTICS",
                    fontSize = 10.sp,
                    color = ThemeMutedText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5f.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Sent Files", fontSize = 12.sp, color = ThemeMutedText)
                        Text("$totalSent", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeDeepDarkGreen)
                    }
                    Column {
                        Text("Received", fontSize = 12.sp, color = ThemeMutedText)
                        Text("$totalReceived", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeDeepDarkGreen)
                    }
                    Column {
                        Text("Total Volume", fontSize = 12.sp, color = ThemeMutedText)
                        Text(formatBytes(totalSize), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeDeepDarkGreen)
                    }
                }
            }
        }

        // 2 SQUARE CARDS: Left = Tech Specs, Right = Support
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── LEFT: Protocol / DB / Hosting ──────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ThemeSubtleBorder),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SYSTEM",
                        fontSize = 11.sp,
                        color = ThemeHeaderTitle,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5f.sp,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Protocol",
                                fontSize = 10.sp,
                                color = ThemeMutedText
                            )
                            Text(
                                text = "2.1.0 High-Yield",
                                fontSize = 11.sp,
                                color = ThemeTextDark,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Database",
                                fontSize = 10.sp,
                                color = ThemeMutedText
                            )
                            Text(
                                text = "Active SQLite",
                                fontSize = 11.sp,
                                color = ThemeForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Hosting",
                                fontSize = 10.sp,
                                color = ThemeMutedText
                            )
                            Text(
                                text = "S3 / Firebase RTDB",
                                fontSize = 11.sp,
                                color = ThemeTextDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── RIGHT: Support ─────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ThemeSubtleBorder),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tiêu đề căn giữa, in đậm
                    Text(
                        text = "SUPPORT",
                        fontSize = 11.sp,
                        color = ThemeHeaderTitle,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5f.sp,
                        textAlign = TextAlign.Center
                    )

                    // Mô tả ngay dưới tiêu đề
                    Text(
                        text = "If you enjoy using Chronos Drop, consider supporting via VietQR.",
                        fontSize = 10.sp,
                        color = ThemeMutedText,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    // Dòng gạch ngang
                    Divider(
                        color = ThemeSubtleBorder,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Icon VietQR trong ô vuông to — giống Gmail/Facebook/GitHub
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, ThemeSubtleBorder), RoundedCornerShape(18.dp))
                            .clickable { showQrDialog = true }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.vietqr_logo),
                            contentDescription = "VietQR Support",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
            }
        }


        // CREDIT CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, ThemeSubtleBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "CREDIT",
                    fontSize = 10.sp,
                    color = ThemeMutedText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5f.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nguyen Quoc An",
                        fontSize = 18.sp,
                        color = ThemeHeaderTitle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lead Developer & Application Designer",
                        fontSize = 12.sp,
                        color = ThemeMutedText
                    )
                }

                Divider(color = ThemeSubtleBorder)

                // 3 contact icons — SpaceEvenly, icon 72dp, padding 8dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gmail
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, ThemeSubtleBorder), RoundedCornerShape(18.dp))
                            .clickable { openMail(context, "anvsme123@gmail.com") }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.gmail),
                            contentDescription = "Gmail Contact",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // GitHub
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, ThemeSubtleBorder), RoundedCornerShape(18.dp))
                            .clickable { openLink(context, "https://github.com/maxleverup") }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.social),
                            contentDescription = "GitHub Profile",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Facebook
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, ThemeSubtleBorder), RoundedCornerShape(18.dp))
                            .clickable { openLink(context, "https://www.facebook.com/maxleverup2/") }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.facebook),
                            contentDescription = "Facebook Profile",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }


        // SUPPORT QR DIALOG
        if (showQrDialog) {
            Dialog(onDismissRequest = { showQrDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, ThemeSubtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Viettel Money QR Code",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ThemeHeaderTitle),
                            textAlign = TextAlign.Center
                        )

                        Image(
                            painter = painterResource(id = R.drawable.viettelmoneyqr),
                            contentDescription = "Viettel Money QR Code",
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        // Download Button inside modal below QR image
                        Button(
                            onClick = {
                                downloadQrToDownloads(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ThemeForestGreen),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Download QR Code",
                                    tint = Color.White
                                )
                                Text("Save to Downloads", color = Color.White)
                            }
                        }

                        TextButton(
                            onClick = { showQrDialog = false }
                        ) {
                            Text("Close", color = ThemeMutedText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper to open web link using android Intent
 */
fun openLink(context: android.content.Context, url: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Could not open link: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * Helper to open mail client or fallback to copy to clipboard
 */
fun openMail(context: android.content.Context, email: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email")).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Email address", email)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "Email copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        } catch (ex: Exception) {
            android.widget.Toast.makeText(context, "Could not copy email", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Copies the Viettel Money QR code drawable to local Downloads directory
 */
fun downloadQrToDownloads(context: android.content.Context) {
    try {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = java.io.File(downloadsDir, "viettelmoneyqr.png")
        
        val inputStream = context.resources.openRawResource(com.example.R.drawable.viettelmoneyqr)
        val outputStream = java.io.FileOutputStream(file)
        
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        outputStream.close()
        inputStream.close()
        
        android.widget.Toast.makeText(context, "QR saved to Downloads: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
    } catch (e: java.io.FileNotFoundException) {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, "Viettel_Money_QR.png")
            val inputStream = context.resources.openRawResource(com.example.R.drawable.viettelmoneyqr)
            val outputStream = java.io.FileOutputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.close()
            inputStream.close()
            android.widget.Toast.makeText(context, "QR saved to Downloads!", android.widget.Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            android.widget.Toast.makeText(context, "Failed to save QR code: ${ex.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Failed to save QR code: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Format timestamp values for local display
 */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Clean storage size in string format
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

/**
 * Mở file bằng ứng dụng khác — hiện giao diện "Open with" của Android
 */
fun openFileWith(context: android.content.Context, file: java.io.File) {
    try {
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Không tìm thấy file", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // Ưu tiên MIME từ extension vì ContentResolver trả về null với file ngoài app
        val mime = getMimeTypeFromExtension(file.extension)
            .takeIf { it != "*/*" }
            ?: (context.contentResolver.getType(uri) ?: "*/*")

        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = android.content.Intent.createChooser(intent, "Mở bằng...").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Không thể mở file: ${e.localizedMessage}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Lấy MIME type từ đuôi file nếu ContentResolver không xác định được
 */
fun getMimeTypeFromExtension(ext: String): String {
    return when (ext.lowercase()) {
        "apk"  -> "application/vnd.android.package-archive"
        "pdf"  -> "application/pdf"
        "zip"  -> "application/zip"
        "rar"  -> "application/x-rar-compressed"
        "mp4"  -> "video/mp4"
        "mkv"  -> "video/x-matroska"
        "mp3"  -> "audio/mpeg"
        "jpg", "jpeg" -> "image/jpeg"
        "png"  -> "image/png"
        "gif"  -> "image/gif"
        "txt"  -> "text/plain"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        else   -> "*/*"
    }
}

/**
 * Open the Chronos Drop folder using various standard mime types and an app chooser
 */
fun openChronosFolder(context: android.content.Context) {
    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    val chronosDropDir = java.io.File(downloadsDir, "Chronos Drop")
    if (!chronosDropDir.exists()) {
        chronosDropDir.mkdirs()
    }

    val oldPolicy = android.os.StrictMode.getVmPolicy()
    try {
        val builder = android.os.StrictMode.VmPolicy.Builder()
        android.os.StrictMode.setVmPolicy(builder.build())

        val uri = android.net.Uri.fromFile(chronosDropDir)
        
        // Try resource/folder MIME type first
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = android.content.Intent.createChooser(intent, "Open Folder with...")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        try {
            // Try with vnd.android.document/directory
            val uri = android.net.Uri.fromFile(chronosDropDir)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, "Open Folder with...")
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e2: Exception) {
            try {
                // Try as generic directory view
                val uri = android.net.Uri.fromFile(chronosDropDir)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = android.content.Intent.createChooser(intent, "Open Folder with...")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e3: Exception) {
                android.widget.Toast.makeText(
                    context, 
                    "No file manager found. Please locate Download/Chronos Drop folder manually.", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    } finally {
        android.os.StrictMode.setVmPolicy(oldPolicy)
    }
}
