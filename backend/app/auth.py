import hashlib
import os
import hmac
import time
import base64
import json
from typing import Optional, Dict

SECRET_KEY = os.environ.get("JWT_SECRET_KEY", "orange-notes-super-secret-key-2026")

def hash_password(password: str) -> tuple[str, str]:
    """生成带随机盐的密码哈希 (PBKDF2-HMAC-SHA256)"""
    salt = os.urandom(16).hex()
    pwd_hash = hashlib.pbkdf2_hmac(
        'sha256',
        password.encode('utf-8'),
        salt.encode('utf-8'),
        100000
    ).hex()
    return pwd_hash, salt

def verify_password(password: str, pwd_hash: str, salt: str) -> bool:
    """校验密码"""
    computed_hash = hashlib.pbkdf2_hmac(
        'sha256',
        password.encode('utf-8'),
        salt.encode('utf-8'),
        100000
    ).hex()
    return hmac.compare_digest(computed_hash, pwd_hash)

def create_access_token(user_id: int, username: str, expires_delta_days: int = 30) -> str:
    """生成安全认证 Token (HMAC-SHA256 签名)"""
    payload = {
        "uid": user_id,
        "sub": username,
        "exp": int(time.time()) + (expires_delta_days * 86400)
    }
    header_b64 = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    signature = hmac.new(
        SECRET_KEY.encode(),
        f"{header_b64}.{payload_b64}".encode(),
        hashlib.sha256
    ).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}.{sig_b64}"

def decode_access_token(token: str) -> Optional[Dict]:
    """验证并解码 Token"""
    try:
        parts = token.strip().split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        
        # 验证签名
        expected_sig = hmac.new(
            SECRET_KEY.encode(),
            f"{header_b64}.{payload_b64}".encode(),
            hashlib.sha256
        ).digest()
        
        # 补齐 base64 padding
        sig_padding = sig_b64 + "=" * (-len(sig_b64) % 4)
        received_sig = base64.urlsafe_b64decode(sig_padding)
        if not hmac.compare_digest(expected_sig, received_sig):
            return None
            
        payload_padding = payload_b64 + "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.urlsafe_b64decode(payload_padding).decode())
        
        # 检查是否过期
        if payload.get("exp", 0) < time.time():
            return None
        return payload
    except Exception:
        return None
