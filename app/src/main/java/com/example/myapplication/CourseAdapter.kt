package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Kurs listesini yatay kartlar halinde ekrana basan ve tıklamayı dinleyen adaptör
class CourseAdapter(
    private val courseList: List<Course>,
    private val kursaTiklandiginda: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivCourseThumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tvCourseTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courseList[position]
        holder.tvTitle.text = course.title

        // Kapak resmi url si varsa glide ile yüklüyor
        if (course.thumbnailUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(course.thumbnailUrl)
                .into(holder.ivThumbnail)
        }

        // Karta tıklandığında seçilen kursu MainActivitye bildiriyor
        holder.itemView.setOnClickListener {
            kursaTiklandiginda(course)
        }
    }

    override fun getItemCount(): Int = courseList.size
}