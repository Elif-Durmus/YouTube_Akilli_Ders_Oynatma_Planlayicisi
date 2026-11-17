package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class ScheduleActivity : AppCompatActivity() {

    private lateinit var tvPlanOzeti: TextView
    private lateinit var recyclerViewTakvim: RecyclerView
    private lateinit var btnYeniPlanYap: Button
    private lateinit var btnAlgoritmaSpt: MaterialButton
    private lateinit var btnAlgoritmaLoadBalance: MaterialButton

    private var gelenDersler = ArrayList<Lecture>()
    private var seciliAlgoritma = "SPT" // "SPT" veya "LOAD_BALANCE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        val btnTakvimGeri = findViewById<ImageButton>(R.id.btnTakvimGeri)
        btnTakvimGeri.setOnClickListener {
            finish() // ScheduleActivity'yi kapatıp doğrudan alttaki MainActivity ekranına döner
        }

        tvPlanOzeti = findViewById(R.id.tvPlanOzeti)
        recyclerViewTakvim = findViewById(R.id.recyclerViewTakvim)
        btnYeniPlanYap = findViewById(R.id.btnYeniPlanYap)
        btnAlgoritmaSpt = findViewById(R.id.btnAlgoritmaSpt)
        btnAlgoritmaLoadBalance = findViewById(R.id.btnAlgoritmaLoadBalance)

        recyclerViewTakvim.layoutManager = LinearLayoutManager(this)

        val aktarilanListe = intent.getSerializableExtra("mevcutDersler") as? ArrayList<Lecture>
        if (aktarilanListe != null && aktarilanListe.isNotEmpty()) {
            gelenDersler = aktarilanListe
        }

        btnAlgoritmaSpt.setOnClickListener {
            seciliAlgoritma = "SPT"
            butonGorunumunuGuncelle()
            kayitliPlaniYukle()
        }

        btnAlgoritmaLoadBalance.setOnClickListener {
            seciliAlgoritma = "LOAD_BALANCE"
            butonGorunumunuGuncelle()
            kayitliPlaniYukle()
        }

        butonGorunumunuGuncelle()
        kayitliPlaniYukle()

        btnYeniPlanYap.setOnClickListener {
            planSihirbaziniAc()
        }
    }

    private fun butonGorunumunuGuncelle() {
        if (seciliAlgoritma == "SPT") {
            btnAlgoritmaSpt.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_primary))
            btnAlgoritmaSpt.setTextColor(Color.WHITE)
            btnAlgoritmaLoadBalance.setBackgroundColor(ContextCompat.getColor(this, R.color.ice_surface))
            btnAlgoritmaLoadBalance.setTextColor(ContextCompat.getColor(this, R.color.slate_dark))
        } else {
            btnAlgoritmaLoadBalance.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_primary))
            btnAlgoritmaLoadBalance.setTextColor(Color.WHITE)
            btnAlgoritmaSpt.setBackgroundColor(ContextCompat.getColor(this, R.color.ice_surface))
            btnAlgoritmaSpt.setTextColor(ContextCompat.getColor(this, R.color.slate_dark))
        }
    }

    // Dakikayı '1 sa 15 dk' veya '45 dk' olarak metne çevirir
    private fun dakikayiFormatla(dakika: Long): String {
        val sa = dakika / 60
        val dk = dakika % 60
        return when {
            sa > 0 && dk > 0L -> "$sa sa $dk dk"
            sa > 0 && dk == 0L -> "$sa sa"
            else -> "$dk dk"
        }
    }

    private fun planSihirbaziniAc() {
        if (gelenDersler.isEmpty()) {
            Toast.makeText(this, "Önce ana ekrandan bir oynatma listesi seçmelisiniz.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_plan_olustur, null)
        val tvVideoBilgi = dialogView.findViewById<TextView>(R.id.tvDialogVideoBilgi)
        val btnTarihSec = dialogView.findViewById<MaterialButton>(R.id.btnTarihSec)
        val etSaat = dialogView.findViewById<EditText>(R.id.etDialogSaat)
        val etDakika = dialogView.findViewById<EditText>(R.id.etDialogDakika)
        val tvHata = dialogView.findViewById<TextView>(R.id.tvDialogHata)

        tvVideoBilgi.text = "Seçili listede ${gelenDersler.size} ders videosu bulunuyor"

        // 1. Başlangıç Tarihi Seçici (Varsayılan: Bugün)
        val seciliTakvim = Calendar.getInstance()
        val tarihFormati = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr"))
        btnTarihSec.text = "📅 ${tarihFormati.format(seciliTakvim.time)}"

        btnTarihSec.setOnClickListener {
            DatePickerDialog(
                this,
                { _, yil, ay, gun ->
                    seciliTakvim.set(yil, ay, gun)
                    btnTarihSec.text = "📅 ${tarihFormati.format(seciliTakvim.time)}"
                },
                seciliTakvim.get(Calendar.YEAR),
                seciliTakvim.get(Calendar.MONTH),
                seciliTakvim.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 2. Haftanın Günleri Butonları (Calendar sabitleriyle eşleşik)
        val gunButonMap = listOf(
            Calendar.MONDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunPzt),
            Calendar.TUESDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunSal),
            Calendar.WEDNESDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunCar),
            Calendar.THURSDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunPer),
            Calendar.FRIDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunCum),
            Calendar.SATURDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunCmt),
            Calendar.SUNDAY to dialogView.findViewById<MaterialButton>(R.id.btnGunPaz)
        )

        // Varsayılan olarak hafta içi 5 gün seçili gelsin
        val seciliGunler = mutableSetOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )

        fun gunButonStiliniGuncelle(btn: MaterialButton, seciliMi: Boolean) {
            if (seciliMi) {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_primary))
                btn.setTextColor(Color.WHITE)
            } else {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.ice_surface))
                btn.setTextColor(ContextCompat.getColor(this, R.color.slate_dark))
            }
        }

        for ((gunKodu, buton) in gunButonMap) {
            gunButonStiliniGuncelle(buton, seciliGunler.contains(gunKodu))
            buton.setOnClickListener {
                if (seciliGunler.contains(gunKodu)) {
                    seciliGunler.remove(gunKodu)
                    gunButonStiliniGuncelle(buton, false)
                } else {
                    seciliGunler.add(gunKodu)
                    gunButonStiliniGuncelle(buton, true)
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Çalışma Planı Parametreleri")
            .setView(dialogView)
            .setPositiveButton("Takvimi Oluştur", null)
            .setNegativeButton("İptal", null)
            .create()

        dialog.setOnShowListener {
            val btnOlustur = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnOlustur.setOnClickListener {
                val girilenSaat = etSaat.text.toString().toLongOrNull() ?: 0L
                val girilenDk = etDakika.text.toString().toLongOrNull() ?: 0L
                val toplamGunlukDakika = (girilenSaat * 60) + girilenDk

                // 8. Hafta Parametre Doğrulama (Validation) Kuralları
                when {
                    seciliGunler.isEmpty() -> {
                        tvHata.text = "⚠️ Lütfen haftada en az 1 çalışma günü seçin."
                        tvHata.visibility = View.VISIBLE
                    }
                    toplamGunlukDakika !in 30..120 -> {
                        tvHata.text = "⚠️ Günlük süre bütçesi 30 ile 120 dakika arasında olmalıdır (Girilen: $toplamGunlukDakika dk)."
                        tvHata.visibility = View.VISIBLE
                    }
                    else -> {
                        tvHata.visibility = View.GONE
                        val siraliSeciliGunler = listOf(
                            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
                        ).filter { seciliGunler.contains(it) }

                        val gunlerString = siraliSeciliGunler.joinToString(",")
                        planiKaydet(siraliSeciliGunler.size, toplamGunlukDakika, seciliTakvim.timeInMillis, gunlerString)
                        planiHesaplaVeGoster(siraliSeciliGunler, toplamGunlukDakika, seciliTakvim.timeInMillis)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    // Seçilen başlangıç tarihinden itibaren sadece seçilen haftanın günlerine denk gelen gerçek tarihleri üretir
    private fun gercekTarihEtiketleriniUret(baslangicMillis: Long, seciliGunKodlari: List<Int>): List<String> {
        val etiketler = ArrayList<String>()
        val takvim = Calendar.getInstance().apply { timeInMillis = baslangicMillis }
        val kisaFormat = SimpleDateFormat("dd MMM EEEE", Locale("tr"))

        var bakilanGunSayisi = 0
        while (etiketler.size < seciliGunKodlari.size && bakilanGunSayisi < 14) {
            val haftaninGunu = takvim.get(Calendar.DAY_OF_WEEK)
            if (seciliGunKodlari.contains(haftaninGunu)) {
                etiketler.add(kisaFormat.format(takvim.time))
            }
            takvim.add(Calendar.DAY_OF_YEAR, 1)
            bakilanGunSayisi++
        }
        return etiketler
    }

    private fun planiHesaplaVeGoster(
        seciliGunKodlari: List<Int>,
        toplamGunlukDakika: Long,
        baslangicMillis: Long
    ) {
        val haftalikGun = seciliGunKodlari.size
        val tarihEtiketleri = gercekTarihEtiketleriniUret(baslangicMillis, seciliGunKodlari)

        val baslangicZamani = System.nanoTime()
        val hamGunler = if (seciliAlgoritma == "SPT") {
            sadeceBirHaftalikProgramHesaplaSPT(gelenDersler, haftalikGun, toplamGunlukDakika)
        } else {
            greedyLoadBalancingHesapla(gelenDersler, haftalikGun, toplamGunlukDakika)
        }
        val gecenSureMs = (System.nanoTime() - baslangicZamani) / 1_000_000.0

        // Günlere gerçek takvim tarihlerini bağla
        val tarihliGunler = ArrayList<GunlukPlan>()
        for (i in hamGunler.indices) {
            val g = hamGunler[i]
            val tarih = if (i < tarihEtiketleri.size) tarihEtiketleri[i] else "${g.gunNumarasi}. Gün"
            tarihliGunler.add(g.copy(tarihMetni = tarih))
        }

        // Karşılaştırma Metrikleri Hesabı
        val yerlesenVideoSayisi = tarihliGunler.sumOf { it.dersler.size }
        val gunlukSurelerDk = tarihliGunler.map { gun ->
            gun.dersler.sumOf { it.durationSeconds } / 60.0
        }
        val ortalamaSapma = if (gunlukSurelerDk.isNotEmpty()) {
            gunlukSurelerDk.map { abs(toplamGunlukDakika - it) }.average()
        } else 0.0
        val yukFarki = if (gunlukSurelerDk.isNotEmpty()) {
            (gunlukSurelerDk.maxOrNull() ?: 0.0) - (gunlukSurelerDk.minOrNull() ?: 0.0)
        } else 0.0

        val yontemAdi = if (seciliAlgoritma == "SPT") "Yöntem 1: SPT (Kısa Önce)" else "Yöntem 2: Greedy Load Balancing"
        val formatliSure = dakikayiFormatla(toplamGunlukDakika)

        tvPlanOzeti.text = "$yontemAdi • Hedef: $formatliSure/gün ($haftalikGun gün)\n" +
                "Yerleşen: $yerlesenVideoSayisi/${gelenDersler.size} video | Ort. Sapma: ${String.format("%.1f", ortalamaSapma)} dk | " +
                "Yük Farkı: ${String.format("%.1f", yukFarki)} dk (${String.format("%.2f", gecenSureMs)} ms)"

        recyclerViewTakvim.adapter = TakvimGunKartAdapter(tarihliGunler)
    }

    // YÖNTEM 1 (6. Hafta): Videoları süresi en kısa olandan en uzun olana sıralar ve günlere yerleştirir (SPT)
    private fun sadeceBirHaftalikProgramHesaplaSPT(
        dersler: List<Lecture>,
        haftalikGunSayisi: Int,
        gunlukHedefDakika: Long
    ): ArrayList<GunlukPlan> {
        val gunlukHedefSaniye = gunlukHedefDakika * 60
        val buHaftaninGunleri = ArrayList<GunlukPlan>()

        // Videoları sürelerine göre küçükten büyüğe sırala
        val siraliDersler = dersler.sortedBy { it.durationSeconds }

        var suankiGunNo = 1
        var suankiGunSaniyesi = 0L
        var suankiGunDersleri = ArrayList<Lecture>()

        for (ders in siraliDersler) {
            if (suankiGunNo > haftalikGunSayisi) break

            val dersSaniyesi = ders.durationSeconds.toLong()

            if (suankiGunDersleri.isEmpty()) {
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi += dersSaniyesi
            } else if (suankiGunSaniyesi + dersSaniyesi <= gunlukHedefSaniye) {
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi += dersSaniyesi
            } else {
                val toplamDk = suankiGunSaniyesi / 60
                buHaftaninGunleri.add(GunlukPlan(suankiGunNo, dakikayiFormatla(toplamDk), suankiGunDersleri))
                suankiGunNo++

                if (suankiGunNo > haftalikGunSayisi) break

                suankiGunDersleri = ArrayList()
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi = dersSaniyesi
            }
        }

        if (suankiGunDersleri.isNotEmpty() && suankiGunNo <= haftalikGunSayisi) {
            val toplamDk = suankiGunSaniyesi / 60
            buHaftaninGunleri.add(GunlukPlan(suankiGunNo, dakikayiFormatla(toplamDk), suankiGunDersleri))
        }

        return buHaftaninGunleri
    }

    // YÖNTEM 2 (7. Hafta): Her videoyu o anda toplam süresi en düşük uygun güne atayan Greedy Load Balancing
    private fun greedyLoadBalancingHesapla(
        dersler: List<Lecture>,
        haftalikGunSayisi: Int,
        gunlukHedefDakika: Long
    ): ArrayList<GunlukPlan> {
        val gunlukHedefSaniye = gunlukHedefDakika * 60
        val gunListeleri = Array(haftalikGunSayisi) { ArrayList<Lecture>() }
        val gunToplamSaniyeleri = LongArray(haftalikGunSayisi) { 0L }

        for (ders in dersler) {
            val dersSaniyesi = ders.durationSeconds.toLong()

            var enDusukYukluGunIndex = -1
            var minSaniye = Long.MAX_VALUE

            for (i in 0 until haftalikGunSayisi) {
                if (gunToplamSaniyeleri[i] + dersSaniyesi <= gunlukHedefSaniye) {
                    if (gunToplamSaniyeleri[i] < minSaniye) {
                        minSaniye = gunToplamSaniyeleri[i]
                        enDusukYukluGunIndex = i
                    }
                }
            }

            if (enDusukYukluGunIndex != -1) {
                gunListeleri[enDusukYukluGunIndex].add(ders)
                gunToplamSaniyeleri[enDusukYukluGunIndex] += dersSaniyesi
            }
        }

        val sonucGunler = ArrayList<GunlukPlan>()
        for (i in 0 until haftalikGunSayisi) {
            val toplamDk = gunToplamSaniyeleri[i] / 60
            sonucGunler.add(GunlukPlan(i + 1, dakikayiFormatla(toplamDk), gunListeleri[i]))
        }
        return sonucGunler
    }

    private fun planiKaydet(haftalikGun: Int, gunlukDk: Long, baslangicMillis: Long, seciliGunlerStr: String) {
        val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
        sp.edit()
            .putInt("haftalikGun", haftalikGun)
            .putLong("gunlukDk", gunlukDk)
            .putLong("baslangicMillis", baslangicMillis)
            .putString("seciliGunlerStr", seciliGunlerStr)
            .apply()
    }

    private fun kayitliPlaniYukle() {
        val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
        val gun = sp.getInt("haftalikGun", 0)
        val dk = sp.getLong("gunlukDk", 0L)
        val baslangicMillis = sp.getLong("baslangicMillis", System.currentTimeMillis())
        val gunlerStr = sp.getString("seciliGunlerStr", "") ?: ""

        if (gun > 0 && dk > 0L && gelenDersler.isNotEmpty()) {
            val seciliKodlar = if (gunlerStr.isNotEmpty()) {
                gunlerStr.split(",").mapNotNull { it.toIntOrNull() }
            } else {
                listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY).take(gun)
            }
            planiHesaplaVeGoster(seciliKodlar, dk, baslangicMillis)
        }
    }
}

class TakvimGunKartAdapter(private val gunler: List<GunlukPlan>) :
    RecyclerView.Adapter<TakvimGunKartAdapter.GunViewHolder>() {

    class GunViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvGunBaslik: TextView = view.findViewById(R.id.tvGunBaslik)
        val tvGunSureRozeti: TextView = view.findViewById(R.id.tvGunSureRozeti)
        val layoutGunDersleri: LinearLayout = view.findViewById(R.id.layoutGunDersleri)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GunViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_takvim_gun, parent, false)
        return GunViewHolder(view)
    }

    override fun onBindViewHolder(holder: GunViewHolder, position: Int) {
        val gun = gunler[position]
        val context = holder.itemView.context

        holder.tvGunBaslik.text = if (gun.tarihMetni.isNotEmpty()) {
            "${gun.gunNumarasi}. Gün • ${gun.tarihMetni}"
        } else {
            "${gun.gunNumarasi}. Gün Programı"
        }
        holder.tvGunSureRozeti.text = "Toplam: ${gun.toplamSureMetni}"
        holder.layoutGunDersleri.removeAllViews()

        if (gun.dersler.isEmpty()) {
            val bosText = TextView(context).apply {
                text = "Bu gün için uygun sürede video atanmadı."
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            holder.layoutGunDersleri.addView(bosText)
        } else {
            for (ders in gun.dersler) {
                val satir = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 12, 16, 12)
                    setBackgroundResource(R.drawable.bg_smoke_card)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, 10)
                    layoutParams = params
                }

                val tvDersAdi = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = ders.title
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    maxLines = 2
                }

                val tvSure = TextView(context).apply {
                    text = ders.duration
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.slate_primary))
                    setPadding(12, 0, 0, 0)
                }

                satir.addView(tvDersAdi)
                satir.addView(tvSure)
                holder.layoutGunDersleri.addView(satir)
            }
        }
    }

    override fun getItemCount(): Int = gunler.size
}