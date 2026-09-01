package com.hundewildnis.app

import com.google.gson.annotations.SerializedName

data class WeatherFavorite(
    @SerializedName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerializedName("name") val name: String = "",
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lon") val lon: Double = 0.0,
    @SerializedName("isCurrentLocation") val isCurrentLocation: Boolean = false
)
