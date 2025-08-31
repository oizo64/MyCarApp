package com.example.mycarapp.dto

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@SuppressLint("ParcelCreator")
@Parcelize
data class Album(
    val id: String = "",
    val name: String,
    val coverArtUrl: String? = null,
    val albumArtist: String = "",
    val playCount: Int? = null,
    val playDate: String? = null,
    val starredAt: String? = null,
    val libraryId: Int = 0,
    val libraryPath: String = "",
    val libraryName: String = "",
    val maxYear: Int = 0,
    val minYear: Int = 0,
    val date: String = "",
    val maxOriginalYear: Int = 0,
    val minOriginalYear: Int = 0,
    val releaseDate: String = "",
    val compilation: Boolean = false,
    val songCount: Int = 0,
    val duration: Double = 0.0,
    val size: Long = 0L,
    val discs: Map<String, String> = emptyMap(),
    val orderAlbumName: String = "",
    val orderAlbumArtistName: String = "",
    val explicitStatus: String = "",
    val externalInfoUpdatedAt: String? = null,
    val genre: String = "",
    val genres: List<Genre> = emptyList(),
    val tags: Tags = Tags(emptyList(), emptyList()),
    val participants: Participants = Participants(emptyList(), emptyList(), emptyList()),
    val missing: Boolean = false,
    val importedAt: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable

@Parcelize
data class Genre(
    val id: String,
    val name: String
) : Parcelable

@Parcelize
data class Tags(
    val genre: List<String>,
    val grouping: List<String>
) : Parcelable

@Parcelize
data class Participants(
    val albumartist: List<Participant>,
    val artist: List<Participant>,
    val composer: List<Participant>
) : Parcelable

@Parcelize
data class Participant(
    val id: String,
    val name: String,
    val missing: Boolean
) : Parcelable