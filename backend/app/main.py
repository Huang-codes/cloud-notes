import os
import uuid
import shutil
from datetime import datetime
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Header, Depends, Query, status, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

from app.database import get_connection, init_db
from app.auth import hash_password, verify_password, create_access_token, decode_access_token
from app.schemas import (
    UserRegisterRequest, UserLoginRequest, UserResponse, AuthTokenResponse,
    NoteCreate, NoteUpdate, NoteResponse, ApiResponse, UploadResponse
)

init_db()

UPLOAD_DIR = os.environ.get("NOTE_UPLOAD_DIR", os.path.join(os.path.dirname(__file__), "..", "uploads"))
os.makedirs(UPLOAD_DIR, exist_ok=True)

app = FastAPI(
    title="橙子笔记 API",
    description="橙子笔记 (Orange Notes) 现代云端笔记服务",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 挂载上传文件服务
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")

def get_current_user(
    authorization: Optional[str] = Header(None),
    x_api_token: Optional[str] = Header(None, alias="X-API-Token")
) -> dict:
    """从 Authorization Bearer 或 Token 解析当前用户"""
    token = None
    if authorization and authorization.startswith("Bearer "):
        token = authorization[7:].strip()
    elif x_api_token:
        token = x_api_token.strip()

    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="请先登录橙子笔记账号"
        )

    # 优先解析 JWT Token
    payload = decode_access_token(token)
    if payload and "uid" in payload:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT id, username, email, created_at FROM users WHERE id = ?", (payload["uid"],))
        user = cursor.fetchone()
        conn.close()
        if user:
            return dict(user)

    # 兼容历史密钥模式（自动关联到默认管理员账号）
    legacy_token = os.environ.get("API_TOKEN", "cloud-notes-secret")
    if token == legacy_token:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT id, username, email, created_at FROM users ORDER BY id ASC LIMIT 1")
        first_user = cursor.fetchone()
        if not first_user:
            # 自动初始化默认用户
            pwd_hash, salt = hash_password("orange123")
            now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            cursor.execute(
                "INSERT INTO users (username, email, password_hash, salt, created_at) VALUES (?, ?, ?, ?, ?)",
                ("orange", "admin@orange.local", pwd_hash, salt, now)
            )
            conn.commit()
            cursor.execute("SELECT id, username, email, created_at FROM users WHERE id = ?", (cursor.lastrowid,))
            first_user = cursor.fetchone()
        conn.close()
        return dict(first_user)

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="登录凭据无效或已过期，请重新登录")

# ----------------- 账号系统 (用户注册、登录、信息) -----------------

@app.post("/api/auth/register", response_model=AuthTokenResponse)
def register(req: UserRegisterRequest):
    conn = get_connection()
    cursor = conn.cursor()

    # 检查用户名与邮箱是否已存在
    cursor.execute("SELECT id FROM users WHERE username = ? OR email = ?", (req.username, req.email))
    if cursor.fetchone():
        conn.close()
        raise HTTPException(status_code=400, detail="用户名或邮箱已被注册")

    pwd_hash, salt = hash_password(req.password)
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cursor.execute(
        "INSERT INTO users (username, email, password_hash, salt, created_at) VALUES (?, ?, ?, ?, ?)",
        (req.username.strip(), req.email.strip().lower(), pwd_hash, salt, now)
    )
    conn.commit()
    user_id = cursor.lastrowid
    conn.close()

    token = create_access_token(user_id, req.username)
    return AuthTokenResponse(
        access_token=token,
        user=UserResponse(id=user_id, username=req.username, email=req.email, created_at=now)
    )

@app.post("/api/auth/login", response_model=AuthTokenResponse)
def login(req: UserLoginRequest):
    account = req.account.strip()
    conn = get_connection()
    cursor = conn.cursor()
    # 支持用户名或邮箱登录
    cursor.execute("SELECT * FROM users WHERE username = ? OR email = ?", (account, account.lower()))
    user = cursor.fetchone()
    conn.close()

    if not user or not verify_password(req.password, user["password_hash"], user["salt"]):
        raise HTTPException(status_code=400, detail="账号或密码错误")

    token = create_access_token(user["id"], user["username"])
    return AuthTokenResponse(
        access_token=token,
        user=UserResponse(
            id=user["id"],
            username=user["username"],
            email=user["email"],
            created_at=user["created_at"]
        )
    )

