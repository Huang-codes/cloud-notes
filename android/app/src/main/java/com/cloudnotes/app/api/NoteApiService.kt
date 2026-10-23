package com.cloudnotes.app.api

import com.cloudnotes.app.model.ApiResponse
import com.cloudnotes.app.model.Note
import com.cloudnotes.app.model.NoteRequest
import retrofit2.Response
import retrofit2.http.*

interface NoteApiService {

    @POST("api/auth/verify")
    suspend fun verifyAuth(): Response<ApiResponse>

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
}
