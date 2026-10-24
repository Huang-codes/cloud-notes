package com.cloudnotes.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.cloudnotes.app.api.ApiClient
import com.cloudnotes.app.util.BiometricHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var layoutAvatar: FrameLayout
    private lateinit var layoutUserInfo: LinearLayout
    private lateinit var cardChangePassword: MaterialCardView
    private lateinit var switchBiometric: SwitchMaterial
    private lateinit var tvDarkModeDesc: TextView
    private lateinit var btnLogout: Button

    companion object {
        private const val REQUEST_PICK_AVATAR = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvUsername = findViewById(R.id.tvUsername)
        tvEmail = findViewById(R.id.tvEmail)
        ivAvatar = findViewById(R.id.ivAvatar)
        layoutAvatar = findViewById(R.id.layoutAvatar)
        layoutUserInfo = findViewById(R.id.layoutUserInfo)
        cardChangePassword = findViewById(R.id.cardChangePassword)
        switchBiometric = findViewById(R.id.switchBiometric)
        tvDarkModeDesc = findViewById(R.id.tvDarkModeDesc)
        btnLogout = findViewById(R.id.btnLogout)

        tvUsername.text = ApiClient.getUsername(this)
        tvEmail.text = ApiClient.getEmail(this).ifEmpty { "未绑定邮箱" }

        // 点击头像更换
        layoutAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_PICK_AVATAR)
        }

        // 点击修改用户名
        layoutUserInfo.setOnClickListener {
            showEditUsernameDialog()
        }

        // 修改登录密码
        cardChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        if (requestCode == REQUEST_PICK_AVATAR) {
            uploadAvatar(data.data!!)
        }
    }

    private fun uploadAvatar(uri: Uri) {
        Toast.makeText(this, "正在上传新头像...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val temp = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(temp)
                    inputStream?.copyTo(outputStream)
                    outputStream.close()
                    inputStream?.close()
                    temp
                }

                val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, reqFile)

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@SettingsActivity).uploadAvatar(body)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    ivAvatar.setImageURI(uri)
                    ApiClient.saveAvatarUrl(this@SettingsActivity, response.body()!!.url)
                    Toast.makeText(this@SettingsActivity, "头像已更新！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "头像上传失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "上传出错: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditUsernameDialog() {
        val etNewName = EditText(this).apply {
            setText(ApiClient.getUsername(this@SettingsActivity))
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("修改用户名")
            .setView(etNewName)
            .setPositiveButton("保存") { _, _ ->
                val newName = etNewName.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getService(this@SettingsActivity).updateProfile(mapOf("username" to newName))
                        }
                        if (res.isSuccessful && res.body() != null) {
                            tvUsername.text = res.body()!!.username
                            ApiClient.saveAuth(
                                this@SettingsActivity,
                                ApiClient.getToken(this@SettingsActivity) ?: "",
                                res.body()!!.username,
                                res.body()!!.email,
                                res.body()!!.avatarUrl ?: ""
                            )
                            Toast.makeText(this@SettingsActivity, "用户名已更新", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SettingsActivity, "修改失败：用户名可能已被占用", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "网络错误: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val etOld = dialogView.findViewById<EditText>(R.id.etOldPassword)
        val etNew = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("修改登录密码")
            .setView(dialogView)
            .setPositiveButton("确认修改") { _, _ ->
                val oldPwd = etOld.text.toString().trim()
                val newPwd = etNew.text.toString().trim()
                val confirmPwd = etConfirm.text.toString().trim()

                if (oldPwd.isEmpty() || newPwd.isEmpty()) {
                    Toast.makeText(this, "请输入完整密码信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPwd != confirmPwd) {
                    Toast.makeText(this, "两次新密码输入不一致", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getService(this@SettingsActivity).changePassword(
                                mapOf("old_password" to oldPwd, "new_password" to newPwd)
                            )
                        }
                        if (res.isSuccessful && res.body()?.success == true) {
                            Toast.makeText(this@SettingsActivity, "密码修改成功！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SettingsActivity, "原密码不正确", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, "修改失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
