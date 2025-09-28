package com.example.mycarapp.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    fun formatAlbumDate(dateString: String?): String {
        return try {
            if (dateString.isNullOrEmpty()) {
                return "Brak daty"
            }
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.getDefault())
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date: Date? = parser.parse(dateString)
            date?.let { formatter.format(it) } ?: "Brak daty"
        } catch (e: Exception) {
            Log.e("DATE_FORMAT", "Błąd parsowania daty: $dateString", e)
            "Błędna data"
        }
    }
}