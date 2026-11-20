package com.mangashelf.app.engine

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CbzDownloadEngine(private val client: OkHttpClient) {

    suspend fun downloadAsCbz(
        mangaTitle: String,
        chapterNumber: String,
        pageUrls: List<String>,
        referer: String,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (pageUrls.isEmpty()) throw IllegalStateException("Зургийн холбоос олдсонгүй")

        val safeManga = mangaTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Manga" }
        val safeCh = chapterNumber.padStart(3, '0')
        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MangaShelf/$safeManga"
        )
        if (!baseDir.exists()) baseDir.mkdirs()

        val outFile = File(baseDir, "${safeManga}_Chapter_${safeCh}.cbz")
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            pageUrls.forEachIndexed { idx, url ->
                onProgress("${idx + 1}/${pageUrls.size} хуудас...")
                val req = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code} (Хуудас ${idx + 1})")
                    val bytes = res.body?.bytes() ?: throw Exception("Хоосон зураг")
                    val ext = url.substringAfterLast('.').substringBefore('?').lowercase()
                        .let { if (it in listOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
                    val name = String.format("%03d.%s", idx + 1, ext)
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        outFile
    }
}
