import os
from datetime import datetime
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Header, Depends, Query, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.staticfiles import StaticFiles

from app.database import get_connection, init_db
from app.schemas import NoteCreate, NoteUpdate, NoteResponse, ApiResponse

# 初始化数据库
init_db()

app = FastAPI(
    title="Cloud Notes API",
    description="运行在谷歌云 VM 上的轻量云记事本后端",
    version="1.0.0"
)

# 允许跨域请求（方便手机 APP 与 Web 访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

API_TOKEN = os.environ.get("API_TOKEN", "cloud-notes-secret")

def verify_token(
    x_api_token: Optional[str] = Header(None, alias="X-API-Token"),
    token: Optional[str] = Query(None)
):
    # 允许从 Header 或 Query 参数中获取 token
    provided = x_api_token or token
    if not provided or provided != API_TOKEN:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="无效的访问密钥 (Invalid API Token)"
        )
    return True

@app.get("/api/status")
def health_check():
    return {
        "status": "online",
        "service": "Cloud Notes API",
        "server_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }

@app.post("/api/auth/verify", response_model=ApiResponse)
def verify_auth(authorized: bool = Depends(verify_token)):
    return ApiResponse(success=True, message="身份认证成功")

@app.get("/api/notes", response_model=List[NoteResponse])
def get_notes(
    q: Optional[str] = Query(None, description="搜索关键词"),
    category: Optional[str] = Query(None, description="分类筛选"),
    authorized: bool = Depends(verify_token)
):
    conn = get_connection()
    cursor = conn.cursor()
    query = "SELECT * FROM notes WHERE 1=1"
    params = []

    if q:
        query += " AND (title LIKE ? OR content LIKE ?)"
        params.extend([f"%{q}%", f"%{q}%"])

    if category:
        query += " AND category = ?"
        params.append(category)

    # 置顶笔记优先，随后按更新时间倒序
    query += " ORDER BY is_pinned DESC, updated_at DESC"
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()

    return [
        NoteResponse(
            id=row["id"],
            title=row["title"],
            content=row["content"],
            category=row["category"],
            is_pinned=bool(row["is_pinned"]),
            created_at=row["created_at"],
            updated_at=row["updated_at"]
        ) for row in rows
    ]

@app.post("/api/notes", response_model=NoteResponse)
def create_note(note: NoteCreate, authorized: bool = Depends(verify_token)):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """
        INSERT INTO notes (title, content, category, is_pinned, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (note.title, note.content, note.category or "默认", 1 if note.is_pinned else 0, now, now)
    )
    conn.commit()
    note_id = cursor.lastrowid
    conn.close()

    return NoteResponse(
        id=note_id,
        title=note.title,
        content=note.content,
        category=note.category or "默认",
        is_pinned=bool(note.is_pinned),
        created_at=now,
        updated_at=now
    )

@app.get("/api/notes/{note_id}", response_model=NoteResponse)
def get_note(note_id: int, authorized: bool = Depends(verify_token)):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM notes WHERE id = ?", (note_id,))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail="笔记不存在")

    return NoteResponse(
        id=row["id"],
        title=row["title"],
        content=row["content"],
        category=row["category"],
        is_pinned=bool(row["is_pinned"]),
        created_at=row["created_at"],
        updated_at=row["updated_at"]
    )

@app.put("/api/notes/{note_id}", response_model=NoteResponse)
def update_note(note_id: int, note_update: NoteUpdate, authorized: bool = Depends(verify_token)):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM notes WHERE id = ?", (note_id,))
    existing = cursor.fetchone()
    if not existing:
        conn.close()
        raise HTTPException(status_code=404, detail="笔记不存在")

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    title = note_update.title if note_update.title is not None else existing["title"]
    content = note_update.content if note_update.content is not None else existing["content"]
    category = note_update.category if note_update.category is not None else existing["category"]
    is_pinned = 1 if note_update.is_pinned else 0 if note_update.is_pinned is not None else existing["is_pinned"]

    cursor.execute(
        """
        UPDATE notes
        SET title = ?, content = ?, category = ?, is_pinned = ?, updated_at = ?
        WHERE id = ?
        """,
        (title, content, category, is_pinned, now, note_id)
    )
    conn.commit()
    conn.close()

    return NoteResponse(
        id=note_id,
        title=title,
        content=content,
        category=category,
        is_pinned=bool(is_pinned),
        created_at=existing["created_at"],
        updated_at=now
    )

@app.delete("/api/notes/{note_id}", response_model=ApiResponse)
def delete_note(note_id: int, authorized: bool = Depends(verify_token)):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM notes WHERE id = ?", (note_id,))
    deleted = cursor.rowcount > 0
    conn.commit()
    conn.close()

    if not deleted:
        raise HTTPException(status_code=404, detail="笔记不存在")

    return ApiResponse(success=True, message="删除成功")

# 挂载 Web 端静态页面（手机浏览器与电脑均可直接打开）
static_dir = os.path.join(os.path.dirname(__file__), "..", "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

@app.get("/", response_class=HTMLResponse)
def index_page():
    index_file = os.path.join(os.path.dirname(__file__), "..", "static", "index.html")
    if os.path.exists(index_file):
        with open(index_file, "r", encoding="utf-8") as f:
            return f.read()
    return "<h1>Cloud Notes API 运行中</h1><p>请访问 /docs 查看 API 文档</p>"
