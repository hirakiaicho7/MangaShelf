package com.mangashelf.app.source

import com.mangashelf.app.model.ChapterItem
import com.mangashelf.app.model.MangaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class MangaDexConnector(private val client: OkHttpClient) : MangaConnector {
    override val id = "mangadex"
    override val name = "MangaDex"
    override val baseUrl = "https://api.mangadex.org"

    override suspend fun search(query: String): List<MangaItem> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga?title=${URLEncoder.encode(query, "UTF-8")}&limit=20"
        val res = client.newCall(Request.Builder().url(url).build()).execute()
        val json = JSONObject(res.body?.string() ?: return@withContext emptyList())
        val data = json.getJSONArray("data")
        val list = mutableListOf<MangaItem>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val id = item.getString("id")
            val titles = item.getJSONObject("attributes").getJSONObject("title")
            val title = titles.optString("en", titles.optString(titles.keys().next(), "Unknown"))
            list.add(MangaItem(id, title, "https://mangadex.org/title/$id", this@MangaDexConnector.id))
        }
        list
    }

    override suspend fun getChapters(manga: MangaItem): List<ChapterItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChapterItem>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val url = "$baseUrl/manga/${manga.id}/feed?translatedLanguage[]=en&order[chapter]=asc&limit=100&offset=$offset"
            val res = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(res.body?.string() ?: break)
            total = json.optInt("total", 0)
            val data = json.getJSONArray("data")
            if (data.length() == 0) break
            for (i in 0 until data.length()) {
                val ch = data.getJSONObject(i)
                val attr = ch.getJSONObject("attributes")
                val num = attr.optString("chapter", "0")
                val title = attr.optString("title", "")
                list.add(ChapterItem(ch.getString("id"), num, title, "", id))
            }
            offset += data.length()
        }
        list
    }

    override suspend fun getPageUrls(chapter: ChapterItem): List<String> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/at-home/server/${chapter.id}"
        val res = client.newCall(Request.Builder().url(url).build()).execute()
        val json = JSONObject(res.body?.string() ?: return@withContext emptyList())
        val host = json.getString("baseUrl")
        val chObj = json.getJSONObject("chapter")
        val hash = chObj.getString("hash")
        val files = chObj.getJSONArray("data")
        List(files.length()) { i -> "$host/data/$hash/${files.getString(i)}" }
    }
}
