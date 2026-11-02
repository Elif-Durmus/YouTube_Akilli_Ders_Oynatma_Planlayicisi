package com.example.myapplication

import java.io.Serializable

data class Lecture(
    val id: String,
    val title: String,
    val duration: String,
    val durationSeconds: Int,
    val youtubeVideoId: String,
    val thumbnailUrl: String
) : Serializable


