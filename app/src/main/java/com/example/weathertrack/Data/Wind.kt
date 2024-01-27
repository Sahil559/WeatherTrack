package com.example.weathertrack.Data

import java.io.Serializable

data class Wind(
    val speed: Double,
    val deg: Int
) : Serializable