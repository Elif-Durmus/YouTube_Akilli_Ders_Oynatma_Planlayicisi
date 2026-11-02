package com.example.myapplication

import java.io.Serializable

// Bir günün çalışma verisi
data class GunlukPlan(
    val gunNumarasi: Int,
    val toplamSureMetni: String,
    val dersler: ArrayList<Lecture>
) : Serializable

// Bir haftanın günlerini toplayan veri modeli
data class HaftalikPlan(
    val haftaNumarasi: Int,
    val gunler: ArrayList<GunlukPlan>
) : Serializable