import sqlite3
import os

DB_PATH = os.environ.get("NOTE_DB_PATH", "notes.db")

def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_connection()
    cursor = conn.cursor()

    # 用户表 (含头像 avatar_url)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            avatar_url TEXT DEFAULT '',
            created_at TEXT NOT NULL
        )
    """)

    # 邮箱验证码表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS email_codes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT NOT NULL,
            code TEXT NOT NULL,
            purpose TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            created_at TEXT NOT NULL
        )
    """)

    # 笔记表
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL DEFAULT 1,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            category TEXT DEFAULT '默认',
            is_pinned INTEGER DEFAULT 0,
            is_locked INTEGER DEFAULT 0,
            is_deleted INTEGER DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        )
    """)

    # 兼容历史数据库结构的迁移
    cursor.execute("PRAGMA table_info(users)")
    user_cols = [col["name"] for col in cursor.fetchall()]
    if "avatar_url" not in user_cols:
        cursor.execute("ALTER TABLE users ADD COLUMN avatar_url TEXT DEFAULT ''")

    cursor.execute("PRAGMA table_info(notes)")
    note_cols = [col["name"] for col in cursor.fetchall()]
    if "user_id" not in note_cols:
        cursor.execute("ALTER TABLE notes ADD COLUMN user_id INTEGER NOT NULL DEFAULT 1")
    if "is_locked" not in note_cols:
        cursor.execute("ALTER TABLE notes ADD COLUMN is_locked INTEGER DEFAULT 0")
    if "is_deleted" not in note_cols:
        cursor.execute("ALTER TABLE notes ADD COLUMN is_deleted INTEGER DEFAULT 0")

    conn.commit()
    conn.close()
