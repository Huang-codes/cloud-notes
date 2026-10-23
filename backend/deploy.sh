#!/usr/bin/env bash
set -e

echo "=========================================="
echo "    云端记事本 (Cloud Notes) 快速部署脚本   "
echo "=========================================="

# 针对甲骨文云 (OCI) 系统自带防火墙自动放行 8000 端口
echo ">> 正在配置本机系统防火墙 (针对甲骨文云 / Linux 规则)..."
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
    echo ">> 检测到 Docker，正在使用 Docker Compose 启动服务..."
    mkdir -p data
    if command -v docker-compose >/dev/null 2>&1; then
        docker-compose up -d --build
    else
        docker compose up -d --build
    fi
    echo ">> Docker 容器启动成功！"
else
    echo ">> 未检测到 Docker，正在使用 Python 虚拟环境部署..."
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

    # 启动后台服务 (使用 nohup)
    mkdir -p data
    export NOTE_DB_PATH="data/notes.db"
    export API_TOKEN="cloud-notes-secret"
    
    # 停止旧进程
    pkill -f "uvicorn app.main:app" || true
    
    nohup uvicorn app.main:app --host 0.0.0.0 --port 8000 > server.log 2>&1 &
    echo ">> 服务已在后台启动 (PID: $!)，日志请查看 server.log"
fi

echo ""
echo "=========================================="
echo "部署完成！"
echo "1. 请确保已在甲骨文云 (OCI) 控制台安全列表 (Security List) 中开放 TCP:8000 端口入站规则。"
echo "2. 访问地址: http://<你的甲骨文云公网IP>:8000"
echo "3. 默认访问 Token: cloud-notes-secret"
echo "=========================================="
