package com.example.mycarapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class CoverManager(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
    }

    fun getLocalCoverUri(albumId: String, remoteCoverUrl: String?): Uri {
        return getCoverUriInternal(albumId, remoteCoverUrl, useFileProvider = true)
    }

    private fun getCoverUriInternal(albumId: String, remoteCoverUrl: String?, useFileProvider: Boolean): Uri {
        return if (remoteCoverUrl != null) {
            val localFile = getCoverFile(albumId)

            Log.d(
                "CoverManager",
                "Checking local cover - File: ${localFile.absolutePath}, Exists: ${localFile.exists()}"
            )

            if (localFile.exists() && localFile.length() > 0) {
                if (useFileProvider) {
                    getFileProviderUri(localFile, remoteCoverUrl)
                } else {
                    Uri.fromFile(localFile)
                }
            } else {
                // Jeśli plik nie istnieje, użyj URL zdalnego i rozpocznij pobieranie
                downloadAndSaveCover(albumId, remoteCoverUrl)
                remoteCoverUrl.toUri()
            }
        } else {
            getDefaultLocalCoverUri()
        }
    }

    private fun getCoverFile(albumId: String): File {
        return File(getCoverStorageDir(), "${albumId}.jpg")
    }

    private fun getFileProviderUri(localFile: File, fallbackRemoteUrl: String): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            ).also { uri ->
                Log.d("CoverManager", "FileProvider URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(
                "CoverManager",
                "FileProvider error: ${e.message}, falling back to remote URL"
            )
            fallbackRemoteUrl.toUri()
        }
    }

    fun getCoverStorageDir(): File {
        val dir = File(context.getExternalFilesDir(null), COVERS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getDefaultLocalCoverUri(): Uri {
        val defaultFile = File(getCoverStorageDir(), "default_cover.jpg")
        return if (defaultFile.exists()) {
            Uri.fromFile(defaultFile)
        } else {
            // Fallback do zdalnego domyślnego obrazka
            "https://i.pinimg.com/736x/1e/1e/fc/1e1efcc0e4005e2b93d321b9a69a6899.jpg".toUri()
        }
    }

    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    fun downloadAndSaveCover(albumId: String, remoteCoverUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(
                    "CoverManager",
                    "Starting cover download for album: $albumId from: $remoteCoverUrl"
                )

                val url = URL(remoteCoverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                // Sprawdź czy to obrazek
                val contentType = connection.contentType
                if (!contentType.startsWith("image/")) {
                    Log.e("CoverManager", "Invalid content type: $contentType for album: $albumId")
                    return@launch
                }

                downloadAndProcessImage(albumId, connection)
            } catch (e: Exception) {
                Log.e(
                    "CoverManager",
                    "Failed to download cover for album $albumId: ${e.message}",
                    e
                )
            }
        }
    }

    private fun downloadAndProcessImage(albumId: String, connection: HttpURLConnection) {
        val inputStream = connection.inputStream
        val coverFile = getCoverFile(albumId)
        val tempFile = File(getCoverStorageDir(), "${albumId}.tmp")

        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }

            if (tempFile.renameTo(coverFile)) {
                val fileSize = coverFile.length()
                Log.d(
                    "CoverManager",
                    "Cover downloaded successfully - Album: $albumId, Size: $fileSize bytes"
                )

                // Ustaw uprawnienia
                setFilePermissions(coverFile)
            } else {
                Log.e("CoverManager", "Failed to rename temp file for album: $albumId")
            }
        } finally {
            inputStream.close()
        }
    }

    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    private fun setFilePermissions(file: File) {
        file.setReadable(true, false)
        file.setWritable(true, false)
        Log.d("CoverManager", "File permissions set - Readable: ${file.canRead()}, Writable: ${file.canWrite()}")
    }

    fun cleanupCorruptedFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val coverDir = getCoverStorageDir()
                val files = coverDir.listFiles()

                files?.forEach { file ->
                    if (file.length() == 0L) {
                        file.delete()
                        Log.d("CoverManager", "Deleted corrupted file: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("CoverManager", "Error cleaning up files: ${e.message}")
            }
        }
    }

    // Dodatkowa metoda pomocnicza do sprawdzania istnienia pliku
    fun hasLocalCover(albumId: String): Boolean {
        val file = getCoverFile(albumId)
        return file.exists() && file.length() > 0
    }

    // Metoda do uzyskania ścieżki pliku
    fun getCoverFilePath(albumId: String): String {
        return getCoverFile(albumId).absolutePath
    }
}