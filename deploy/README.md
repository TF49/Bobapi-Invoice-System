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

> **从旧版本升级**：compose 为 `invoice-network` 固定了子网 `172.16.238.0/24`，并给前端 Nginx 分配固定 IP
> `172.16.238.10`（后端只信任该地址发来的 `X-Forwarded-For`）。已有网络无法原地修改，升级时请先执行
> `docker compose down` 再 `docker compose up -d`（不要加 `-v`，数据卷会保留）。
> 若该子网与服务器已有网段冲突，修改 compose 中的 `subnet`、frontend 的 `ipv4_address`，
> 并把 `.env` 中的 `TRUSTED_PROXIES` 改成新的前端 IP。

### 4. 访问系统
- 前端入口：`http://<服务器IP>:8080`
- 后端 API：`http://<服务器IP>:9090/api`
- API 文档：`http://<服务器IP>:9090/api/doc.html`
