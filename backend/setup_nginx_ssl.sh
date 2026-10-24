#!/usr/bin/env bash
set -e

DOMAIN="muchen.krystal1122.dpdns.org"

echo "=========================================="
echo "   配置 Nginx 反向代理与 HTTPS 证书       "
echo "   绑定域名: $DOMAIN                      "
echo "=========================================="

# 1. 检查并开放系统防火墙 80, 443, 8000 端口
echo ">> 正在检查并放行系统防火墙端口 (80, 443, 8000)..."
if command -v iptables >/dev/null 2>&1; then
    sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT 2>/dev/null || true
    sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT 2>/dev/null || true
    sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT 2>/dev/null || true
    if command -v netfilter-persistent >/dev/null 2>&1; then
        sudo netfilter-persistent save 2>/dev/null || true
    fi
fi
if command -v ufw >/dev/null 2>&1; then
    sudo ufw allow 80/tcp 2>/dev/null || true
    sudo ufw allow 443/tcp 2>/dev/null || true
    sudo ufw allow 8000/tcp 2>/dev/null || true
fi
if command -v firewall-cmd >/dev/null 2>&1; then
    sudo firewall-cmd --zone=public --add-port=80/tcp --permanent 2>/dev/null || true
    sudo firewall-cmd --zone=public --add-port=443/tcp --permanent 2>/dev/null || true
    sudo firewall-cmd --zone=public --add-port=8000/tcp --permanent 2>/dev/null || true
    sudo firewall-cmd --reload 2>/dev/null || true
fi

# 2. 安装 Nginx 和 Certbot
echo ">> 正在安装 Nginx 与 Certbot SSL 证书工具..."
if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y nginx certbot python3-certbot-nginx
elif command -v yum >/dev/null 2>&1; then
    sudo yum install -y epel-release || true
    sudo yum install -y nginx certbot python3-certbot-nginx
fi

# 3. 配置 Nginx 站点
echo ">> 写入 Nginx 站点配置 (/etc/nginx/sites-available/orange-notes)..."
sudo mkdir -p /etc/nginx/sites-available /etc/nginx/sites-enabled

sudo tee /etc/nginx/sites-available/orange-notes > /dev/null <<EOF
server {
    listen 80;
    server_name $DOMAIN;

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/orange-notes /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

echo ">> 正在验证 Nginx 配置并启动..."
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl restart nginx

# 4. 自动申请并配置 Let's Encrypt HTTPS 证书
echo ">> 正在申请 Let's Encrypt 免费 HTTPS SSL 证书..."
if sudo certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --register-unsafely-without-email --redirect; then
    echo ""
    echo "=========================================="
    echo "🎉 恭喜！HTTPS 证书申请与配置成功！"
    echo "网页端访问地址: https://$DOMAIN"
    echo "=========================================="
else
    echo ""
    echo "⚠️ Certbot 证书申请暂未完成，可能原因："
    echo "1. 云平台安全列表中尚未添加 80 和 443 端口的 入站规则 (Ingress Rules)。"
    echo "2. 域名 DNS 尚在全面生效中。"
    echo "不过 Nginx 已配置就绪，您可以通过 http://$DOMAIN 正常访问！"
    echo "放行 80/443 端口后可随时再次运行此脚本申请 SSL。"
fi
