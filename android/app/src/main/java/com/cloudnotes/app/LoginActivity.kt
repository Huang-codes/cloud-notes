package com.cloudnotes.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cloudnotes.app.api.ApiClient
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private var isRegisterMode = false

    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilAccount: TextInputLayout
    private lateinit var etUsername: TextInputEditText
    private lateinit var etAccount: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var tvToggleMode: TextView
    private lateinit var tvToggleServerConfig: TextView
    private lateinit var layoutServerConfig: LinearLayout
    private lateinit var etServerUrl: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tilUsername = findViewById(R.id.tilUsername)
        tilAccount = findViewById(R.id.tilAccount)
        etUsername = findViewById(R.id.etUsername)
        etAccount = findViewById(R.id.etAccount)
        etPassword = findViewById(R.id.etPassword)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvToggleMode = findViewById(R.id.tvToggleMode)
        tvToggleServerConfig = findViewById(R.id.tvToggleServerConfig)
        layoutServerConfig = findViewById(R.id.layoutServerConfig)
        etServerUrl = findViewById(R.id.etServerUrl)

        etServerUrl.setText(ApiClient.getBaseUrl(this))

        tvToggleMode.setOnClickListener {
            toggleMode()
        }

        tvToggleServerConfig.setOnClickListener {
            if (layoutServerConfig.visibility == View.VISIBLE) {
                layoutServerConfig.visibility = View.GONE
                tvToggleServerConfig.text = "高级设置：自定义服务器地址 ▼"
            } else {
                layoutServerConfig.visibility = View.VISIBLE
                tvToggleServerConfig.text = "收起高级设置 ▲"
            }
        }

        btnSubmit.setOnClickListener {
            submitAuth()
        }
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        if (isRegisterMode) {
            tilUsername.visibility = View.VISIBLE
            tilAccount.hint = "电子邮箱"
            btnSubmit.text = "注 册"
            tvToggleMode.text = "已有账号？点击返回登录"
        } else {
            tilUsername.visibility = View.GONE
            tilAccount.hint = "账号 (用户名或电子邮箱)"
            btnSubmit.text = "登 录"
            tvToggleMode.text = "还没有账号？点击立即注册"
        }
    }

    private fun submitAuth() {
        val serverUrl = etServerUrl.text.toString().trim()
        val account = etAccount.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (serverUrl.isNotEmpty()) {
            ApiClient.saveBaseUrl(this, serverUrl)
        }

        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账号与密码", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRegisterMode) {
            val username = etUsername.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                return
            }
            doRegister(username, account, password)
        } else {
            doLogin(account, password)
        }
    }

    private fun doLogin(account: String, password: String) {
        btnSubmit.isEnabled = false
        btnSubmit.text = "正在登录..."

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@LoginActivity)
                val body = mapOf("account" to account, "password" to password)
                val response = withContext(Dispatchers.IO) {
                    service.login(body)
                }

                if (response.isSuccessful && response.body() != null) {
                    val auth = response.body()!!
                    ApiClient.saveAuth(this@LoginActivity, auth.accessToken, auth.user.username, auth.user.email)
                    Toast.makeText(this@LoginActivity, "欢迎回来，${auth.user.username}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "登录失败"
                    Toast.makeText(this@LoginActivity, "登录失败: 账号或密码错误", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "网络错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSubmit.isEnabled = true
                btnSubmit.text = "登 录"
            }
        }
    }

    private fun doRegister(username: String, email: String, password: String) {
        btnSubmit.isEnabled = false
        btnSubmit.text = "正在注册..."

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@LoginActivity)
                val body = mapOf("username" to username, "email" to email, "password" to password)
                val response = withContext(Dispatchers.IO) {
                    service.register(body)
                }

                if (response.isSuccessful && response.body() != null) {
                    val auth = response.body()!!
                    ApiClient.saveAuth(this@LoginActivity, auth.accessToken, auth.user.username, auth.user.email)
                    Toast.makeText(this@LoginActivity, "注册成功，欢迎加入橙子笔记！", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "注册失败：用户名或邮箱可能已被占用", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "网络错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                btnSubmit.isEnabled = true
                btnSubmit.text = "注 册"
            }
        }
    }
}
