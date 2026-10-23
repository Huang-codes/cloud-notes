package com.cloudnotes.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
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

class NoteDetailActivity : AppCompatActivity() {

    private var currentNote: Note? = null
    private lateinit var etTitle: EditText
    private lateinit var etCategory: EditText
    private lateinit var etContent: EditText
    private lateinit var cbPinned: CheckBox

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

        // 接收传递的 Note 数据
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
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        // 新建模式下隐藏删除按钮
        if (currentNote == null) {
            menu?.findItem(R.id.action_delete)?.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveNote()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveNote() {
        val title = etTitle.text.toString().trim()
        val content = etContent.text.toString().trim()
        val category = etCategory.text.toString().trim().ifEmpty { "默认" }
        val isPinned = cbPinned.isChecked

        if (title.isEmpty()) {
            Toast.makeText(this, "请输入笔记标题", Toast.LENGTH_SHORT).show()
            return
        }

        val request = NoteRequest(
            title = title,
            content = content,
            category = category,
            isPinned = isPinned
        )

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@NoteDetailActivity)
                val response = withContext(Dispatchers.IO) {
                    if (currentNote != null) {
                        service.updateNote(currentNote!!.id, request)
                    } else {
                        service.createNote(request)
                    }
                }

                if (response.isSuccessful) {
                    Toast.makeText(this@NoteDetailActivity, "保存成功", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@NoteDetailActivity, "保存失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NoteDetailActivity, "网络错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        if (currentNote == null) return

        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteNote() {
        val noteId = currentNote?.id ?: return
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@NoteDetailActivity).deleteNote(noteId)
                }

                if (response.isSuccessful) {
                    Toast.makeText(this@NoteDetailActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@NoteDetailActivity, "删除失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NoteDetailActivity, "网络错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
