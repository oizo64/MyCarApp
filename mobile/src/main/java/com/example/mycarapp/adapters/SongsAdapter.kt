package com.example.mycarapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mycarapp.R
import com.example.mycarapp.dto.SongResponse

class SongsAdapter(private val songs: List<SongResponse>) :
    RecyclerView.Adapter<SongsAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songNameTextView: TextView = itemView.findViewById(R.id.song_name_text_view)
        val songArtistTextView: TextView = itemView.findViewById(R.id.song_artist_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.songNameTextView.text = song.name
        holder.songArtistTextView.text = song.artist
    }

    override fun getItemCount(): Int = songs.size
}