package com.cloudnotes.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.cloudnotes.app.api.ApiClient
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var etServerUrl: TextInputEditText
    private lateinit var etToken: TextInputEditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etServerUrl = findViewById(R.id.etServerUrl)
        etToken = findViewById(R.id.etToken)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSave = findViewById(R.id.btnSave)

        // 读取已有配置
        etServerUrl.setText(ApiClient.getBaseUrl(this))
        etToken.setText(ApiClient.getToken(this))

        btnTestConnection.setOnClickListener {
            testConnection()
        }

        btnSave.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            val token = etToken.text.toString().trim()

            if (url.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ApiClient.saveBaseUrl(this, url)
            ApiClient.saveToken(this, token)
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun testConnection() {
        val testUrl = etServerUrl.text.toString().trim()
        val testToken = etToken.text.toString().trim()

        if (testUrl.isEmpty()) {
            Toast.makeText(this, "请输入要测试的服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        btnTestConnection.isEnabled = false
        btnTestConnection.text = "正在连接中..."

        // 临时保存以便测试
        ApiClient.saveBaseUrl(this, testUrl)
        ApiClient.saveToken(this, testToken)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@SettingsActivity).verifyAuth()
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(this@SettingsActivity, "连接成功！云服务器正常响应", Toast.LENGTH_LONG).show()
                } else if (response.code() == 401) {
                    Toast.makeText(this@SettingsActivity, "连接到了服务器，但 Token 错误 (401)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "响应异常: HTTP ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "连接失败: ${e.localizedMessage}\n请检查公网IP及安全列表防火墙设置", Toast.LENGTH_LONG).show()
            } finally {
                btnTestConnection.isEnabled = true
                btnTestConnection.text = getString(R.string.test_connection)
            }
        }
    }
}
