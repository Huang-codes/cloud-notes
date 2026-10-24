package com.cloudnotes.app.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cloudnotes.app.model.Note

class LocalNoteDbHelper(context: Context) : SQLiteOpenHelper(context, "orange_notes_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.executeSQL("""
            CREATE TABLE local_notes (
                id INTEGER PRIMARY KEY,
                user_id INTEGER,
                title TEXT,
                content TEXT,
                category TEXT,
                is_pinned INTEGER,
                is_locked INTEGER,
                is_deleted INTEGER,
                created_at TEXT,
                updated_at TEXT,
                is_synced INTEGER DEFAULT 1
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.executeSQL("DROP TABLE IF EXISTS local_notes")
        onCreate(db)
    }

    fun saveNotes(notes: List<Note>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (note in notes) {
                val cv = ContentValues().apply {
                    put("id", note.id)
                    put("user_id", note.userId)
                    put("title", note.title)
                    put("content", note.content)
                    put("category", note.category)
                    put("is_pinned", if (note.isPinned) 1 else 0)
                    put("is_locked", if (note.isLocked) 1 else 0)
                    put("is_deleted", if (note.isDeleted) 1 else 0)
                    put("created_at", note.createdAt)
                    put("updated_at", note.updatedAt)
                    put("is_synced", 1)
                }
                db.insertWithOnConflict("local_notes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllNotes(query: String? = null): List<Note> {
        val list = mutableListOf<Note>()
        val db = readableDatabase
        var sql = "SELECT * FROM local_notes WHERE is_deleted = 0"
        val params = mutableListOf<String>()

        if (!query.isNullOrBlank()) {
            sql += " AND (title LIKE ? OR content LIKE ?)"
            params.add("%$query%")
            params.add("%$query%")
        }
        sql += " ORDER BY is_pinned DESC, updated_at DESC"

        val cursor = db.rawQuery(sql, params.toTypedArray())
        while (cursor.moveToNext()) {
            list.add(
                Note(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    userId = cursor.getLong(cursor.getColumnIndexOrThrow("user_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1,
                    isLocked = cursor.getInt(cursor.getColumnIndexOrThrow("is_locked")) == 1,
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_deleted")) == 1,
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                    updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"))
                )
            )
        }
        cursor.close()
        return list
    }

    fun clearAll() {
        writableDatabase.delete("local_notes", null, null)
    }
}
