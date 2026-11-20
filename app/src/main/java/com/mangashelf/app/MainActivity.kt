package com.mangashelf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangashelf.app.engine.CbzDownloadEngine
import com.mangashelf.app.model.ChapterItem
import com.mangashelf.app.model.MangaItem
import com.mangashelf.app.source.MangaConnector
import com.mangashelf.app.source.MangaDexConnector
import com.mangashelf.app.source.WebViewScraperConnector
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val engine = CbzDownloadEngine(client)

        setContent {
            val connectors = remember {
                mutableStateListOf<MangaConnector>(
                    MangaDexConnector(client),
                    WebViewScraperConnector(this, "yaoiscan", "YaoiScan", "https://yaoiscan.com")
                )
            }
            var activeConnector by remember { mutableStateOf(connectors.first()) }
            var siteInput by remember { mutableStateOf("") }
            var queryInput by remember { mutableStateOf("") }
            var statusMsg by remember { mutableStateOf("Бэлэн. Сайт сонгоод манга хайх эсвэл URL оруулна уу.") }
            var mangaList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
            var selectedManga by remember { mutableStateOf<MangaItem?>(null) }
            var chapters by remember { mutableStateOf<List<ChapterItem>>(emptyList()) }
            var isBusy by remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope()

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B0711),
                    surface = Color(0xFF160F24),
                    primary = Color(0xFF8B5CF6)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0711))
                        .padding(12.dp)
                ) {
                    Text("MangaShelf", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(statusMsg, fontSize = 13.sp, color = Color(0xFFB59FF7), modifier = Modifier.padding(vertical = 6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(connectors) { conn ->
                            FilterChip(
                                selected = activeConnector.id == conn.id,
                                onClick = {
                                    activeConnector = conn
                                    statusMsg = "${conn.name} сонгогдлоо."
                                },
                                label = { Text(conn.name) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = siteInput,
                            onValueChange = { siteInput = it },
                            placeholder = { Text("Шинэ сайт URL (https://...)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val raw = siteInput.trim()
                            if (raw.isNotEmpty()) {
                                val url = if (raw.startsWith("http")) raw else "https://$raw"
                                val host = url.removePrefix("https://").removePrefix("http://").substringBefore("/")
                                val newConn = WebViewScraperConnector(this@MainActivity, host, host, url)
                                connectors.add(newConn)
                                activeConnector = newConn
                                siteInput = ""
                                statusMsg = "$host нэмэгдлээ."
                            }
                        }) { Text("Сайт нэмэх") }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = { queryInput = it },
                            placeholder = { Text("Манга нэр эсвэл Бүлгийн URL...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(enabled = !isBusy, onClick = {
                            scope.launch {
                                isBusy = true
                                statusMsg = "Хайж/Уншиж байна..."
                                try {
                                    mangaList = activeConnector.search(queryInput.trim())
                                    chapters = emptyList()
                                    statusMsg = "${mangaList.size} илэрц олдлоо."
                                } catch (e: Exception) {
                                    statusMsg = "Алдаа: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        }) { Text("Хайх") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (mangaList.isNotEmpty() && chapters.isEmpty()) {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(mangaList) { m ->
                                Surface(
                                    color = Color(0xFF1D1330),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            isBusy = true
                                            selectedManga = m
                                            statusMsg = "${m.title} бүлгүүдийг ачааллаж байна..."
                                            try {
                                                chapters = activeConnector.getChapters(m)
                                                statusMsg = "${chapters.size} бүлэг олдлоо."
                                            } catch (e: Exception) {
                                                statusMsg = "Алдаа: ${e.message}"
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    }
                                ) {
                                    Text(m.title, color = Color.White, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = {
                                chapters = chapters.map { it.copy(isSelected = true) }
                            }) { Text("Бүгд") }

                            OutlinedButton(onClick = {
                                chapters = emptyList()
                            }) { Text("Буцах") }

                            Button(enabled = !isBusy, onClick = {
                                scope.launch {
                                    isBusy = true
                                    val targets = chapters.filter { it.isSelected }
                                    for ((idx, ch) in targets.withIndex()) {
                                        try {
                                            statusMsg = "[${idx + 1}/${targets.size}] Ch.${ch.number} хуудас уншиж байна..."
                                            val pages = activeConnector.getPageUrls(ch)
                                            val saved = engine.downloadAsCbz(
                                                mangaTitle = selectedManga?.title ?: "Manga",
                                                chapterNumber = ch.number,
                                                pageUrls = pages,
                                                referer = activeConnector.baseUrl
                                            ) { prog ->
                                                statusMsg = "[${idx + 1}/${targets.size}] Ch.${ch.number}: $prog"
                                            }
                                            chapters = chapters.map {
                                                if (it.id == ch.id) it.copy(status = "Дууссан ✓") else it
                                            }
                                            statusMsg = "Хадгалагдлаа: ${saved.name}"
                                        } catch (e: Exception) {
                                            chapters = chapters.map {
                                                if (it.id == ch.id) it.copy(status = "Алдаа") else it
                                            }
                                            statusMsg = "Ch.${ch.number} алдаа: ${e.message}"
                                        }
                                    }
                                    isBusy = false
                                }
                            }) { Text("CBZ Татах") }
                        }

                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(chapters) { ch ->
                                Surface(
                                    color = Color(0xFF1D1330),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        chapters = chapters.map {
                                            if (it.id == ch.id) it.copy(isSelected = !it.isSelected) else it
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Checkbox(
                                                checked = ch.isSelected,
                                                onCheckedChange = { checked ->
                                                    chapters = chapters.map {
                                                        if (it.id == ch.id) it.copy(isSelected = checked) else it
                                                    }
                                                }
                                            )
                                            Text("Ch. ${ch.number} ${ch.title}", color = Color.White, maxLines = 1)
                                        }
                                        Text(ch.status, color = Color(0xFF34D399), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
