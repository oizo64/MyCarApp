package com.example.mycarapp.adapters

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.mycarapp.R
import com.example.mycarapp.dto.Album
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.graphics.toColorInt
import com.example.mycarapp.utils.DateFormatter

// Definiujemy interfejs, który będzie nasłuchiwał na kliknięcia
interface OnItemClickListener {
    fun onItemClick(album: Album)
}

// Zmieniamy konstruktor adaptera, aby przyjmował instancję słuchacza
class AlbumsAdapter(
    private val albums: List<Album>,
    private val listener: OnItemClickListener // Dodajemy nowy parametr
) : RecyclerView.Adapter<AlbumsAdapter.AlbumViewHolder>() {

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_view)
        val albumCoverImageView: ImageView = itemView.findViewById(R.id.album_cover_image_view)
        val albumNameTextView: TextView = itemView.findViewById(R.id.album_name_text_view)
        val albumArtistTextView: TextView = itemView.findViewById(R.id.album_artist_text_view)
        val albumCreatedTextView: TextView = itemView.findViewById(R.id.album_created_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]

        holder.albumNameTextView.text = album.name
        holder.albumArtistTextView.text = "Artysta: ${album.albumArtist}"
        holder.albumCreatedTextView.text = "Created: ${formatDate(album.createdAt)}"

        holder.albumCoverImageView.load(album.coverArtUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder_album)
            error(R.drawable.ic_error_album)
            transformations(RoundedCornersTransformation(16f))
        }

        if (isWithinLastSevenDays(album.createdAt)) {
            holder.cardView.setCardBackgroundColor("#E8F5E9".toColorInt())
        } else {
            val typedValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            holder.cardView.setCardBackgroundColor(typedValue.data)
        }

        // Dodajemy listener do całego widoku elementu
        holder.itemView.setOnClickListener {
            listener.onItemClick(album)
        }
    }

    override fun getItemCount(): Int {
        return albums.size
    }

    fun formatDate(dateString: String?): String {
        return DateFormatter.formatAlbumDate(dateString)
    }

    private fun isWithinLastSevenDays(dateString: String?): Boolean {
        if (dateString.isNullOrEmpty()) {
            return false
        }
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", Locale.getDefault())
            val albumDate = parser.parse(dateString)
            val currentDate = Date()
            val diffInMillis = albumDate?.time?.let { albumTime ->
                currentDate.time - albumTime
            } ?: -1
            val diffInDays = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS)
            diffInDays <= 7
        } catch (e: Exception) {
            Log.e("DATE_CHECK", "Błąd sprawdzania daty: $dateString", e)
            false
        }
    }
}