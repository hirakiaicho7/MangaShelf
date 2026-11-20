package com.mangashelf.app.model

data class MangaItem(
    val id: String,
    val title: String,
    val url: String,
    val sourceId: String
)

data class ChapterItem(
    val id: String,
    val number: String,
    val title: String,
    val url: String,
    val sourceId: String,
    var isSelected: Boolean = false,
    var status: String = "Бэлэн"
)
