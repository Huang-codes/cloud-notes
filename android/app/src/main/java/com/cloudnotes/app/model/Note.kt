package com.cloudnotes.app.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Note(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("title")
    var title: String,

    @SerializedName("content")
    var content: String,

    @SerializedName("category")
    var category: String = "默认",

    @SerializedName("is_pinned")
    var isPinned: Boolean = false,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
) : Serializable

data class NoteRequest(
    val title: String,
    val content: String,
    val category: String,
    @SerializedName("is_pinned")
    val isPinned: Boolean
)

data class ApiResponse(
    val success: Boolean,
    val message: String
)
