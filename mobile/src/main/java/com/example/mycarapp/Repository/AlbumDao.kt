package com.example.mycarapp.Repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mycarapp.dto.Album
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    suspend fun getAllAlbumsSync(): List<Album>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)

    @Query("UPDATE albums SET lastPlaybackPosition = :position WHERE id = :albumId")
    suspend fun updatePlaybackPosition(albumId: String, position: Long)

    @Query("SELECT lastPlaybackPosition FROM albums WHERE id = :albumId")
    suspend fun getPlaybackPosition(albumId: String): Long?

    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}
