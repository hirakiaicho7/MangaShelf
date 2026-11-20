package com.mangashelf.app.source

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mangashelf.app.model.ChapterItem
import com.mangashelf.app.model.MangaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import kotlin.coroutines.resume

class WebViewScraperConnector(
    private val context: Context,
    override val id: String,
    override val name: String,
    override val baseUrl: String
) : MangaConnector {

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun evalJsOnPage(targetUrl: String, jsScript: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.postDelayed({
                        view.evaluateJavascript(jsScript) { result ->
                            webView.destroy()
                            if (cont.isActive) cont.resume(result ?: "[]")
                        }
                    }, 2500)
                }
            }
            webView.loadUrl(targetUrl)
            cont.invokeOnCancellation { webView.destroy() }
        }
    }

    private fun unescapeJsonArray(raw: String): JSONArray {
        val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        return try { JSONArray(cleaned) } catch (_: Exception) { JSONArray() }
    }

    override suspend fun search(query: String): List<MangaItem> {
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return listOf(MangaItem(query, "URL: ${query.substringAfterLast('/')}", query, id))
        }
        val searchUrl = "${baseUrl.trimEnd('/')}/?s=${URLEncoder.encode(query, "UTF-8")}"
        val js = """
            (function() {
                const out = [];
                const seen = new Set();
                document.querySelectorAll('a[href]').forEach(a => {
                    const href = a.href;
                    const txt = (a.innerText || a.title || '').trim();
                    if (txt.length > 2 && !seen.has(href) && /\/(manga|series|comic|read|webtoon)\//i.test(href)) {
                        seen.add(href);
                        out.push({ title: txt.split('\n')[0], url: href });
                    }
                });
                return JSON.stringify(out.slice(0, 25));
            })();
        """.trimIndent()

        val arr = unescapeJsonArray(evalJsOnPage(searchUrl, js))
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val u = obj.getString("url")
            MangaItem(u, obj.getString("title"), u, id)
        }
    }

    override suspend fun getChapters(manga: MangaItem): List<ChapterItem> {
        if (manga.url.contains("chapter", ignoreCase = true)) {
            return listOf(ChapterItem(manga.url, "001", manga.title, manga.url, id))
        }
        val js = """
            (function() {
                const out = [];
                const seen = new Set();
                document.querySelectorAll('a[href]').forEach(a => {
                    const href = a.href;
                    const txt = (a.innerText || '').trim();
                    if (!seen.has(href) && (/chapter/i.test(href) || /chapter/i.test(txt))) {
                        seen.add(href);
                        out.push({ title: txt.split('\n')[0] || 'Chapter', url: href });
                    }
                });
                return JSON.stringify(out.reverse());
            })();
        """.trimIndent()

        val arr = unescapeJsonArray(evalJsOnPage(manga.url, js))
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            val u = obj.getString("url")
            ChapterItem(u, (i + 1).toString().padStart(3, '0'), obj.getString("title"), u, id)
        }
    }

    override suspend fun getPageUrls(chapter: ChapterItem): List<String> {
        val js = """
            (function() {
                const imgs = [];
                document.querySelectorAll('img').forEach(img => {
                    const src = img.currentSrc || img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || img.src;
                    if (src && src.startsWith('http') && (img.naturalWidth === 0 || img.naturalWidth > 250)) {
                        imgs.push(src);
                    }
                });
                return JSON.stringify([...new Set(imgs)]);
            })();
        """.trimIndent()

        val arr = unescapeJsonArray(evalJsOnPage(chapter.url, js))
        return List(arr.length()) { i -> arr.getString(i) }
    }
}
