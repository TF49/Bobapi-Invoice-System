#!/bin/bash
set -e

echo "=== 开始离线导入 Docker 镜像 ==="
if [ -f "../invoice-backend.tar" ]; then
    docker load -i ../invoice-backend.tar
elif [ -f "./invoice-backend.tar" ]; then
    docker load -i ./invoice-backend.tar
else
    echo "未找到 invoice-backend.tar，请确认文件路径"
fi

if [ -f "../invoice-frontend.tar" ]; then
    docker load -i ../invoice-frontend.tar
elif [ -f "./invoice-frontend.tar" ]; then
    docker load -i ./invoice-frontend.tar
else
    echo "未找到 invoice-frontend.tar，请确认文件路径"
fi

if [ ! -f ".env" ] && [ -f ".env.example" ]; then
    echo "未检测到 .env 文件，已从 .env.example 自动生成，请按需修改！"
    cp .env.example .env
fi

echo "=== 启动服务 ==="
docker compose up -d

echo "=== 服务启动完成 ==="
docker compose ps
