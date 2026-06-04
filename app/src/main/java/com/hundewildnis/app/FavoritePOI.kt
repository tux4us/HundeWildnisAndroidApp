// FavoritePOI.kt
package com.hundewildnis.app

import com.google.gson.annotations.SerializedName

data class FavoritePOI(
    @SerializedName("id")
    val id: String = java.util.UUID.randomUUID().toString(),

    @SerializedName("name")
    val name: String,

    @SerializedName("customName")
    val customName: String? = null,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lon")
    val lon: Double,

    @SerializedName("categoryIcon")
    val categoryIcon: String = "📍",

    @SerializedName("address")
    val address: String? = null,

    @SerializedName("savedAt")
    val savedAt: Long = System.currentTimeMillis(),

    @SerializedName("sortOrder")
    val sortOrder: Int = 0
) {
    /** Zeigt customName wenn gesetzt, sonst Originalname */
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: name
}