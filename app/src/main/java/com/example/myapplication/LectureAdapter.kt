package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Ders videoları listesini Recyclerview üzerinde gösteren köprü
class LectureAdapter(
    private val lectures: List<Lecture>,
    private val elemanaTiklandiginda: (Lecture) -> Unit // Listeden bir videoya tıklandığında çalışacak olay
) : RecyclerView.Adapter<LectureAdapter.LectureViewHolder>() {

    // item_lecture içindeki görsel bileşenleri hafızada tutan kalıp
    class LectureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgThumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)
    }

    // Her liste satırı için item_lectur tasarımını belleğe yüklüyor
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LectureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lecture, parent, false)
        return LectureViewHolder(view)
    }

    // Sıradaki dersin verilerini (bslık süre resim) ekrandaki bileşenlere bağlıyor
    override fun onBindViewHolder(holder: LectureViewHolder, position: Int) {
        val lecture = lectures[position]

        // Videonun başlığını ve süresini TextView'e basar
        holder.txtTitle.text = lecture.title
        holder.txtDuration.text = lecture.duration

        // Kapak resmini internet bağlantısından çekip ImageView içine taşır
        Glide.with(holder.itemView.context)
            .load(lecture.thumbnailUrl)
            .into(holder.imgThumbnail)

        // Kullanıcı bu satıra tıkladığında PlayerActivity de video başlatılıyor
        holder.itemView.setOnClickListener {
            elemanaTiklandiginda(lecture)
        }
    }

    //Listede kaç ders videosu olduğunu bildirir
    override fun getItemCount(): Int {
        return lectures.size
    }
}