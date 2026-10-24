package com.cloudnotes.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.cloudnotes.app.api.ApiClient
import com.cloudnotes.app.model.Note
import com.cloudnotes.app.model.NoteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class NoteDetailActivity : AppCompatActivity() {

    private var currentNote: Note? = null
    private var isAutoSaving = false

    private lateinit var etTitle: EditText
    private lateinit var etCategory: EditText
    private lateinit var etContent: EditText
    private lateinit var cbPinned: CheckBox
    private lateinit var tvSaveStatus: TextView

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable {
        saveNote(isAuto = true)
    }

    companion object {
        private const val REQUEST_PICK_IMAGE = 101
        private const val REQUEST_TAKE_PHOTO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etTitle = findViewById(R.id.etTitle)
        etCategory = findViewById(R.id.etCategory)
        etContent = findViewById(R.id.etContent)
        cbPinned = findViewById(R.id.cbPinned)
        tvSaveStatus = findViewById(R.id.tvSaveStatus)

        // 绑定富文本与待办快捷工具栏
        findViewById<ImageButton>(R.id.btnBold).setOnClickListener { insertFormat("**", "**", "加粗内容") }
        findViewById<ImageButton>(R.id.btnItalic).setOnClickListener { insertFormat("*", "*", "斜体内容") }
        findViewById<ImageButton>(R.id.btnChecklist).setOnClickListener { insertFormat("- [ ] ", "", "待办事项") }
        findViewById<ImageButton>(R.id.btnPhoto).setOnClickListener { pickPhoto() }
        findViewById<ImageButton>(R.id.btnCamera).setOnClickListener { takePhoto() }

        @Suppress("DEPRECATION")
        currentNote = intent.getSerializableExtra("EXTRA_NOTE") as? Note

        if (currentNote != null) {
            supportActionBar?.title = getString(R.string.title_edit_note)
            etTitle.setText(currentNote?.title)
            etCategory.setText(currentNote?.category)
            etContent.setText(currentNote?.content)
            cbPinned.isChecked = currentNote?.isPinned ?: false
        } else {
            supportActionBar?.title = getString(R.string.title_new_note)
        }

        // 监听输入触发自动保存
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvSaveStatus.visibility = View.GONE
                autoSaveHandler.removeCallbacks(autoSaveRunnable)
                autoSaveHandler.postDelayed(autoSaveRunnable, 1000) // 停顿 1 秒自动存
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etTitle.addTextChangedListener(watcher)
        etContent.addTextChangedListener(watcher)
        etCategory.addTextChangedListener(watcher)
        cbPinned.setOnCheckedChangeListener { _, _ ->
            autoSaveHandler.removeCallbacks(autoSaveRunnable)
            autoSaveHandler.post(autoSaveRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        // 离开页面时强制自动保存
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        saveNote(isAuto = true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        if (currentNote == null) {
            menu?.findItem(R.id.action_delete)?.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveNote(isAuto = false)
                finish()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveNote(isAuto: Boolean) {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()
        val category = etCategory.text.toString().trim().ifEmpty { "默认" }
        val isPinned = cbPinned.isChecked

        if (title.isEmpty() && content.isEmpty()) return

        val finalTitle = title.ifEmpty { "未命名笔记" }
        val request = NoteRequest(
            title = finalTitle,
            content = content,
            category = category,
            isPinned = isPinned
        )

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@NoteDetailActivity)
                val response = withContext(Dispatchers.IO) {
                    if (currentNote != null && currentNote!!.id > 0) {
                        service.updateNote(currentNote!!.id, request)
                    } else {
                        service.createNote(request)
                    }
                }

                if (response.isSuccessful && response.body() != null) {
                    currentNote = response.body()
                    tvSaveStatus.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                // 静默失败，避免打断输入
            }
        }
    }

    private fun confirmDelete() {
        if (currentNote == null) return
        AlertDialog.Builder(this)
            .setTitle("移入回收站")
            .setMessage("确定将此笔记移入回收站吗？你可以随时在回收站还原它。")
            .setPositiveButton("移入回收站") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteNote() {
        val noteId = currentNote?.id ?: return
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@NoteDetailActivity).deleteNote(noteId)
                }
                if (res.isSuccessful) {
                    Toast.makeText(this@NoteDetailActivity, "已移入回收站", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NoteDetailActivity, "删除失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun insertFormat(prefix: String, suffix: String, defaultText: String) {
        val start = etContent.selectionStart
        val end = etContent.selectionEnd
        val original = etContent.text.toString()
        val selected = if (start != end && start >= 0 && end <= original.length) {
            original.substring(start, end)
        } else {
            defaultText
        }
        val replacement = prefix + selected + suffix
        etContent.text.replace(start.coerceAtLeast(0), end.coerceAtLeast(0), replacement)
    }

    private fun pickPhoto() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_TAKE_PHOTO)
        } else {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == REQUEST_PICK_IMAGE && data?.data != null) {
            uploadAndInsertImage(data.data!!)
        }
    }

    private fun uploadAndInsertImage(uri: Uri) {
        Toast.makeText(this, "正在上传图片...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val temp = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(temp)
                    inputStream?.copyTo(outputStream)
                    outputStream.close()
                    inputStream?.close()
                    temp
                }

                val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, reqFile)

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@NoteDetailActivity).uploadFile(body)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    val fullUrl = ApiClient.getBaseUrl(this@NoteDetailActivity).removeSuffix("/") + response.body()!!.url
                    val mdImage = "\n![图片]($fullUrl)\n"
                    etContent.append(mdImage)
                    Toast.makeText(this@NoteDetailActivity, "图片上传成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@NoteDetailActivity, "上传失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NoteDetailActivity, "上传出错: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
