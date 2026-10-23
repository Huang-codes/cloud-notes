from pydantic import BaseModel, Field
from typing import Optional

class NoteBase(BaseModel):
    title: str = Field(..., example="工作备忘")
    content: str = Field(..., example="今天下午 3 点云服务上线讨论")
    category: Optional[str] = Field("默认", example="日常")
    is_pinned: Optional[bool] = False

class NoteCreate(NoteBase):
    pass

class NoteUpdate(BaseModel):
    title: Optional[str] = None
    content: Optional[str] = None
    category: Optional[str] = None
    is_pinned: Optional[bool] = None

class NoteResponse(NoteBase):
    id: int
    created_at: str
    updated_at: str

    class Config:
        from_attributes = True

class AuthCheckRequest(BaseModel):
    token: str

class ApiResponse(BaseModel):
    success: bool
    message: str
