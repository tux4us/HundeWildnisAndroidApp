package com.hundewildnis.app

data class ContentItem(
    val title: String,
    val url: String,
    val excerpt: String = "",
    val date: String = "",
    val level: Int = 0,
    val type: String = "blog",
    val imageUrl: String = "",
    val id: Int = 0,
    val parentId: Int = 0,  // NEU
    val menuOrder: Int = 0  // NEU: Für WordPress Menu-Reihenfolge
)

data class BlogCategory(
    val id: Int,
    val name: String,
    val count: Int
)
