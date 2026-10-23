FROM python:3.11-slim

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制源码与前端文件
COPY . .

# 暴露端口
EXPOSE 8000

ENV NOTE_DB_PATH=/app/data/notes.db
ENV API_TOKEN=cloud-notes-secret

# 创建持久化数据目录
RUN mkdir -p /app/data

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
