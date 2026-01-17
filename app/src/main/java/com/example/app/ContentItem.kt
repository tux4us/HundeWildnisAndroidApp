package com.hundewildnis.app

data class ContentItem(
    val title: String,
    val url: String,
    val excerpt: String = "",
    val date: String = "",
    val level: Int = 0,
    val type: String = "blog",
    val imageUrl: String = "",
    val id: Int = 0
)
