package com.example.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class BmrngFile(
    val id: String,
    val filename: String,
    val size: Long,
    val downloadUrl: String,
    val uploadUrl: String = ""
)

object ChronosClient {
    private const val TAG = "ChronosClient"
    private const val DATABASE_URL = "https://chronosdrop-rtdb-default-rtdb.firebaseio.com/mappings"

    /**
     * Extracts a single string field from simple flat JSON string using Regex.
     */
    fun extractStringField(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\/", "/")
    }

    /**
     * Parses the files array in the json response.
     */
    fun parseFilesList(json: String): List<BmrngFile> {
        val list = mutableListOf<BmrngFile>()
        // Regular expression to match any JSON object {...} inside files array
        val filePattern = "\\{[^\\}]+\\}".toRegex()
        val matches = filePattern.findAll(json)
        for (m in matches) {
            val fileJson = m.value
            val id = extractStringField(fileJson, "id") ?: ""
            val filename = extractStringField(fileJson, "filename") ?: ""
            
            val sizePattern = "\"size\"\\s*:\\s*(\\d+)".toRegex()
            val size = sizePattern.find(fileJson)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            
            val downloadUrl = extractStringField(fileJson, "downloadUrl") ?: ""
            val uploadUrl = extractStringField(fileJson, "uploadUrl") ?: ""
            
            if (id.isNotEmpty() && filename.isNotEmpty() && downloadUrl.isNotEmpty()) {
                list.add(BmrngFile(id, filename, size, downloadUrl, uploadUrl))
            }
        }
        return list
    }

