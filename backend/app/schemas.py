from pydantic import BaseModel, Field
from typing import Optional

# 用户相关
class UserRegisterRequest(BaseModel):
    username: str = Field(..., min_length=2, max_length=32)
    email: str = Field(..., min_length=3, max_length=64)
    password: str = Field(..., min_length=6, max_length=64)

class UserLoginRequest(BaseModel):
    account: str = Field(..., description="用户名或邮箱")
    password: str = Field(...)

class UserResponse(BaseModel):
    id: int
    username: str
    email: str
    created_at: str

class AuthTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse

# 笔记相关
class NoteBase(BaseModel):
    title: str = Field(...)
    content: str = Field(...)
    category: Optional[str] = "默认"
    is_pinned: Optional[bool] = False
    is_locked: Optional[bool] = False

class NoteCreate(NoteBase):
    pass

class NoteUpdate(BaseModel):
    title: Optional[str] = None
    content: Optional[str] = None
    category: Optional[str] = None
    is_pinned: Optional[bool] = None
    is_locked: Optional[bool] = None

class NoteResponse(NoteBase):
    id: int
    user_id: int
    is_deleted: bool
    created_at: str
    updated_at: str

    class Config:
        from_attributes = True

class ApiResponse(BaseModel):
    success: bool
    message: str

class UploadResponse(BaseModel):
    success: bool
    url: str
    filename: str
