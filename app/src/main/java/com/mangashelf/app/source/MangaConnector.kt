package com.mangashelf.app.source

import com.mangashelf.app.model.ChapterItem
import com.mangashelf.app.model.MangaItem

interface MangaConnector {
    val id: String
    val name: String
    val baseUrl: String

    suspend fun search(query: String): List<MangaItem>
    suspend fun getChapters(manga: MangaItem): List<ChapterItem>
    suspend fun getPageUrls(chapter: ChapterItem): List<String>
}
