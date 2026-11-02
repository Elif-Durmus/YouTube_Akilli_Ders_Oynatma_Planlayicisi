package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScheduleActivity : AppCompatActivity() {

    private lateinit var tvPlanOzeti: TextView
    private lateinit var recyclerViewTakvim: RecyclerView
    private lateinit var btnYeniPlanYap: Button

    private var gelenDersler = ArrayList<Lecture>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        tvPlanOzeti = findViewById(R.id.tvPlanOzeti)
        recyclerViewTakvim = findViewById(R.id.recyclerViewTakvim)
        btnYeniPlanYap = findViewById(R.id.btnYeniPlanYap)

        recyclerViewTakvim.layoutManager = LinearLayoutManager(this)

        val aktarilanListe = intent.getSerializableExtra("mevcutDersler") as? ArrayList<Lecture>
        if (aktarilanListe != null && aktarilanListe.isNotEmpty()) {
            gelenDersler = aktarilanListe
        }

        kayitliPlaniYukle()

        btnYeniPlanYap.setOnClickListener {
            planSihirbaziniAc()
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

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val tvBilgi = TextView(this).apply {
            text = "Algoritma: Shortest Processing Time (SPT)\nSeçili ders listesindeki toplam video: ${gelenDersler.size}\n"
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        // 1. Haftalık Gün Sayısı
        val tvGunSec = TextView(this).apply {
            text = "Haftada Kaç Gün Çalışacaksınız? (1-7):"
        }
        val etHaftalikGun = EditText(this).apply {
            hint = "Örn: 5"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        // 2. Saat ve Dakika Giriş Kutuları
        val tvSureBaslik = TextView(this).apply {
            text = "\nGünlük Hedef Çalışma Süresi:"
        }

        val timeInputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val etSaat = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Saat (Örn: 1)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val tvAyirici = TextView(this).apply {
            text = " sa  "
            setPadding(8, 24, 8, 0)
        }

        val etDakika = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Dakika (Örn: 20)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val tvDkMetni = TextView(this).apply {
            text = " dk"
            setPadding(8, 24, 8, 0)
        }

        timeInputLayout.addView(etSaat)
        timeInputLayout.addView(tvAyirici)
        timeInputLayout.addView(etDakika)
        timeInputLayout.addView(tvDkMetni)

        rootLayout.addView(tvBilgi)
        rootLayout.addView(tvGunSec)
        rootLayout.addView(etHaftalikGun)
        rootLayout.addView(tvSureBaslik)
        rootLayout.addView(timeInputLayout)

        AlertDialog.Builder(this)
            .setTitle("1 Haftalık Çalışma Takvimi")
            .setView(rootLayout)
            .setPositiveButton("Takvimi Oluştur") { _, _ ->
                val haftalikGun = etHaftalikGun.text.toString().toIntOrNull() ?: 5
                val girilenSaat = etSaat.text.toString().toLongOrNull() ?: 0L
                val girilenDk = etDakika.text.toString().toLongOrNull() ?: 0L

                val toplamGunlukDakika = (girilenSaat * 60) + girilenDk

                if (haftalikGun in 1..7 && toplamGunlukDakika > 0) {
                    // Sadece 1 haftalık liste hesaplanır
                    val tekHaftalikPlan = sadeceBirHaftalikProgramHesapla(gelenDersler, haftalikGun, toplamGunlukDakika)
                    val formatliSure = dakikayiFormatla(toplamGunlukDakika)

                    tvPlanOzeti.text = "Aktif Plan: 1 Hafta ($haftalikGun gün), Günde $formatliSure"
                    recyclerViewTakvim.adapter = TakvimHaftaAdapter(tekHaftalikPlan)
                    planiKaydet(haftalikGun, toplamGunlukDakika)
                } else {
                    Toast.makeText(this, "Lütfen geçerli bir gün ve süre girin.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // Videoları süresi en kısa olandan en uzun olana sıralar ve günlere yerleştirir
    private fun sadeceBirHaftalikProgramHesapla(
        dersler: List<Lecture>,
        haftalikGunSayisi: Int,
        gunlukHedefDakika: Long
    ): ArrayList<HaftalikPlan> {
        val gunlukHedefSaniye = gunlukHedefDakika * 60
        val buHaftaninGunleri = ArrayList<GunlukPlan>()

        //  Videoları sürelerine göre küçükten büyüğe sırala
        val siraliDersler = dersler.sortedBy { it.durationSeconds }

        var suankiGunNo = 1
        var suankiGunSaniyesi = 0L
        var suankiGunDersleri = ArrayList<Lecture>()

        //  Sıralanmış videoları günlerin bütçesine göre açgözlü olarak dağıt
        for (ders in siraliDersler) {
            if (suankiGunNo > haftalikGunSayisi) break

            val dersSaniyesi = ders.durationSeconds.toLong()

            // Eğer güne henüz hiç ders eklenmediyse doğrudan ekle
            if (suankiGunDersleri.isEmpty()) {
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi += dersSaniyesi
            } else if (suankiGunSaniyesi + dersSaniyesi <= gunlukHedefSaniye) {
                // Günlük hedef süreyi aşmıyorsa mevcut güne eklemeye devam et
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi += dersSaniyesi
            } else {
                // Günlük süre doldu, bu günü tamamla ve bir sonraki güne geç
                val toplamDk = suankiGunSaniyesi / 60
                val formatliGunlukSure = dakikayiFormatla(toplamDk)
                buHaftaninGunleri.add(GunlukPlan(suankiGunNo, formatliGunlukSure, suankiGunDersleri))
                suankiGunNo++

                if (suankiGunNo > haftalikGunSayisi) break

                suankiGunDersleri = ArrayList()
                suankiGunDersleri.add(ders)
                suankiGunSaniyesi = dersSaniyesi
            }
        }

        // Kalan son günü de listeye ekle
        if (suankiGunDersleri.isNotEmpty() && suankiGunNo <= haftalikGunSayisi) {
            val toplamDk = suankiGunSaniyesi / 60
            val formatliGunlukSure = dakikayiFormatla(toplamDk)
            buHaftaninGunleri.add(GunlukPlan(suankiGunNo, formatliGunlukSure, suankiGunDersleri))
        }

        val haftalikListe = ArrayList<HaftalikPlan>()
        if (buHaftaninGunleri.isNotEmpty()) {
            haftalikListe.add(HaftalikPlan(1, buHaftaninGunleri))
        }

        return haftalikListe
    }

    private fun planiKaydet(haftalikGun: Int, gunlukDk: Long) {
        val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
        sp.edit()
            .putInt("haftalikGun", haftalikGun)
            .putLong("gunlukDk", gunlukDk)
            .apply()
    }

    private fun kayitliPlaniYukle() {
        val sp = getSharedPreferences("PlanKayitlari", Context.MODE_PRIVATE)
        val gun = sp.getInt("haftalikGun", 0)
        val dk = sp.getLong("gunlukDk", 0L)
        if (gun > 0 && dk > 0L) {
            val formatliSure = dakikayiFormatla(dk)
            tvPlanOzeti.text = "Aktif Plan (SPT): 1 Hafta ($gun gün), Günde $formatliSure"
            if (gelenDersler.isNotEmpty()) {
                val plan = sadeceBirHaftalikProgramHesapla(gelenDersler, gun, dk)
                recyclerViewTakvim.adapter = TakvimHaftaAdapter(plan)
            }
        }
    }
}

class TakvimHaftaAdapter(private val haftalar: List<HaftalikPlan>) :
    RecyclerView.Adapter<TakvimHaftaAdapter.HaftaViewHolder>() {

    class HaftaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHaftaBaslik: TextView = view.findViewById(android.R.id.text1)
        val tvGunDetay: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HaftaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return HaftaViewHolder(view)
    }

    override fun onBindViewHolder(holder: HaftaViewHolder, position: Int) {
        val hafta = haftalar[position]

        holder.tvHaftaBaslik.text = "🗓️ 1 Haftalık Program (${hafta.gunler.size} Gün)"
        holder.tvHaftaBaslik.textSize = 17f
        holder.tvHaftaBaslik.setTextColor(android.graphics.Color.parseColor("#4A148C"))
        holder.tvHaftaBaslik.setTypeface(null, android.graphics.Typeface.BOLD)

        val detayBuilder = StringBuilder()
        for (gun in hafta.gunler) {
            detayBuilder.append("📅 ${gun.gunNumarasi}. Gün [Hedef: ${gun.toplamSureMetni}]\n")
            for (ders in gun.dersler) {
                detayBuilder.append("   • ${ders.title} (${ders.duration})\n")
            }
            detayBuilder.append("\n")
        }

        holder.tvGunDetay.text = detayBuilder.toString().trimEnd()
        holder.tvGunDetay.textSize = 14f
        holder.tvGunDetay.setTextColor(android.graphics.Color.parseColor("#212121"))
        holder.tvGunDetay.setLineSpacing(4f, 1.1f)
    }

    override fun getItemCount(): Int = haftalar.size
}