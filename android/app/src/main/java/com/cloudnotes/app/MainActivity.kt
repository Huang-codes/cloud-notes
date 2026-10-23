package com.cloudnotes.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cloudnotes.app.adapter.NoteAdapter
import com.cloudnotes.app.api.ApiClient
import com.cloudnotes.app.model.Note
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

    private var currentQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    override fun onResume() {
        super.onResume()
        loadNotes(currentQuery)
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
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadNotes(query: String? = null) {
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@MainActivity)
                val response = withContext(Dispatchers.IO) {
                    service.getNotes(query = query?.ifBlank { null })
                }

                if (response.isSuccessful) {
                    val notes = response.body() ?: emptyList()
                    adapter.updateData(notes)

                    if (notes.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                } else if (response.code() == 401) {
                    Toast.makeText(this@MainActivity, "访问密钥错误，请在设置中配置", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "获取笔记失败: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                emptyView.text = "无法连接到服务器\n请在右上角设置中检查IP与Token\n(${e.localizedMessage})"
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}
