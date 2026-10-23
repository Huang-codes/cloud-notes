package com.cloudnotes.app.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val PREFS_NAME = "cloud_notes_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "api_token"

    private const val DEFAULT_URL = "http://10.0.2.2:8000/" // Android 模拟器访问本机，或替换为谷歌云公网 IP
    private const val DEFAULT_TOKEN = "cloud-notes-secret"

    private var apiService: NoteApiService? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(context: Context): String {
        var url = getPrefs(context).getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    fun saveBaseUrl(context: Context, url: String) {
        var formatted = url.trim()
        if (!formatted.endsWith("/")) {
            formatted += "/"
        }
        getPrefs(context).edit().putString(KEY_BASE_URL, formatted).apply()
        apiService = null // 重置实例
    }

    fun getToken(context: Context): String {
        return getPrefs(context).getString(KEY_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
    }

    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
        apiService = null // 重置实例
    }

    fun getService(context: Context): NoteApiService {
        if (apiService == null) {
            val token = getToken(context)
            val baseUrl = getBaseUrl(context)

            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-API-Token", token)
                    .build()
                chain.proceed(request)
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(NoteApiService::class.java)
        }
        return apiService!!
    }
}
