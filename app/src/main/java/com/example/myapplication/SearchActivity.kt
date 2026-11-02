package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Ayrı arama ekranı - tüm dersler arasında canlı filtreleme yapıyor
class SearchActivity : AppCompatActivity() {

    private lateinit var searchViewPage: SearchView
    private lateinit var recyclerViewSearchResults: RecyclerView
    private lateinit var btnBack: ImageButton

    private val allLectures = ArrayList<Lecture>()
    private val filteredLectures = ArrayList<Lecture>()
    private lateinit var adapter: LectureAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        btnBack = findViewById(R.id.btnBack)
        searchViewPage = findViewById(R.id.searchViewPage)
        recyclerViewSearchResults = findViewById(R.id.recyclerViewSearchResults)

        btnBack.setOnClickListener { finish() }

        // MainActivity den gönderilen ders listesini alıyor
        val passedLectures = intent.getStringArrayListExtra("LECTURE_TITLES") ?: arrayListOf()
        val passedIds = intent.getStringArrayListExtra("LECTURE_IDS") ?: arrayListOf()
        val passedDurations = intent.getStringArrayListExtra("LECTURE_DURATIONS") ?: arrayListOf()
        val passedThumbs = intent.getStringArrayListExtra("LECTURE_THUMBS") ?: arrayListOf()

        for (i in 0 until aktarilanBaslikSayisi(passedLectures.size, passedIds.size)) {
            allLectures.add(
                Lecture(
                    id = passedIds[i],
                    title = passedLectures[i],
                    duration = if (i < passedDurations.size) passedDurations[i] else "",
                    durationSeconds = 0,
                    youtubeVideoId = passedIds[i],
                    thumbnailUrl = if (i < passedThumbs.size) passedThumbs[i] else ""
                )
            )
        }
        filteredLectures.addAll(allLectures)

        // Listeyi bağla
        recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)
        adapter = LectureAdapter(filteredLectures) { clickedLecture ->
            // Seçilen dersi MainActivitye geri döndürüp ekranı kapatıyor
            val resultIntent = Intent().apply {
                putExtra("SELECTED_VIDEO_ID", clickedLecture.youtubeVideoId)
                putExtra("SELECTED_TITLE", clickedLecture.title)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        recyclerViewSearchResults.adapter = adapter

        // Arama kutusuna yazıldıkça filtreleme yap
        searchViewPage.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText.orEmpty().lowercase().trim()
                filteredLectures.clear()
                if (query.isEmpty()) {
                    filteredLectures.addAll(allLectures)
                } else {
                    for (lec in allLectures) {
                        if (lec.title.lowercase().contains(query)) {
                            filteredLectures.add(lec)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                return true
            }
        })
    }

    private fun aktarilanBaslikSayisi(size1: Int, size2: Int): Int = if (size1 < size2) size1 else size2
}