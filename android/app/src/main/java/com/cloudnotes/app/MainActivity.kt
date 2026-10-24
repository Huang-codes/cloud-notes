package com.cloudnotes.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cloudnotes.app.adapter.NoteAdapter
import com.cloudnotes.app.api.ApiClient
import com.cloudnotes.app.db.LocalNoteDbHelper
import com.cloudnotes.app.model.Note
import com.cloudnotes.app.util.BiometricHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var searchView: SearchView
    private lateinit var adapter: NoteAdapter
    private lateinit var dbHelper: LocalNoteDbHelper

    private var currentQuery: String? = null
    private var isBiometricAuthenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 应用深色模式偏好
        when (ApiClient.getDarkModePref(this)) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // 检查登录状态
        if (!ApiClient.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        dbHelper = LocalNoteDbHelper(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        fabAdd = findViewById(R.id.fabAdd)
        searchView = findViewById(R.id.searchView)

        setupRecyclerView()
        setupSearchView()

        swipeRefreshLayout.setOnRefreshListener {
            loadNotes(currentQuery)
        }

        fabAdd.setOnClickListener {
            val intent = Intent(this, NoteDetailActivity::class.java)
            startActivity(intent)
        }

        // 检查生物识别隐私锁
        if (ApiClient.isBiometricEnabled(this) && !isBiometricAuthenticated) {
            BiometricHelper.showBiometricPrompt(
                activity = this,
                onSuccess = {
                    isBiometricAuthenticated = true
                    loadNotes(currentQuery)
                },
                onError = {
                    Toast.makeText(this, "安全验证失败，请重新进入", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (ApiClient.isLoggedIn(this)) {
            loadNotes(currentQuery)
        }
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(emptyList()) { note ->
            val intent = Intent(this, NoteDetailActivity::class.java).apply {
                putExtra("EXTRA_NOTE", note)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = query
                loadNotes(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText
                loadNotes(newText)
                return true
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_trash -> {
                startActivity(Intent(this, TrashActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadNotes(query: String? = null) {
        // 1. 离线优先：瞬间加载本地缓存
        val localList = dbHelper.getAllNotes(query)
        if (localList.isNotEmpty()) {
            adapter.updateData(localList)
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        // 2. 云端异步刷新并同步
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@MainActivity)
                val response = withContext(Dispatchers.IO) {
                    service.getNotes(query = query?.ifBlank { null })
                }

                if (response.isSuccessful && response.body() != null) {
                    val notes = response.body()!!
                    // 存入本地缓存
                    withContext(Dispatchers.IO) {
                        dbHelper.saveNotes(notes)
                    }
                    adapter.updateData(notes)

                    if (notes.isEmpty()) {
                        emptyView.text = "暂无笔记\n点击右下角添加你的第一条笔记"
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                } else if (response.code() == 401) {
                    ApiClient.logout(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                // 处于离线模式
                if (localList.isEmpty()) {
                    emptyView.text = "当前处于离线模式\n暂未缓存笔记"
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}