@app.get("/api/auth/me", response_model=UserResponse)
def get_profile(current_user: dict = Depends(get_current_user)):
    return UserResponse(
        id=current_user["id"],
        username=current_user["username"],
        email=current_user["email"],
        created_at=current_user["created_at"]
    )

# ----------------- 笔记核心 CRUD 与软删除回收站 -----------------

@app.get("/api/notes", response_model=List[NoteResponse])
def get_notes(
    q: Optional[str] = Query(None, description="搜索关键词"),
    category: Optional[str] = Query(None, description="分类筛选"),
    current_user: dict = Depends(get_current_user)
):
    conn = get_connection()
    cursor = conn.cursor()
    query = "SELECT * FROM notes WHERE user_id = ? AND is_deleted = 0"
    params = [current_user["id"]]

    if q:
        query += " AND (title LIKE ? OR content LIKE ?)"
        params.extend([f"%{q}%", f"%{q}%"])

    if category and category != "全部":
        query += " AND category = ?"
        params.append(category)

    query += " ORDER BY is_pinned DESC, updated_at DESC"
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()

    return [
        NoteResponse(
            id=row["id"],
            user_id=row["user_id"],
            title=row["title"],
            content=row["content"],
            category=row["category"],
            is_pinned=bool(row["is_pinned"]),
            is_locked=bool(row["is_locked"]),
            is_deleted=bool(row["is_deleted"]),
            created_at=row["created_at"],
            updated_at=row["updated_at"]
        ) for row in rows
    ]

@app.post("/api/notes", response_model=NoteResponse)
def create_note(note: NoteCreate, current_user: dict = Depends(get_current_user)):
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """
        INSERT INTO notes (user_id, title, content, category, is_pinned, is_locked, is_deleted, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        (
            current_user["id"],
            note.title,
            note.content,
            note.category or "默认",
            1 if note.is_pinned else 0,
            1 if note.is_locked else 0,
            now,
            now
        )
    )
    conn.commit()
    note_id = cursor.lastrowid
    conn.close()

    return NoteResponse(
        id=note_id,
        user_id=current_user["id"],
        title=note.title,
        content=note.content,
        category=note.category or "默认",
        is_pinned=bool(note.is_pinned),
        is_locked=bool(note.is_locked),
        is_deleted=False,
        created_at=now,
        updated_at=now
    )

@app.get("/api/notes/{note_id}", response_model=NoteResponse)
def get_note(note_id: int, current_user: dict = Depends(get_current_user)):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM notes WHERE id = ? AND user_id = ?", (note_id, current_user["id"]))
    row = cursor.fetchone()
    conn.close()

    if not row:
        raise HTTPException(status_code=404, detail="笔记不存在")

    return NoteResponse(
        id=row["id"],
        user_id=row["user_id"],
        title=row["title"],
        content=row["content"],
        category=row["category"],
        is_pinned=bool(row["is_pinned"]),
        is_locked=bool(row["is_locked"]),
        is_deleted=bool(row["is_deleted"]),
        created_at=row["created_at"],
        updated_at=row["updated_at"]
    )

@app.put("/api/notes/{note_id}", response_model=NoteResponse)
def update_note(note_id: int, note_update: NoteUpdate, current_user: dict = Depends(get_current_user)):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM notes WHERE id = ? AND user_id = ?", (note_id, current_user["id"]))
    existing = cursor.fetchone()
    if not existing:
        conn.close()
        raise HTTPException(status_code=404, detail="笔记不存在")

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    title = note_update.title if note_update.title is not None else existing["title"]
    content = note_update.content if note_update.content is not None else existing["content"]
    category = note_update.category if note_update.category is not None else existing["category"]
    is_pinned = 1 if note_update.is_pinned else 0 if note_update.is_pinned is not None else existing["is_pinned"]
    is_locked = 1 if note_update.is_locked else 0 if note_update.is_locked is not None else existing["is_locked"]

    cursor.execute(
        """
        UPDATE notes
        SET title = ?, content = ?, category = ?, is_pinned = ?, is_locked = ?, updated_at = ?
        WHERE id = ? AND user_id = ?
        """,
        (title, content, category, is_pinned, is_locked, now, note_id, current_user["id"])
    )
    conn.commit()
    conn.close()

    return NoteResponse(
        id=note_id,
        user_id=current_user["id"],
        title=title,
        content=content,
        category=category,
        is_pinned=bool(is_pinned),
        is_locked=bool(is_locked),
        is_deleted=bool(existing["is_deleted"]),
        created_at=existing["created_at"],
        updated_at=now
    )

@app.delete("/api/notes/{note_id}", response_model=ApiResponse)
def delete_to_trash(note_id: int, current_user: dict = Depends(get_current_user)):
    """软删除：放入回收站"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE notes SET is_deleted = 1, updated_at = ? WHERE id = ? AND user_id = ?",
        (datetime.now().strftime("%Y-%m-%d %H:%M:%S"), note_id, current_user["id"])
    )
    affected = cursor.rowcount > 0
    conn.commit()
    conn.close()
    if not affected:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return ApiResponse(success=True, message="已移入回收站")

