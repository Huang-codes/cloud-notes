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

    private const val PREFS_NAME = "orange_notes_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USERNAME = "current_username"
    private const val KEY_EMAIL = "current_email"
    private const val KEY_AVATAR = "current_avatar"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_DARK_MODE = "dark_mode_pref" // 0: 跟随系统, 1: 浅色, 2: 深色

    // 默认内置云服务器地址 (使用已绑定的专属域名)
    private const val DEFAULT_URL = "https://muchen.krystal1122.dpdns.org/"

    private var apiService: NoteApiService? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBaseUrl(context: Context): String {
        var url = getPrefs(context).getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
        if (url.contains("161.153.58.223") || url.contains("eric.krystal1122.dpdns.org")) {
            url = DEFAULT_URL
            saveBaseUrl(context, url)
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun saveBaseUrl(context: Context, url: String) {
        var formatted = url.trim()
        if (!formatted.endsWith("/")) formatted += "/"
        getPrefs(context).edit().putString(KEY_BASE_URL, formatted).apply()
        apiService = null
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    fun saveAuth(context: Context, token: String, username: String, email: String, avatarUrl: String = "") {
        getPrefs(context).edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_AVATAR, avatarUrl)
            .apply()
        apiService = null
    }

    fun getAvatarUrl(context: Context): String {
        return getPrefs(context).getString(KEY_AVATAR, "") ?: ""
    }

    fun saveAvatarUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_AVATAR, url).apply()
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "橙子用户") ?: "橙子用户"
    }

    fun getEmail(context: Context): String {
        return getPrefs(context).getString(KEY_EMAIL, "") ?: ""
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    fun logout(context: Context) {
        getPrefs(context).edit().remove(KEY_TOKEN).remove(KEY_USERNAME).remove(KEY_EMAIL).apply()
        apiService = null
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getDarkModePref(context: Context): Int {
        return getPrefs(context).getInt(KEY_DARK_MODE, 0)
    }

    fun setDarkModePref(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_DARK_MODE, mode).apply()
    }

    fun getService(context: Context): NoteApiService {
        if (apiService == null) {
            val token = getToken(context)
            val baseUrl = getBaseUrl(context)

            val authInterceptor = Interceptor { chain ->
                val builder = chain.request().newBuilder()
                if (!token.isNullOrBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
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
