package com.cloudnotes.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.cloudnotes.app.api.ApiClient
import com.cloudnotes.app.util.BiometricHelper
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var switchBiometric: SwitchMaterial
    private lateinit var tvDarkModeDesc: TextView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvUsername = findViewById(R.id.tvUsername)
        tvEmail = findViewById(R.id.tvEmail)
        switchBiometric = findViewById(R.id.switchBiometric)
        tvDarkModeDesc = findViewById(R.id.tvDarkModeDesc)
        btnLogout = findViewById(R.id.btnLogout)

        tvUsername.text = ApiClient.getUsername(this)
        tvEmail.text = ApiClient.getEmail(this).ifEmpty { "未绑定邮箱" }

        // 指纹/应用锁
        switchBiometric.isChecked = ApiClient.isBiometricEnabled(this)
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !BiometricHelper.isBiometricAvailable(this)) {
                switchBiometric.isChecked = false
                Toast.makeText(this, "当前设备不支持或未录入生物识别/锁屏密码", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            ApiClient.setBiometricEnabled(this, isChecked)
        }

        // 深色模式选择
        updateDarkModeDesc()
        tvDarkModeDesc.setOnClickListener {
            showDarkModeDialog()
        }

        // 退出登录
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认退出")
                .setMessage("退出登录后将返回登录界面。")
                .setPositiveButton("退出") { _, _ ->
                    ApiClient.logout(this)
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun updateDarkModeDesc() {
        val mode = ApiClient.getDarkModePref(this)
        tvDarkModeDesc.text = when (mode) {
            1 -> "浅色模式"
            2 -> "深色模式"
            else -> "跟随系统"
        }
    }

    private fun showDarkModeDialog() {
        val options = arrayOf("跟随系统主题", "浅色模式", "深色模式")
        val current = ApiClient.getDarkModePref(this)
        AlertDialog.Builder(this)
            .setTitle("深色模式")
            .setSingleChoiceItems(options, current) { dialog, which ->
                ApiClient.setDarkModePref(this, which)
                updateDarkModeDesc()
                when (which) {
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                dialog.dismiss()
            }
            .show()
    }
}
