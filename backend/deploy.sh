#!/usr/bin/env bash
set -e

echo "=========================================="
echo "    橙子笔记 (Orange Notes) 一键部署脚本   "
echo "=========================================="

# 配置系统防火墙端口
echo ">> 正在检查系统防火墙 8000 端口..."
if command -v iptables >/dev/null 2>&1; then
    sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT 2>/dev/null || true
    if command -v netfilter-persistent >/dev/null 2>&1; then
        sudo netfilter-persistent save 2>/dev/null || true
    fi
fi
if command -v ufw >/dev/null 2>&1; then
    sudo ufw allow 8000/tcp 2>/dev/null || true
fi
if command -v firewall-cmd >/dev/null 2>&1; then
    sudo firewall-cmd --zone=public --add-port=8000/tcp --permanent 2>/dev/null || true
    sudo firewall-cmd --reload 2>/dev/null || true
fi

# 检测是否有 docker
if command -v docker >/dev/null 2>&1; then
    echo ">> 检测到 Docker，正在启动容器服务..."
    mkdir -p data uploads
    if command -v docker-compose >/dev/null 2>&1; then
        docker-compose up -d --build
    else
        docker compose up -d --build
    fi
    echo ">> Docker 容器启动成功！"
else
    echo ">> 使用 Python 虚拟环境部署..."
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update
        sudo apt-get install -y python3 python3-pip python3-venv
    elif command -v yum >/dev/null 2>&1; then
        sudo yum install -y python3 python3-pip
    fi

    # 创建虚拟环境
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt

    mkdir -p data uploads
    export NOTE_DB_PATH="data/notes.db"
    export NOTE_UPLOAD_DIR="uploads"
    
    # 停止旧进程
    pkill -f "uvicorn app.main:app" || true
    
    nohup uvicorn app.main:app --host 0.0.0.0 --port 8000 > server.log 2>&1 &
    echo ">> 橙子笔记服务已在后台启动 (PID: $!)"
fi

echo ""
echo "=========================================="
echo "部署完成！"
echo "服务已成功运行在端口 8000。"
echo "=========================================="
