package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.content.Intent
import android.os.CountDownTimer
import android.text.InputType
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkError
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import android.content.Context

class MainActivity : AppCompatActivity() {

    private lateinit var webViewPlayer: WebView
    private lateinit var tvActiveTitle: TextView
    private lateinit var tvRemainingTime: TextView
    private lateinit var btnProgramAksiyon: Button
    private lateinit var btnOpenSchedule: Button
    private lateinit var searchViewCourses: SearchView
    private lateinit var recyclerViewLectures: RecyclerView

    private lateinit var recyclerViewCourses: RecyclerView
    private val courseList = ArrayList<Course>()
    private lateinit var courseAdapter: CourseAdapter

    private val fullLectureList = ArrayList<Lecture>()
    private val displayedLectureList = ArrayList<Lecture>()
    private lateinit var adapter: LectureAdapter

    private var studyTimer: CountDownTimer? = null
    private var remainingTimeSeconds: Long = 0L
    private var currentPlayingLecture: Lecture? = null
    private var currentVideoTimeSeconds: Long = 0L

    private var totalTargetMinutes: Long = 0L
    private var currentCourseName: String = "Algoritmalar"
    private var currentCourseKey: String = "algorithms"

    private var isTimerRunning: Boolean = false

    // ngrok linkin değiştiyse burayı güncelleyebilirsin
    private val BACKEND_URL = "https://observing-provoke-greedless.ngrok-free.dev/api/lectures"


    inner class YouTubeWebAppInterface {
        @JavascriptInterface
        fun zamanGuncellendiginde(seconds: Float) {
            currentVideoTimeSeconds = seconds.toLong()
        }

        @JavascriptInterface
        fun videoOynatilmayaBasladi() {
            runOnUiThread {
                if (remainingTimeSeconds > 0L && !isTimerRunning) {
                    sayaciDevamEttir()
                }
            }
        }

        @JavascriptInterface
        fun videoDuraklatildi() {
            runOnUiThread {
                if (isTimerRunning) {
                    sayaciDuraklat()
                }
            }
        }
    }
    // Kalan saniyeyi 'Kalan: 1 sa 15 dk 20 sn' veya 'Kalan: 45 dk 10 sn' yapar
    private fun saniyeyiSayacaCevir(toplamSaniye: Long): String {
        val sa = toplamSaniye / 3600
        val dk = (toplamSaniye % 3600) / 60
        val sn = toplamSaniye % 60
        return if (sa > 0) {
            String.format("Kalan: %d sa %02d dk %02d sn", sa, dk, sn)
        } else {
            String.format("Kalan: %02d dk %02d sn", dk, sn)
        }
    }