# ----------------- 回收站功能 -----------------

@app.get("/api/notes/trash/list", response_model=List[NoteResponse])
def get_trash_notes(current_user: dict = Depends(get_current_user)):
    """获取回收站内的笔记"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT * FROM notes WHERE user_id = ? AND is_deleted = 1 ORDER BY updated_at DESC",
        (current_user["id"],)
    )
    rows = cursor.fetchall()
    conn.close()
    return [
        NoteResponse(
            id=row["id"],
            user_id=row["user_id"],
            title=row["title"],
            content=row["content"],
            category=row["category"],
            is_pinned=bool(row["is_pinned"]),
            is_locked=bool(row["is_locked"]),
            is_deleted=True,
            created_at=row["created_at"],
            updated_at=row["updated_at"]
        ) for row in rows
    ]

@app.post("/api/notes/{note_id}/restore", response_model=ApiResponse)
def restore_note(note_id: int, current_user: dict = Depends(get_current_user)):
    """从回收站恢复笔记"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE notes SET is_deleted = 0, updated_at = ? WHERE id = ? AND user_id = ?",
        (datetime.now().strftime("%Y-%m-%d %H:%M:%S"), note_id, current_user["id"])
    )
    affected = cursor.rowcount > 0
    conn.commit()
    conn.close()
    if not affected:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return ApiResponse(success=True, message="已成功还原笔记")

@app.delete("/api/notes/{note_id}/permanent", response_model=ApiResponse)
def permanently_delete_note(note_id: int, current_user: dict = Depends(get_current_user)):
    """彻底删除笔记"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM notes WHERE id = ? AND user_id = ?", (note_id, current_user["id"]))
    affected = cursor.rowcount > 0
    conn.commit()
    conn.close()
    if not affected:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return ApiResponse(success=True, message="已彻底删除")

# ----------------- 附件 / 图片上传接口 -----------------

@app.post("/api/upload", response_model=UploadResponse)
async def upload_attachment(
    file: UploadFile = File(...),
    current_user: dict = Depends(get_current_user)
):
    """支持上传图片和附件"""
    ext = os.path.splitext(file.filename)[1].lower()
    if not ext:
        ext = ".jpg"
    unique_name = f"{uuid.uuid4().hex}{ext}"
    dest_path = os.path.join(UPLOAD_DIR, unique_name)

    with open(dest_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    return UploadResponse(
        success=True,
        url=f"/uploads/{unique_name}",
        filename=file.filename
    )

@app.get("/api/status")
def health_check():
    return {
        "status": "online",
        "service": "橙子笔记 (Orange Notes) API",
        "version": "2.0.0",
        "server_time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }

# 挂载 Web 端静态页面
static_dir = os.path.join(os.path.dirname(__file__), "..", "static")
if os.path.exists(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

@app.get("/", response_class=HTMLResponse)
def index_page():
    index_file = os.path.join(os.path.dirname(__file__), "..", "static", "index.html")
    if os.path.exists(index_file):
        with open(index_file, "r", encoding="utf-8") as f:
            return f.read()
    return "<h1>橙子笔记 (Orange Notes) 服务运行中</h1><p>请访问 /docs 查看 API 文档</p>"
