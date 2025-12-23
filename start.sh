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
echo "前端地址: http://localhost:3000"
echo "后端API: http://localhost:8080/api"
echo ""
echo "查看日志: docker-compose logs -f"
echo "停止服务: docker-compose down"
echo ""

