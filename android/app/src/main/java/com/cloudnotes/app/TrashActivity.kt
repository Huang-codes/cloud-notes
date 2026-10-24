package com.cloudnotes.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudnotes.app.adapter.NoteAdapter
import com.cloudnotes.app.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerViewTrash)
        emptyView = findViewById(R.id.emptyView)

        adapter = NoteAdapter(emptyList()) { note ->
            showActionDialog(note.id, note.title)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadTrash()
    }

    private fun loadTrash() {
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@TrashActivity)
                val res = withContext(Dispatchers.IO) {
                    service.getTrashNotes()
                }
                if (res.isSuccessful) {
                    val notes = res.body() ?: emptyList()
                    adapter.updateData(notes)
                    if (notes.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@TrashActivity, "加载回收站失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showActionDialog(noteId: Long, noteTitle: String) {
        val options = arrayOf("还原笔记", "彻底删除")
        AlertDialog.Builder(this)
            .setTitle(noteTitle.ifEmpty { "未命名笔记" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> restore(noteId)
                    1 -> permanentDelete(noteId)
                }
            }
            .show()
    }

    private fun restore(noteId: Long) {
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getService(this@TrashActivity).restoreNote(noteId)
                }
                if (res.isSuccessful) {
                    Toast.makeText(this@TrashActivity, "已还原", Toast.LENGTH_SHORT).show()
                    loadTrash()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TrashActivity, "还原失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun permanentDelete(noteId: Long) {
        AlertDialog.Builder(this)
            .setTitle("确认彻底删除")
            .setMessage("彻底删除后将无法恢复，确定删除吗？")
            .setPositiveButton("确定删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.getService(this@TrashActivity).permanentlyDeleteNote(noteId)
                        }
                        if (res.isSuccessful) {
                            Toast.makeText(this@TrashActivity, "已彻底删除", Toast.LENGTH_SHORT).show()
                            loadTrash()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@TrashActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
