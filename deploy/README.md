# Bobapi Invoice System 离线部署包使用说明

本目录用于在目标服务器上快速离线部署 Bobapi 发票管理系统。

## 部署清单

将以下文件复制到目标服务器的同一目录中：
- `invoice-backend.tar`（后端 Docker 镜像离线包）
- `invoice-frontend.tar`（前端 Docker 镜像离线包）
- `docker-compose.yml`（或项目根目录的 `docker-compose.deploy.yml`）
- `.env`（可根据 `.env.example` 复制并修改）

## 部署步骤

### 1. 导入镜像
```bash
docker load -i invoice-backend.tar
docker load -i invoice-frontend.tar
```

### 2. 准备环境变量
```bash
cp .env.example .env
# 可编辑 .env 修改数据库密码、JWT 密钥以及 AI_API_KEY
```

### 3. 启动所有服务
```bash
docker compose up -d
```

### 4. 访问系统
- 前端入口：`http://<服务器IP>:8080`
- 后端 API：`http://<服务器IP>:9090/api`
- API 文档：`http://<服务器IP>:9090/api/doc.html`
