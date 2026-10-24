import os
import smtplib
import random
import time
from email.mime.text import MIMEText
from email.header import Header

# SMTP 配置（支持 QQ邮箱、163邮箱、Gmail 或任意邮件服务商）
SMTP_HOST = os.environ.get("SMTP_HOST", "")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "465"))
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASS = os.environ.get("SMTP_PASS", "")
SMTP_SENDER = os.environ.get("SMTP_SENDER", SMTP_USER or "noreply@orange-notes.local")

def generate_verification_code() -> str:
    """生成 6 位随机数字验证码"""
    return f"{random.randint(100000, 999999)}"

def send_email_code(to_email: str, code: str, purpose: str = "找回密码") -> bool:
    """发送邮箱验证码"""
    subject = f"【橙子笔记】你的验证码是 {code}"
    content = f"""
    <div style="max-width: 480px; margin: 0 auto; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 24px; border: 1px solid #e2e8f0; border-radius: 12px;">
        <h2 style="color: #2563eb; margin-top: 0;">橙子笔记安全验证</h2>
        <p style="color: #475569; font-size: 14px;">你正在进行 <strong>{purpose}</strong> 操作，验证码如下：</p>
        <div style="background: #eff6ff; padding: 16px; border-radius: 8px; text-align: center; margin: 20px 0;">
            <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1d4ed8;">{code}</span>
        </div>
        <p style="color: #94a3b8; font-size: 12px; margin-bottom: 0;">验证码有效期为 10 分钟。如果不是你本人操作，请忽略此邮件。</p>
    </div>
    """

    # 如果配置了真实 SMTP，则调用真实发信
    if SMTP_HOST and SMTP_USER and SMTP_PASS:
        try:
            msg = MIMEText(content, 'html', 'utf-8')
            msg['From'] = Header(f"橙子笔记 <{SMTP_SENDER}>", 'utf-8')
            msg['To'] = Header(to_email, 'utf-8')
            msg['Subject'] = Header(subject, 'utf-8')

            if SMTP_PORT == 465:
                server = smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, timeout=10)
            else:
                server = smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=10)
                server.starttls()

            server.login(SMTP_USER, SMTP_PASS)
            server.sendmail(SMTP_SENDER, [to_email], msg.as_string())
            server.quit()
            return True
        except Exception as e:
            print(f"[EmailService] 发信异常: {e}")

    # 开发/测试模式下打印到后台日志中，方便随时查看使用
    print(f"[EmailService 验证码] 目标邮箱: {to_email} | 操作: {purpose} | 验证码: {code}")
    return True
