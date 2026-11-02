package com.example.myapplication

// Oynatma listelerinin bilgilerini tutan veri modeli
data class Course(
    val key: String,

    val title: String,
    val playlistId: String,
    val description: String,
    val thumbnailUrl: String
)
