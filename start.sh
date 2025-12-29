#!/bin/bash

echo "=========================================="
echo "智能图片管理网站 - 启动脚本"
echo "=========================================="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: 未安装Docker，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未安装Docker Compose，请先安装Docker Compose"
    exit 1
fi

# 创建必要的目录
echo "创建必要的目录..."
mkdir -p uploads thumbnails

# 启动服务
echo "启动Docker容器..."
docker-compose up -d --build

echo ""
echo "=========================================="
echo "服务启动中，请稍候..."
echo "=========================================="
echo ""
echo "等待服务启动（约30秒）..."
sleep 30

echo ""
echo "=========================================="
echo "服务已启动！"
echo "=========================================="
echo ""

# 获取本机IP地址
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
if [ -z "$IP" ]; then
    IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7; exit}')
fi
if [ -z "$IP" ]; then
    IP=$(ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1' | head -n 1)
fi
if [ -z "$IP" ]; then
    IP="localhost"
fi

echo "PC访问地址:"
echo "  前端地址: http://localhost:3000"
echo "  后端API: http://localhost:8080/api"
echo ""
echo "=========================================="
echo "手机访问地址（请确保手机和电脑在同一WiFi网络）:"
echo "=========================================="
echo ""
echo "  ==========================================="
echo "  http://${IP}:3000"
echo "  ==========================================="
echo ""
echo "  请复制上面的地址到手机浏览器打开"
echo "  如果无法访问，请检查:"
echo "  1. 手机和电脑是否在同一WiFi网络"
echo "  2. 防火墙是否允许3000端口"
echo "  3. 路由器是否允许设备间通信"
echo ""
echo "=========================================="
echo "其他命令:"
echo "=========================================="
echo "  查看日志: docker-compose logs -f"
echo "  停止服务: docker-compose down"
echo ""

