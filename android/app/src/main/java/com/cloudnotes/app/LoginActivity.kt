package com.cloudnotes.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var tvForgotPassword: TextView

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
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        tvToggleMode.setOnClickListener {
            toggleMode()
        }

        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
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
            tvToggleMode.text = "已有账号？返回登录"
            tvForgotPassword.visibility = View.GONE
        } else {
            tilUsername.visibility = View.GONE
            tilAccount.hint = "账号 (用户名或电子邮箱)"
            btnSubmit.text = "登 录"
            tvToggleMode.text = "还没有账号？立即注册"
            tvForgotPassword.visibility = View.VISIBLE
        }
    }

    private fun submitAuth() {
        val account = etAccount.text.toString().trim()
        val password = etPassword.text.toString().trim()

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

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val etResetEmail = dialogView.findViewById<EditText>(R.id.etResetEmail)
        val etResetCode = dialogView.findViewById<EditText>(R.id.etResetCode)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val btnSendCode = dialogView.findViewById<Button>(R.id.btnSendCode)

        val dialog = AlertDialog.Builder(this)
            .setTitle("找回密码")
            .setView(dialogView)
            .setPositiveButton("重置密码", null)
            .setNegativeButton("取消", null)
            .create()

        btnSendCode.setOnClickListener {
            val email = etResetEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "请输入你的注册邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSendCode.isEnabled = false
            btnSendCode.text = "发送中..."
            lifecycleScope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        ApiClient.getService(this@LoginActivity).sendCode(mapOf("email" to email, "purpose" to "reset"))
                    }
                    if (res.isSuccessful && res.body()?.success == true) {
                        Toast.makeText(this@LoginActivity, "验证码已发送至邮箱，请查收", Toast.LENGTH_LONG).show()
                        btnSendCode.text = "已发送"
                    } else {
                        Toast.makeText(this@LoginActivity, "发送失败：未找到该邮箱", Toast.LENGTH_SHORT).show()
                        btnSendCode.isEnabled = true
                        btnSendCode.text = "重新发送"
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "发送失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    btnSendCode.isEnabled = true
                    btnSendCode.text = "发送验证码"
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val email = etResetEmail.text.toString().trim()
                val code = etResetCode.text.toString().trim()
                val newPwd = etNewPassword.text.toString().trim()

                if (email.isEmpty() || code.isEmpty() || newPwd.isEmpty()) {
                    Toast.makeText(this, "请填写完整重置信息", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getService(this@LoginActivity).resetPassword(
                                mapOf("email" to email, "code" to code, "new_password" to newPwd)
                            )
                        }
                        if (res.isSuccessful && res.body()?.success == true) {
                            Toast.makeText(this@LoginActivity, "密码重置成功，请使用新密码登录！", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@LoginActivity, "重置失败：验证码错误或已失效", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "重置失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }
}