    /**
     * Creates a temporary space on bmrng.me, streams the file content to S3,
     * registers it with a flat 8-digit numeric access code on Firebase RTDB,
     * and returns the code and the spaceId.
     *
     * Uses streaming (InputStream) instead of ByteArray to avoid OOM on large files.
     */
    suspend fun uploadAndRegisterFile(
        filename: String,
        fileSize: Long,
        openStream: () -> InputStream,
        mimeType: String,
        onProgress: (Float) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {

        // 1. Create space on bmrng.me
        Log.d(TAG, "Creating space on bmrng.me for $filename ($fileSize bytes)...")
        val createUrl = URL("https://bmrng.me/api/upload/space/create")
        val createConn = createUrl.openConnection() as HttpURLConnection
        createConn.requestMethod = "POST"
        createConn.doOutput = true
        createConn.setRequestProperty("Content-Type", "application/json")
        createConn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val createBody = "{\"files\":[{\"filename\":\"$filename\",\"size\":$fileSize,\"contentType\":\"$mimeType\"}]}"
        createConn.outputStream.use { os ->
            os.write(createBody.toByteArray(Charsets.UTF_8))
        }

        val createCode = createConn.responseCode
        if (createCode != 200 && createCode != 201) {
            val err = createConn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
            throw IllegalStateException("Failed to create space. Code: $createCode, Msg: $err")
        }

        val createResponse = createConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        Log.d(TAG, "Space created response: $createResponse")

        val spaceId = extractStringField(createResponse, "spaceId")
            ?: throw IllegalStateException("Could not extract spaceId from bmrng.me response")

        val uploadUrl = extractStringField(createResponse, "uploadUrl")
            ?: throw IllegalStateException("Could not extract uploadUrl from bmrng.me response")

        // 2. Stream file directly to presigned S3 URL via PUT — no ByteArray in memory
        Log.d(TAG, "Streaming file to S3 presigned URL ($fileSize bytes)...")
        val uploadConn = URL(uploadUrl).openConnection() as HttpURLConnection
        uploadConn.requestMethod = "PUT"
        uploadConn.doOutput = true
        uploadConn.setRequestProperty("Content-Type", mimeType)
        uploadConn.setRequestProperty("User-Agent", "Mozilla/5.0")
        uploadConn.setFixedLengthStreamingMode(fileSize)

        openStream().use { inputStream ->
            uploadConn.outputStream.use { outputStream ->
                val buffer = ByteArray(256 * 1024) // 256 KB chunks — fast but memory-safe
                var totalWritten = 0L
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalWritten += bytesRead
                    if (fileSize > 0) onProgress(totalWritten.toFloat() / fileSize)
                }
            }
        }
        
        val uploadStatus = uploadConn.responseCode
        Log.d(TAG, "PUT upload finished with status: $uploadStatus")
        if (uploadStatus != 200 && uploadStatus != 201 && uploadStatus != 204) {
            val err = uploadConn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
            throw IllegalStateException("Failed file body upload. S3 Status: $uploadStatus, Msg: $err")
        }
        
        // 3. Register custom alphanumeric short url on various shorteners, and register directly on Firebase RTDB
        var finalCode = ""
        val targetUrlStr = "https://bmrng.me/spaceId/$spaceId"
        val encodedUrl = java.net.URLEncoder.encode(targetUrlStr, "UTF-8")

        // Phase 1: Try registering a custom 'c' + 7 digits code (for an elegant 8-character code standard)
        var customAttempts = 0
        while (customAttempts < 4) {
            val digits = (1..7).map { "0123456789".random() }.joinToString("")
            val potentialCode = "c$digits" // starts with a lowercase letter, valid on is.gd/v.gd/ulvis.net
            Log.d(TAG, "Attempt $customAttempts: Trying to register custom code $potentialCode...")
            
            // A. Try ulvis.net Custom API
            try {
                Log.d(TAG, "Trying custom register on ulvis.net for code $potentialCode...")
                val ulvisApiUrl = URL("https://ulvis.net/api.php?url=$encodedUrl&custom=$potentialCode&type=json")
                val ulvisConn = ulvisApiUrl.openConnection() as HttpURLConnection
                ulvisConn.requestMethod = "GET"
                ulvisConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                ulvisConn.connectTimeout = 5000
                ulvisConn.readTimeout = 5000
                
                if (ulvisConn.responseCode == 200) {
                    val responseBody = ulvisConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "ulvis.net API response: $responseBody")
                    val isSuccess = responseBody.contains("\"success\":true")
                    val shortUrl = extractStringField(responseBody, "url")
                    if (isSuccess && !shortUrl.isNullOrEmpty()) {
                        finalCode = potentialCode
                        Log.d(TAG, "Successfully registered on ulvis.net: $shortUrl")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ulvis.net connection error on custom attempt $customAttempts", e)
            }

            // B. Try is.gd Custom
            try {
                val isGdApiUrl = URL("https://is.gd/create.php?format=json&url=$encodedUrl&shorturl=$potentialCode")
                val isGdConn = isGdApiUrl.openConnection() as HttpURLConnection
                isGdConn.requestMethod = "GET"
                isGdConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                isGdConn.connectTimeout = 5000
                isGdConn.readTimeout = 5000
                
                val responseStatus = isGdConn.responseCode
                if (responseStatus == 200) {
                    val responseBody = isGdConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "is.gd API response: $responseBody")
                    
                    val shortUrl = extractStringField(responseBody, "shorturl")
                    val errorCodePattern = "\"errorcode\"\\s*:\\s*(\\d+)".toRegex()
                    val errCode = errorCodePattern.find(responseBody)?.groupValues?.get(1)?.toIntOrNull()
                    
                    if (shortUrl != null && errCode == null) {
                        finalCode = potentialCode
                        Log.d(TAG, "Successfully registered on is.gd: $shortUrl")
                        break
                    } else if (errCode == 2) {
                        Log.d(TAG, "Code $potentialCode already taken on is.gd, trying another...")
                    } else {
                        Log.w(TAG, "is.gd gave error $errCode on code $potentialCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "is.gd connection error on custom attempt $customAttempts", e)
            }

            // C. Try v.gd custom
            try {
                Log.d(TAG, "Trying fallback custom register on v.gd for code $potentialCode...")
                val vGdApiUrl = URL("https://v.gd/create.php?format=json&url=$encodedUrl&shorturl=$potentialCode")
                val vGdConn = vGdApiUrl.openConnection() as HttpURLConnection
                vGdConn.requestMethod = "GET"
                vGdConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                vGdConn.connectTimeout = 5000
                vGdConn.readTimeout = 5000
                
                val responseStatus = vGdConn.responseCode
                if (responseStatus == 200) {
                    val responseBody = vGdConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "v.gd API response: $responseBody")
                    
                    val shortUrl = extractStringField(responseBody, "shorturl")
                    val errorCodePattern = "\"errorcode\"\\s*:\\s*(\\d+)".toRegex()
                    val errCode = errorCodePattern.find(responseBody)?.groupValues?.get(1)?.toIntOrNull()
                    
                    if (shortUrl != null && errCode == null) {
                        finalCode = potentialCode
                        Log.d(TAG, "Successfully registered on v.gd: $shortUrl")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "v.gd connection error on custom attempt $customAttempts", e)
            }

            customAttempts++
        }

        // Phase 2: Ultimate fallbacks to auto-generated short URLs if custom registration is rejected or rate-limited
        if (finalCode.isEmpty()) {
            Log.d(TAG, "Custom registration unsuccessful. Trying auto-generated short URL on ulvis.net...")
            try {
                val ulvisApiUrl = URL("https://ulvis.net/api.php?url=$encodedUrl&type=json")
                val ulvisConn = ulvisApiUrl.openConnection() as HttpURLConnection
                ulvisConn.requestMethod = "GET"
                ulvisConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                ulvisConn.connectTimeout = 5000
                ulvisConn.readTimeout = 5000
                
                if (ulvisConn.responseCode == 200) {
                    val responseBody = ulvisConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "ulvis.net auto response: $responseBody")
                    val isSuccess = responseBody.contains("\"success\":true")
                    val shortUrl = extractStringField(responseBody, "url")
                    if (isSuccess && !shortUrl.isNullOrEmpty()) {
                        finalCode = shortUrl.substringAfterLast("/")
                        Log.d(TAG, "Successfully obtained fallback ulvis.net code: $finalCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ulvis.net fallback error", e)
            }
        }

        if (finalCode.isEmpty()) {
            Log.d(TAG, "Custom registration unsuccessful. Requesting auto-generated short URL from tinyurl.com...")
            try {
                val tinyUrlApiUrl = URL("https://tinyurl.com/api-create.php?url=$encodedUrl")
                val tinyUrlConn = tinyUrlApiUrl.openConnection() as HttpURLConnection
                tinyUrlConn.requestMethod = "GET"
                tinyUrlConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                tinyUrlConn.connectTimeout = 5000
                tinyUrlConn.readTimeout = 5000
                
                if (tinyUrlConn.responseCode == 200) {
                    val responseBody = tinyUrlConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()
                    Log.d(TAG, "tinyurl.com auto response: $responseBody")
                    if (responseBody.isNotEmpty() && responseBody.contains("tinyurl.com/")) {
                        finalCode = responseBody.substringAfterLast("/")
                        Log.d(TAG, "Successfully obtained fallback tinyurl.com code: $finalCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "tinyurl.com fallback error", e)
            }
        }

        if (finalCode.isEmpty()) {
            Log.d(TAG, "Custom registration unsuccessful. Requesting auto-generated short URL from is.gd...")
            try {
                val isGdApiUrl = URL("https://is.gd/create.php?format=json&url=$encodedUrl")
                val isGdConn = isGdApiUrl.openConnection() as HttpURLConnection
                isGdConn.requestMethod = "GET"
                isGdConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                isGdConn.connectTimeout = 5000
                isGdConn.readTimeout = 5000
                
                if (isGdConn.responseCode == 200) {
                    val responseBody = isGdConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "is.gd auto response: $responseBody")
                    val shortUrl = extractStringField(responseBody, "shorturl")
                    if (!shortUrl.isNullOrEmpty()) {
                        finalCode = shortUrl.substringAfterLast("/")
                        Log.d(TAG, "Successfully obtained fallback is.gd code: $finalCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "is.gd fallback error", e)
            }
        }

        if (finalCode.isEmpty()) {
            Log.d(TAG, "Requesting auto-generated short URL from v.gd...")
            try {
                val vGdApiUrl = URL("https://v.gd/create.php?format=json&url=$encodedUrl")
                val vGdConn = vGdApiUrl.openConnection() as HttpURLConnection
                vGdConn.requestMethod = "GET"
                vGdConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                vGdConn.connectTimeout = 5000
                vGdConn.readTimeout = 5000
                
                if (vGdConn.responseCode == 200) {
                    val responseBody = vGdConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    Log.d(TAG, "v.gd auto response: $responseBody")
                    val shortUrl = extractStringField(responseBody, "shorturl")
                    if (!shortUrl.isNullOrEmpty()) {
                        finalCode = shortUrl.substringAfterLast("/")
                        Log.d(TAG, "Successfully obtained fallback v.gd code: $finalCode")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "v.gd fallback error", e)
            }
        }

        // Phase 3: Pure programmatic offline/local fallback if all public url shorteners are blocklisting or offline
        if (finalCode.isEmpty()) {
            Log.d(TAG, "All public shorteners unavailable. Generating reliable unique 8-character client fallback mapping...")
            finalCode = "c" + (1..7).map { "0123456789".random() }.joinToString("")
        }

        // Always register directly on our own Firebase Realtime Database
        try {
            Log.d(TAG, "Registering access mapping: $finalCode -> $spaceId directly on Firebase RTDB...")
            val registerUrl = URL("$DATABASE_URL/$finalCode.json")
            val registerConn = registerUrl.openConnection() as HttpURLConnection
            registerConn.requestMethod = "PUT"
            registerConn.doOutput = true
            registerConn.setRequestProperty("Content-Type", "application/json")
            registerConn.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val registerBody = "\"$spaceId\""
            registerConn.outputStream.use { os ->
                os.write(registerBody.toByteArray(Charsets.UTF_8))
            }
            val regStatus = registerConn.responseCode
            Log.d(TAG, "Firebase RTDB direct registration returned code: $regStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Failed placing fallback mapping inside Firebase Realtime Database", e)
        }

        Log.d(TAG, "SUCCESS! Managed file sharing complete: code=$finalCode, spaceId=$spaceId")
        Pair(finalCode, spaceId)
    }

    /**
     * Upload nhiều file vào cùng 1 space trên bmrng.me.
     * Tạo 1 space duy nhất với tất cả file, upload song song, trả về 1 code duy nhất.
     */
    suspend fun uploadMultipleFiles(
        files: List<Triple<String, Long, String>>, // (filename, fileSize, mimeType)
        openStreams: List<() -> java.io.InputStream>,
        onProgress: (fileIndex: Int, progress: Float) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) throw IllegalArgumentException("No files provided")

        // 1. Tạo space với tất cả file cùng lúc
        Log.d(TAG, "Creating multi-file space for ${files.size} files...")
        val filesJson = files.joinToString(",") { (name, size, mime) ->
            "{\"filename\":\"$name\",\"size\":$size,\"contentType\":\"$mime\"}"
        }
        val createUrl = URL("https://bmrng.me/api/upload/space/create")
        val createConn = createUrl.openConnection() as HttpURLConnection
        createConn.requestMethod = "POST"
        createConn.doOutput = true
        createConn.setRequestProperty("Content-Type", "application/json")
        createConn.setRequestProperty("User-Agent", "Mozilla/5.0")

        createConn.outputStream.use { it.write("{\"files\":[$filesJson]}".toByteArray(Charsets.UTF_8)) }

        val createCode = createConn.responseCode
        if (createCode != 200 && createCode != 201) {
            val err = createConn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
            throw IllegalStateException("Failed to create multi-file space. Code: $createCode, Msg: $err")
        }

        val createResponse = createConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        Log.d(TAG, "Multi-file space response: $createResponse")

        val spaceId = extractStringField(createResponse, "spaceId")
            ?: throw IllegalStateException("Could not extract spaceId")

        // Parse tất cả uploadUrl từ response — mỗi file có uploadUrl riêng
        val uploadUrls = mutableListOf<String>()
        val urlPattern = "\"uploadUrl\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        urlPattern.findAll(createResponse).forEach { match ->
            uploadUrls.add(
                match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
            )
        }

        if (uploadUrls.size != files.size) {
            throw IllegalStateException("Expected ${files.size} upload URLs, got ${uploadUrls.size}")
        }

        // 2. Upload song song tất cả file — coroutineScope bắt buộc để dùng async bên trong withContext
        coroutineScope {
            val uploadJobs = files.indices.map { i ->
                async(Dispatchers.IO) {
                    val (filename, fileSize, mimeType) = files[i]
                    val uploadUrl = uploadUrls[i]
                    Log.d(TAG, "Uploading file[$i] $filename to S3...")

                    val uploadConn = URL(uploadUrl).openConnection() as HttpURLConnection
                    uploadConn.requestMethod = "PUT"
                    uploadConn.doOutput = true
                    uploadConn.setRequestProperty("Content-Type", mimeType)
                    uploadConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    uploadConn.setFixedLengthStreamingMode(fileSize)

                    openStreams[i]().use { inputStream ->
                        uploadConn.outputStream.use { outputStream ->
                            val buffer = ByteArray(256 * 1024)
                            var totalWritten = 0L
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalWritten += bytesRead
                                if (fileSize > 0) onProgress(i, totalWritten.toFloat() / fileSize)
                            }
                        }
                    }

                    val uploadStatus = uploadConn.responseCode
                    if (uploadStatus != 200 && uploadStatus != 201 && uploadStatus != 204) {
                        val err = uploadConn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
                        throw IllegalStateException("Failed upload for $filename. S3 Status: $uploadStatus, Msg: $err")
                    }
                    Log.d(TAG, "File[$i] $filename uploaded OK (status $uploadStatus)")
                }
            }
            uploadJobs.forEach { it.await() }
        }

        // 3. Đăng ký code giống như single-file upload
        var finalCode = ""
        val targetUrlStr = "https://bmrng.me/spaceId/$spaceId"
        val encodedUrl = java.net.URLEncoder.encode(targetUrlStr, "UTF-8")

        var customAttempts = 0
        while (customAttempts < 4 && finalCode.isEmpty()) {
            val digits = (1..7).map { "0123456789".random() }.joinToString("")
            val potentialCode = "c$digits"
            try {
                val ulvisApiUrl = URL("https://ulvis.net/api.php?url=$encodedUrl&custom=$potentialCode&type=json")
                val ulvisConn = ulvisApiUrl.openConnection() as HttpURLConnection
                ulvisConn.requestMethod = "GET"
                ulvisConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                ulvisConn.connectTimeout = 5000
                ulvisConn.readTimeout = 5000
                if (ulvisConn.responseCode == 200) {
                    val responseBody = ulvisConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    if (responseBody.contains("\"success\":true") && extractStringField(responseBody, "url") != null) {
                        finalCode = potentialCode
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "ulvis custom error attempt $customAttempts", e) }

            if (finalCode.isEmpty()) {
                try {
                    val isGdApiUrl = URL("https://is.gd/create.php?format=json&url=$encodedUrl&shorturl=$potentialCode")
                    val isGdConn = isGdApiUrl.openConnection() as HttpURLConnection
                    isGdConn.requestMethod = "GET"
                    isGdConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    isGdConn.connectTimeout = 5000
                    isGdConn.readTimeout = 5000
                    if (isGdConn.responseCode == 200) {
                        val responseBody = isGdConn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                        val shortUrl = extractStringField(responseBody, "shorturl")
                        val errCode = "\"errorcode\"\\s*:\\s*(\\d+)".toRegex().find(responseBody)?.groupValues?.get(1)?.toIntOrNull()
                        if (shortUrl != null && errCode == null) finalCode = potentialCode
                    }
                } catch (e: Exception) { Log.e(TAG, "is.gd custom error attempt $customAttempts", e) }
            }
            customAttempts++
        }

        if (finalCode.isEmpty()) {
            try {
                val ulvisApiUrl = URL("https://ulvis.net/api.php?url=$encodedUrl&type=json")
                val conn = ulvisApiUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val shortUrl = extractStringField(body, "url")
                    if (body.contains("\"success\":true") && !shortUrl.isNullOrEmpty()) finalCode = shortUrl.substringAfterLast("/")
                }
            } catch (e: Exception) { Log.e(TAG, "ulvis auto fallback error", e) }
        }

        if (finalCode.isEmpty()) {
            try {
                val url = URL("https://is.gd/create.php?format=json&url=$encodedUrl")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val shortUrl = extractStringField(body, "shorturl")
                    if (!shortUrl.isNullOrEmpty()) finalCode = shortUrl.substringAfterLast("/")
                }
            } catch (e: Exception) { Log.e(TAG, "is.gd auto fallback error", e) }
        }

        if (finalCode.isEmpty()) {
            finalCode = "c" + (1..7).map { "0123456789".random() }.joinToString("")
        }

        try {
            val registerUrl = URL("$DATABASE_URL/$finalCode.json")
            val registerConn = registerUrl.openConnection() as HttpURLConnection
            registerConn.requestMethod = "PUT"
            registerConn.doOutput = true
            registerConn.setRequestProperty("Content-Type", "application/json")
            registerConn.setRequestProperty("User-Agent", "Mozilla/5.0")
            registerConn.outputStream.use { it.write("\"$spaceId\"".toByteArray(Charsets.UTF_8)) }
            Log.d(TAG, "Firebase RTDB multi-file registration: ${registerConn.responseCode}")
        } catch (e: Exception) { Log.e(TAG, "Firebase RTDB registration error", e) }

        Log.d(TAG, "Multi-file upload complete: code=$finalCode, spaceId=$spaceId, files=${files.size}")
        Pair(finalCode, spaceId)
    }


    suspend fun resolveCodeToSpaceId(code: String): String? = withContext(Dispatchers.IO) {
        val codeClean = code.trim().replace(" ", "")
        
        // 1. Try our direct high-performance Firebase Realtime Database mapping FIRST
        try {
            Log.d(TAG, "Resolving code $codeClean directly on Firebase RTDB...")
            val lookupUrl = URL("$DATABASE_URL/$codeClean.json")
            val conn = lookupUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            if (conn.responseCode == 200) {
                val res = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.trim()
                if (res != "null" && res.isNotEmpty()) {
                    val spaceIdClean = res.replace("\"", "").trim()
                    Log.d(TAG, "Resolved spaceId $spaceIdClean directly from Firebase RTDB")
                    return@withContext spaceIdClean
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Err resolving directly on Firebase RTDB", e)
        }

        // 2. Try resolving via ulvis.net redirect
        try {
            Log.d(TAG, "Resolving code $codeClean on ulvis.net...")
            val shortUrlStr = "https://ulvis.net/$codeClean"
            val conn = URL(shortUrlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            
            val status = conn.responseCode
            if (status in 300..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrEmpty() && location.contains("/spaceId/")) {
                    val spaceId = location.substringAfter("/spaceId/").trim()
                    Log.d(TAG, "Resolved spaceId $spaceId from ulvis.net redirect")
                    return@withContext spaceId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Err resolving on ulvis.net", e)
        }

        // 3. Try resolving via tinyurl.com redirect
        try {
            Log.d(TAG, "Resolving code $codeClean on tinyurl.com...")
            val shortUrlStr = "https://tinyurl.com/$codeClean"
            val conn = URL(shortUrlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            
            val status = conn.responseCode
            if (status in 300..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrEmpty() && location.contains("/spaceId/")) {
                    val spaceId = location.substringAfter("/spaceId/").trim()
                    Log.d(TAG, "Resolved spaceId $spaceId from tinyurl.com redirect")
                    return@withContext spaceId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Err resolving on tinyurl.com", e)
        }

        // 4. Try resolving via is.gd
        try {
            Log.d(TAG, "Resolving code $codeClean on is.gd...")
            val shortUrlStr = "https://is.gd/$codeClean"
            val conn = URL(shortUrlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            
            val status = conn.responseCode
            if (status in 300..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrEmpty() && location.contains("/spaceId/")) {
                    val spaceId = location.substringAfter("/spaceId/").trim()
                    Log.d(TAG, "Resolved spaceId $spaceId from is.gd redirect")
                    return@withContext spaceId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Err resolving on is.gd", e)
        }

        // 5. Try resolving via v.gd
        try {
            Log.d(TAG, "Resolving code $codeClean on v.gd...")
            val shortUrlStr = "https://v.gd/$codeClean"
            val conn = URL(shortUrlStr).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            
            val status = conn.responseCode
            if (status in 300..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrEmpty() && location.contains("/spaceId/")) {
                    val spaceId = location.substringAfter("/spaceId/").trim()
                    Log.d(TAG, "Resolved spaceId $spaceId from v.gd redirect")
                    return@withContext spaceId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Err resolving on v.gd", e)
        }

        null
    }

    /**
     * Resolves spaceId and loads available file lists from bmrng.me.
     */
    suspend fun getSpaceDetails(spaceId: String): List<BmrngFile> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching space details for space $spaceId...")
        val spaceUrl = URL("https://bmrng.me/api/upload/space/$spaceId")
        val conn = spaceUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        
        val code = conn.responseCode
        if (code == 200) {
            val raw = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            Log.d(TAG, "Space details obtained successfully: $raw")
            return@withContext parseFilesList(raw)
        } else {
            val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8)
            Log.e(TAG, "Get Space returned code: $code, Msg: $err")
            throw IllegalStateException("Space ID not active or expired.")
        }
    }

    /**
     * Downloads file contents from target bmrng file download URL.
     */
    suspend fun downloadFileBytes(downloadUrl: String): ByteArray = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading file content bytes from $downloadUrl...")
        val conn = URL(downloadUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        
        val status = conn.responseCode
        if (status == 200) {
            return@withContext conn.inputStream.use { it.readBytes() }
        } else {
            throw IllegalStateException("Fetch error. Download response: $status")
        }
    }

    /**
     * Downloads file contents from target bmrng file download URL and writes it directly to the target file
     * with progress updates.
     */
    suspend fun downloadFileToLocalFile(
        downloadUrl: String,
        destinationFile: java.io.File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading and saving from $downloadUrl to ${destinationFile.absolutePath}...")
        val conn = URL(downloadUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        
        val status = conn.responseCode
        if (status == 200) {
            val contentLength = conn.contentLength.toLong()
            val inputStream = conn.inputStream
            java.io.FileOutputStream(destinationFile).use { fileOut ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fileOut.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        onProgress(totalBytesRead.toFloat() / contentLength)
                    } else {
                        onProgress(-1f)
                    }
                }
            }
            onProgress(1f)
            Log.d(TAG, "Download completed successfully: ${destinationFile.absolutePath}")
        } else {
            throw java.lang.IllegalStateException("Fetch error. Download response: $status")
        }
    }
}