    // Dakikayı '1 sa 20 dk' metnine çevirir
    private fun dakikayiMetneCevir(dakika: Long): String {
        val sa = dakika / 60
        val dk = dakika % 60
        return when {
            sa > 0 && dk > 0L -> "$sa sa $dk dk"
            sa > 0 && dk == 0L -> "$sa sa"
            else -> "$dk dk"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewPlayer = findViewById(R.id.webViewPlayer)
        tvActiveTitle = findViewById(R.id.tvActiveTitle)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        btnProgramAksiyon = findViewById(R.id.btnSetPlan)
        btnOpenSchedule = findViewById(R.id.btnOpenSchedule)
        searchViewCourses = findViewById(R.id.searchViewCourses)
        recyclerViewLectures = findViewById(R.id.recyclerViewLectures)

        val btnSpeed1x = findViewById<Button>(R.id.btnSpeed1x)
        val btnSpeed15x = findViewById<Button>(R.id.btnSpeed15x)
        val btnSpeed2x = findViewById<Button>(R.id.btnSpeed2x)

        webViewPlayer.settings.javaScriptEnabled = true
        webViewPlayer.settings.domStorageEnabled = true
        webViewPlayer.settings.mediaPlaybackRequiresUserGesture = false
        webViewPlayer.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        webViewPlayer.webChromeClient = WebChromeClient()
        webViewPlayer.addJavascriptInterface(YouTubeWebAppInterface(), "AndroidBridge")

        recyclerViewLectures.layoutManager = LinearLayoutManager(this)
        adapter = LectureAdapter(displayedLectureList) { selectedLecture ->
            derseTiklandiginda(selectedLecture)
        }
        recyclerViewLectures.adapter = adapter

        recyclerViewCourses = findViewById(R.id.recyclerViewCourses)
        recyclerViewCourses.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        courseAdapter = CourseAdapter(courseList) { selectedCourse ->
            currentCourseName = selectedCourse.title
            currentCourseKey = selectedCourse.key
            dersleriGetir(selectedCourse.key, refresh = false)
        }
        recyclerViewCourses.adapter = courseAdapter

        btnSpeed1x.setOnClickListener { oynatmaHiziniAyarla(1.0) }
        btnSpeed15x.setOnClickListener { oynatmaHiziniAyarla(1.5) }
        btnSpeed2x.setOnClickListener { oynatmaHiziniAyarla(2.0) }

        btnOpenSchedule.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            intent.putExtra("mevcutDersler", fullLectureList)
            startActivity(intent)
        }

        // Uzun basıldığında YouTube'dan taze veriyi çekip listeyi günceller (Hocaya gösterim için)
        btnOpenSchedule.setOnLongClickListener {
            Toast.makeText(this, "Güncel veriler YouTube'dan çekiliyor...", Toast.LENGTH_SHORT)
                .show()
            dersleriGetir(currentCourseKey, refresh = true)
            true
        }

        btnProgramAksiyon.setOnClickListener {
            if (isTimerRunning) {
                // Çalışıyorsa Duraklat
                sayaciDuraklat()
            } else if (remainingTimeSeconds > 0L) {
                // Duraklatılmışsa Kaldığı Yerden Devam Et
                sayaciDevamEttir()
            } else {
                // Sıfırdan Başlat veya Kayıtlı Seansı Devam Ettir
                val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
                val hasSaved = prefs.getBoolean("HAS_SAVED_SESSION", false)
                val savedRemainingSec = prefs.getLong("REMAINING_SECONDS", 0L)

                if (hasSaved && savedRemainingSec > 0L) {
                    val totalMinutes = prefs.getLong("TOTAL_TARGET_MINUTES", 0L)
                    val videoId = prefs.getString("SAVED_VIDEO_ID", "") ?: ""
                    val videoTitle = prefs.getString("SAVED_VIDEO_TITLE", "") ?: ""
                    val videoSec = prefs.getLong("SAVED_VIDEO_SECONDS", 0L)

                    totalTargetMinutes = totalMinutes
                    kalanSuredenCalismaPlaniniBaslat(savedRemainingSec)

                    if (videoId.isNotEmpty()) {
                        tvActiveTitle.text = videoTitle
                        currentVideoTimeSeconds = videoSec
                        currentPlayingLecture =
                            fullLectureList.find { it.youtubeVideoId == videoId }
                                ?: Lecture("0", videoTitle, "", 0, videoId, "")
                        videoyuYukle(videoId, videoSec)
                    }
                    Toast.makeText(this, "Kaldığınız yerden devam ediliyor!", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
                    val gunlukDk = sp.getLong("gunlukDk", 0L)

                    if (gunlukDk <= 0L) {
                        val intent = Intent(this, ScheduleActivity::class.java)
                        intent.putExtra("mevcutDersler", fullLectureList)
                        startActivity(intent)
                    } else {
                        totalTargetMinutes = gunlukDk
                        calismaPlaniniBaslat(gunlukDk)
                        val formatli = dakikayiMetneCevir(gunlukDk)
                        Toast.makeText(
                            this,
                            "Günlük $formatli çalışma süresi başlatıldı!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        aramaFiltresiniAyarla()
        oynatmaListeleriniGetir()
        dersleriGetir(currentCourseKey, refresh = false)
        kaydedilenIlerlemeyiKontrolEt()
    }

    override fun onResume() {
        super.onResume()
        programDurumunuGuncelle()
    }

    private fun sayaciDuraklat() {
        studyTimer?.cancel()
        isTimerRunning = false
        btnProgramAksiyon.text = "▶️ Devam Et"
        videoyuDuraklat()
        kaydetDurumu()
        Toast.makeText(this, "Çalışma duraklatıldı.", Toast.LENGTH_SHORT).show()
    }

    private fun sayaciDevamEttir() {
        kalanSuredenCalismaPlaniniBaslat(remainingTimeSeconds)
        videoyuOynat()
        Toast.makeText(this, "Çalışmaya devam ediliyor.", Toast.LENGTH_SHORT).show()
    }

    private fun programDurumunuGuncelle() {
        if (isTimerRunning) {
            btnProgramAksiyon.text = "⏸️ Çalışmayı Duraklat"
            tvRemainingTime.text = saniyeyiSayacaCevir(remainingTimeSeconds)
            return
        }

        if (remainingTimeSeconds > 0L) {
            btnProgramAksiyon.text = "▶️ Devam Et"
            tvRemainingTime.text = saniyeyiSayacaCevir(remainingTimeSeconds)
            return
        }

        val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
        val hasSaved = prefs.getBoolean("HAS_SAVED_SESSION", false)
        val savedRemainingSec = prefs.getLong("REMAINING_SECONDS", 0L)

        val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
        val gunlukDk = sp.getLong("gunlukDk", 0L)

        if (hasSaved && savedRemainingSec > 0L) {
            btnProgramAksiyon.text = "▶️ Devam Et"
            tvRemainingTime.text = saniyeyiSayacaCevir(savedRemainingSec)
        } else if (gunlukDk > 0L) {
            btnProgramAksiyon.text = "▶️ Programı Başlat"
            tvRemainingTime.text = "Hedef: ${dakikayiMetneCevir(gunlukDk)}"
        } else {
            btnProgramAksiyon.text = "📅 Program Oluştur"
            tvRemainingTime.text = "Plan: Yok"
        }
    }

    private fun derseTiklandiginda(selectedLecture: Lecture) {
        if (remainingTimeSeconds > 0 && selectedLecture.durationSeconds > 0) {
            val videoSeconds = selectedLecture.durationSeconds.toLong()
            val minRequiredSeconds = videoSeconds / 3
            val remainingMinutes = remainingTimeSeconds / 60
            val videoMinutes = videoSeconds / 60

            val kalanMetin = dakikayiMetneCevir(remainingMinutes)
            val videoMetin = dakikayiMetneCevir(videoMinutes)

            if (remainingTimeSeconds < minRequiredSeconds) {
                AlertDialog.Builder(this)
                    .setTitle("Ders Başlatılamaz (1/3 Kuralı)")
                    .setMessage(
                        "Kalan çalışma süreniz ($kalanMetin), bu dersin süresinin ($videoMetin) üçte birinden daha az.\n\n" +
                                "Dikkatinizin bölünmemesi için yeni derse başlanamaz."
                    )
                    .setPositiveButton("Anladım", null)
                    .setNegativeButton("Yine de Başlat") { _, _ ->
                        dersiBaslat(selectedLecture)
                    }
                    .show()
                return
            }

            if (remainingTimeSeconds < videoSeconds) {
                AlertDialog.Builder(this)
                    .setTitle("Süre Aşımı Uyarısı")
                    .setMessage(
                        "Kalan çalışma süreniz $kalanMetin, ancak bu video $videoMetin sürüyor.\n\n" +
                                "Yine de bu videoya geçmek istiyor musunuz?"
                    )
                    .setPositiveButton("Evet, Başlat") { _, _ ->
                        dersiBaslat(selectedLecture)
                    }
                    .setNegativeButton("Vazgeç", null)
                    .show()
                return
            }
        }

        dersiBaslat(selectedLecture)
    }

    private fun dersiBaslat(lecture: Lecture) {
        currentPlayingLecture = lecture
        currentVideoTimeSeconds = 0L
        tvActiveTitle.text = lecture.title
        videoyuYukle(lecture.youtubeVideoId, 0L)
    }

    private fun videoyuYukle(videoId: String, startSeconds: Long = 0L) {
        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; background: black; }
                    #player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                </style>
                <script src="https://www.youtube.com/iframe_api"></script>
            </head>
            <body>
                <div id="player"></div>
                <script>
                    var player;
                    var timeUpdater;

                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            height: '100%',
                            width: '100%',
                            videoId: '$videoId',
                            playerVars: {
                                'playsinline': 1,
                                'autoplay': 1,
                                'start': $startSeconds,
                                'enablejsapi': 1,
                                'origin': 'https://localhost'
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onStateChange': onPlayerStateChange
                            }
                        });
                    }

                    function onPlayerReady(event) {
                        event.target.playVideo();
                    }
                    function onPlayerStateChange(event) {
                        if (event.data == YT.PlayerState.PLAYING) {
                            if (window.AndroidBridge && window.AndroidBridge.videoOynatilmayaBasladi) {
                                window.AndroidBridge.videoOynatilmayaBasladi();
                            }
                            if (!timeUpdater) {
                                timeUpdater = setInterval(function() {
                                    if (player && player.getCurrentTime && window.AndroidBridge) {
                                        window.AndroidBridge.zamanGuncellendiginde(player.getCurrentTime());
                                    }
                                }, 1000);
                            }
                        } else if (event.data == YT.PlayerState.PAUSED) {
                            if (window.AndroidBridge && window.AndroidBridge.videoDuraklatildi) {
                                window.AndroidBridge.videoDuraklatildi();
                            }
                            if (timeUpdater) {
                                clearInterval(timeUpdater);
                                timeUpdater = null;
                            }
                        } else {
                            if (timeUpdater) {
                                clearInterval(timeUpdater);
                                timeUpdater = null;
                            }
                            if (player && player.getCurrentTime && window.AndroidBridge) {
                                window.AndroidBridge.zamanGuncellendiginde(player.getCurrentTime());
                            }
                        }
                    }
                   
                </script>
            </body>
            </html>
        """.trimIndent()

        webViewPlayer.loadDataWithBaseURL("https://localhost", htmlData, "text/html", "utf-8", null)
    }

    private fun videoyuDuraklat() {
        val js = "if(player && player.pauseVideo) { player.pauseVideo(); }"
        webViewPlayer.evaluateJavascript(js, null)
    }

    private fun videoyuOynat() {
        val js = "if(player && player.playVideo) { player.playVideo(); }"
        webViewPlayer.evaluateJavascript(js, null)
    }

    private fun oynatmaHiziniAyarla(rate: Double) {
        val js = "if(player && player.setPlaybackRate) { player.setPlaybackRate($rate); }"
        webViewPlayer.evaluateJavascript(js, null)
        Toast.makeText(this, "Hız: ${rate}x", Toast.LENGTH_SHORT).show()
    }

    private fun calismaPlaniniBaslat(targetMinutes: Long) {
        studyTimer?.cancel()
        remainingTimeSeconds = targetMinutes * 60

        studyTimer = object : CountDownTimer(remainingTimeSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeSeconds = millisUntilFinished / 1000
                tvRemainingTime.text = saniyeyiSayacaCevir(remainingTimeSeconds)
            }

            override fun onFinish() {
                remainingTimeSeconds = 0
                tvRemainingTime.text = "Süre Doldu!"

                val activeLecture = currentPlayingLecture
                val videoKalanSaniye = if (activeLecture != null) {
                    activeLecture.durationSeconds - currentVideoTimeSeconds
                } else 0L

                // 15 Dakika Esneme Kuralı
                if (activeLecture != null && videoKalanSaniye in 1..900) {
                    val kalanDk = (videoKalanSaniye / 60) + 1
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Çalışma Süreniz Doldu!")
                        .setMessage("Bugünkü hedef süreniz tamamlandı ancak bu videonun bitmesine sadece $kalanDk dakika kaldı. Videoyu bitirmek ister misiniz?")
                        .setPositiveButton("Evet, Bitir") { _, _ ->
                            Toast.makeText(
                                this@MainActivity,
                                "Videoyu tamamlayabilirsiniz.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("Dersi Bitir") { _, _ ->
                            calismaIlerlemesiniTemizle()
                            videoyuDuraklat()
                        }
                        .show()
                } else {
                    calismaIlerlemesiniTemizle()
                    videoyuDuraklat()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Tebrikler!")
                        .setMessage("Bugünkü çalışma sürenizi başarıyla tamamladınız. Video otomatik duraklatıldı.")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            }
        }.start()

        Toast.makeText(
            this,
            "${dakikayiMetneCevir(targetMinutes)} çalışma planı başladı!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun aramaFiltresiniAyarla() {
        searchViewCourses.setOnSearchClickListener {
            aramaEkraniniAc()
        }
        searchViewCourses.setOnClickListener {
            aramaEkraniniAc()
        }
    }

    private fun aramaEkraniniAc() {
        val titles = ArrayList<String>()
        val ids = ArrayList<String>()
        val durations = ArrayList<String>()
        val thumbs = ArrayList<String>()

        for (lec in fullLectureList) {
            titles.add(lec.title)
            ids.add(lec.youtubeVideoId)
            durations.add(lec.duration)
            thumbs.add(lec.thumbnailUrl)
        }

        val intent = Intent(this, SearchActivity::class.java).apply {
            putStringArrayListExtra("LECTURE_TITLES", titles)
            putStringArrayListExtra("LECTURE_IDS", ids)
            putStringArrayListExtra("LECTURE_DURATIONS", durations)
            putStringArrayListExtra("LECTURE_THUMBS", thumbs)
        }
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val videoId = data.getStringExtra("SELECTED_VIDEO_ID") ?: ""
            val title = data.getStringExtra("SELECTED_TITLE") ?: ""
            if (videoId.isNotEmpty()) {
                val foundLecture = fullLectureList.find { it.youtubeVideoId == videoId }
                if (foundLecture != null) {
                    derseTiklandiginda(foundLecture)
                } else {
                    tvActiveTitle.text = title
                    videoyuYukle(videoId, 0L)
                }
            }
        }
    }

    private fun oynatmaListeleriniGetir() {
        val playlistsUrl = BACKEND_URL.replace("/api/lectures", "/api/playlists")
        val queue = Volley.newRequestQueue(this)
        val jsonArrayRequest = object : JsonArrayRequest(
            Request.Method.GET,
            playlistsUrl,
            null,
            { response ->
                courseList.clear()
                try {
                    for (i in 0 until response.length()) {
                        val item = response.getJSONObject(i)
                        courseList.add(
                            Course(
                                key = item.getString("key"),
                                title = item.getString("title"),
                                playlistId = item.getString("playlist_id"),
                                description = item.getString("description"),
                                thumbnailUrl = item.optString("thumbnail_url", "")
                            )
                        )
                    }
                    courseAdapter.notifyDataSetChanged()
                } catch (e: JSONException) {
                    Toast.makeText(this, "Kurslar yüklenirken hata oluştu.", Toast.LENGTH_SHORT)
                        .show()
                }
            },
            {
                Toast.makeText(this, "Kurs listesi alınamadı.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["ngrok-skip-browser-warning"] = "true"
                return headers
            }
        }

        queue.add(jsonArrayRequest)
    }

    private fun dersleriGetir(courseKey: String = "algorithms", refresh: Boolean = false) {
        val url = "$BACKEND_URL?course=$courseKey&refresh=$refresh"
        val queue = Volley.newRequestQueue(this)
        val jsonArrayRequest = object : JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                fullLectureList.clear()
                displayedLectureList.clear()
                try {
                    for (i in 0 until response.length()) {
                        val item = response.getJSONObject(i)
                        fullLectureList.add(
                            Lecture(
                                id = item.getString("id"),
                                title = item.getString("title"),
                                duration = item.getString("duration"),
                                durationSeconds = item.optInt("duration_seconds", 0),
                                youtubeVideoId = item.getString("youtube_video_id"),
                                thumbnailUrl = item.getString("thumbnail_url")
                            )
                        )
                    }
                    displayedLectureList.addAll(fullLectureList)
                    adapter.notifyDataSetChanged()

                    val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
                    val hasSaved = prefs.getBoolean("HAS_SAVED_SESSION", false)

                    if (!hasSaved && displayedLectureList.isNotEmpty()) {
                        currentPlayingLecture = displayedLectureList[0]
                        tvActiveTitle.text = displayedLectureList[0].title
                        videoyuYukle(displayedLectureList[0].youtubeVideoId, 0L)
                    }
                } catch (e: JSONException) {
                    Toast.makeText(this, "Veriler okunurken bir hata oluştu.", Toast.LENGTH_SHORT)
                        .show()
                }
            },
            { error ->
                val userFriendlyMessage = when (error) {
                    is NoConnectionError, is NetworkError -> "İnternet bağlantısı yok veya zayıf."
                    is TimeoutError -> "Sunucuya bağlanılamadı, istek zaman aşımına uğradı."
                    is ServerError -> "Sunucu yanıt veremiyor."
                    else -> "Ders listesi yüklenemedi."
                }
                Toast.makeText(
                    this,
                    "$userFriendlyMessage Çevrimdışı demo verisi yükleniyor...",
                    Toast.LENGTH_SHORT
                ).show()

                try {
                    val jsonString =
                        assets.open("offline_mock_data.json").bufferedReader().use { it.readText() }
                    val yerelVeri = org.json.JSONArray(jsonString)

                    fullLectureList.clear()
                    displayedLectureList.clear()

                    for (i in 0 until yerelVeri.length()) {
                        val item = yerelVeri.getJSONObject(i)
                        fullLectureList.add(
                            Lecture(
                                id = item.getString("id"),
                                title = item.getString("title"),
                                duration = item.getString("duration"),
                                durationSeconds = item.optInt("duration_seconds", 0),
                                youtubeVideoId = item.getString("youtube_video_id"),
                                thumbnailUrl = item.getString("thumbnail_url")
                            )
                        )
                    }
                    displayedLectureList.addAll(fullLectureList)
                    adapter.notifyDataSetChanged()

                    if (displayedLectureList.isNotEmpty()) {
                        currentPlayingLecture = displayedLectureList[0]
                        tvActiveTitle.text = displayedLectureList[0].title
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Çevrimdışı dosya okunamadı: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["ngrok-skip-browser-warning"] = "true"
                return headers
            }
        }

        queue.add(jsonArrayRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        studyTimer?.cancel()
    }

    override fun onPause() {
        super.onPause()
        webViewPlayer.evaluateJavascript(
            "if(player && player.getCurrentTime && window.AndroidBridge) { window.AndroidBridge.zamanGuncellendiginde(player.getCurrentTime()); }",
            null
        )
        kaydetDurumu()
    }

    override fun onStop() {
        super.onStop()
        kaydetDurumu()
    }

    private fun kaydetDurumu() {
        val activeLecture = currentPlayingLecture
        if (remainingTimeSeconds > 0 && activeLecture != null) {
            calismaIlerlemesiniKaydet(
                currentCourseName,
                activeLecture.youtubeVideoId,
                activeLecture.title,
                currentVideoTimeSeconds
            )
        }
    }

    private fun calismaIlerlemesiniKaydet(
        courseName: String,
        videoId: String,
        videoTitle: String,
        videoCurrentSec: Long
    ) {
        val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val currentDate = java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale("tr"))
            .format(java.util.Date())

        editor.putBoolean("HAS_SAVED_SESSION", true)
        editor.putString("SAVED_DATE", currentDate)
        editor.putString("SAVED_COURSE", courseName)
        editor.putString("SAVED_VIDEO_ID", videoId)
        editor.putString("SAVED_VIDEO_TITLE", videoTitle)
        editor.putLong("TOTAL_TARGET_MINUTES", totalTargetMinutes)
        editor.putLong("REMAINING_SECONDS", remainingTimeSeconds)
        editor.putLong("SAVED_VIDEO_SECONDS", videoCurrentSec)
        editor.apply()
    }

    private fun calismaIlerlemesiniTemizle() {
        val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun kaydedilenIlerlemeyiKontrolEt() {
        val prefs = getSharedPreferences("StudyPlannerPrefs", Context.MODE_PRIVATE)
        val hasSaved = prefs.getBoolean("HAS_SAVED_SESSION", false)

        if (hasSaved) {
            val savedDate = prefs.getString("SAVED_DATE", "Geçen gün") ?: ""
            val savedCourse = prefs.getString("SAVED_COURSE", "Ders") ?: ""
            val videoId = prefs.getString("SAVED_VIDEO_ID", "") ?: ""
            val videoTitle = prefs.getString("SAVED_VIDEO_TITLE", "") ?: ""
            val totalMinutes = prefs.getLong("TOTAL_TARGET_MINUTES", 0L)
            val remainingSec = prefs.getLong("REMAINING_SECONDS", 0L)
            val videoSec = prefs.getLong("SAVED_VIDEO_SECONDS", 0L)

            val workedSeconds = (totalMinutes * 60) - remainingSec
            val workedMinutes = if (workedSeconds > 0) workedSeconds / 60 else 0

            val message = "$savedDate günü '$savedCourse' oynatma listesinde, " +
                    "${dakikayiMetneCevir(totalMinutes)}lik planın ${
                        dakikayiMetneCevir(
                            workedMinutes
                        )
                    }sinde kaldınız.\n\n" +
                    "Kaldığınız yerden devam etmek ister misiniz?"

            window.decorView.post {
                AlertDialog.Builder(this)
                    .setTitle("Kaldığınız Yerden Devam Edin")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Devam Et") { _, _ ->
                        totalTargetMinutes = totalMinutes
                        kalanSuredenCalismaPlaniniBaslat(remainingSec)
                        if (videoId.isNotEmpty()) {
                            tvActiveTitle.text = videoTitle
                            currentVideoTimeSeconds = videoSec
                            currentPlayingLecture =
                                fullLectureList.find { it.youtubeVideoId == videoId }
                                    ?: Lecture("0", videoTitle, "", 0, videoId, "")
                            videoyuYukle(videoId, videoSec)
                        }
                    }
                    .setNegativeButton("Yeni Plan Oluştur") { _, _ ->
                        calismaIlerlemesiniTemizle()
                        val intent = Intent(this, ScheduleActivity::class.java)
                        intent.putExtra("mevcutDersler", fullLectureList)
                        startActivity(intent)
                    }
                    .show()
            }
        }
    }

    private fun kalanSuredenCalismaPlaniniBaslat(seconds: Long) {
        studyTimer?.cancel()
        remainingTimeSeconds = seconds
        isTimerRunning = true
        btnProgramAksiyon.text = "⏸️ Çalışmayı Duraklat"

        studyTimer = object : CountDownTimer(remainingTimeSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeSeconds = millisUntilFinished / 1000
                tvRemainingTime.text = saniyeyiSayacaCevir(remainingTimeSeconds)
            }

            override fun onFinish() {
                remainingTimeSeconds = 0
                isTimerRunning = false
                tvRemainingTime.text = "Süre Doldu!"
                btnProgramAksiyon.text = "▶️ Programı Başlat"

                val activeLecture = currentPlayingLecture
                val videoKalanSaniye = if (activeLecture != null) {
                    activeLecture.durationSeconds - currentVideoTimeSeconds
                } else 0L

                // 15 Dakika Esneme Kuralı
                if (activeLecture != null && videoKalanSaniye in 1..900) {
                    val kalanDk = (videoKalanSaniye / 60) + 1
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Çalışma Süreniz Doldu!")
                        .setMessage("Bugünkü hedef süreniz tamamlandı ancak bu videonun bitmesine sadece $kalanDk dakika kaldı. Videoyu bitirmek ister misiniz?")
                        .setPositiveButton("Evet, Bitir") { _, _ ->
                            Toast.makeText(
                                this@MainActivity,
                                "Videoyu tamamlayabilirsiniz.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton("Dersi Bitir") { _, _ ->
                            calismaIlerlemesiniTemizle()
                            videoyuDuraklat()
                        }
                        .show()
                } else {
                    calismaIlerlemesiniTemizle()
                    videoyuDuraklat()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Hedef Tamamlandı")
                        .setMessage("Bugünkü çalışma sürenizi tamamladınız. Video duraklatıldı.")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            }
        }.start()
    }
}