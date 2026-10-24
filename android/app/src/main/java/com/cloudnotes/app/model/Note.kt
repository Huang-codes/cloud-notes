package com.cloudnotes.app.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Note(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("user_id")
    val userId: Long = 0,

    @SerializedName("title")
    var title: String,

    @SerializedName("content")
    var content: String,

    @SerializedName("category")
    var category: String = "默认",

    @SerializedName("is_pinned")
    var isPinned: Boolean = false,

    @SerializedName("is_locked")
    var isLocked: Boolean = false,

    @SerializedName("is_deleted")
    var isDeleted: Boolean = false,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    var updatedAt: String? = null
) : Serializable

data class NoteRequest(
    val title: String,
    val content: String,
    val category: String,
    @SerializedName("is_pinned")
    val isPinned: Boolean,
    @SerializedName("is_locked")
    val isLocked: Boolean = false
)

data class User(
    val id: Long,
    val username: String,
    val email: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    val user: User
)

data class ApiResponse(
    val success: Boolean,
    val message: String
)

data class UploadResponse(
    val success: Boolean,
    val url: String,
    val filename: String
)
