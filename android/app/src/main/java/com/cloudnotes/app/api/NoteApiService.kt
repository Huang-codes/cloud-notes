package com.cloudnotes.app.api

import com.cloudnotes.app.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface NoteApiService {

    @POST("api/auth/register")
    suspend fun register(
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @POST("api/auth/send-code")
    suspend fun sendCode(
        @Body body: Map<String, String>
    ): Response<ApiResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(
        @Body body: Map<String, String>
    ): Response<ApiResponse>

    @GET("api/auth/me")
    suspend fun getProfile(): Response<User>

    @GET("api/notes")
    suspend fun getNotes(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null
    ): Response<List<Note>>

    @GET("api/notes/{id}")
    suspend fun getNote(
        @Path("id") id: Long
    ): Response<Note>

    @POST("api/notes")
    suspend fun createNote(
        @Body note: NoteRequest
    ): Response<Note>

    @PUT("api/notes/{id}")
    suspend fun updateNote(
        @Path("id") id: Long,
        @Body note: NoteRequest
    ): Response<Note>

    @DELETE("api/notes/{id}")
    suspend fun deleteNote(
        @Path("id") id: Long
    ): Response<ApiResponse>

    @GET("api/notes/trash/list")
    suspend fun getTrashNotes(): Response<List<Note>>

    @POST("api/notes/{id}/restore")
    suspend fun restoreNote(
        @Path("id") id: Long
    ): Response<ApiResponse>

    @DELETE("api/notes/{id}/permanent")
    suspend fun permanentlyDeleteNote(
        @Path("id") id: Long
    ): Response<ApiResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}
