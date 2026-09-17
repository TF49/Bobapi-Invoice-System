# Docker 部署指南

## 快速启动

### 1. 准备环境变量

复制环境变量示例文件：

```bash
cp .env.example .env
```

编辑 `.env` 文件，修改以下配置：

- `DB_PASSWORD`: 数据库密码
- `JWT_SECRET`: JWT 密钥（生产环境请使用强密码）
- `AI_API_KEY`: DeepSeek API 密钥（如果需要 AI 功能）

### 2. 构建并启动服务

```bash
docker-compose up -d
```

### 3. 访问应用

- 前端: http://localhost:8080
- 后端 API: http://localhost:9090/api
- API 文档: http://localhost:9090/api/doc.html

## 服务说明

### 包含的服务

1. **MySQL 8.0**: 数据库服务，端口 3306
2. **Backend**: Spring Boot 后端服务，端口 9090
3. **Frontend**: Vue 3 前端服务（Nginx），端口 8080

### 数据持久化

- MySQL 数据存储在 Docker volume `mysql-data`
- 后端上传文件存储在 Docker volume `backend-uploads`

## 常用命令

### 查看服务状态

```bash
docker-compose ps
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs backend
docker-compose logs frontend
docker-compose logs mysql

# 实时跟踪日志
docker-compose logs -f backend
```

### 停止服务

```bash
docker-compose stop
```

### 启动服务

```bash
docker-compose start
```

### 重启服务

```bash
docker-compose restart
```

### 停止并删除容器

```bash
docker-compose down
```

### 停止并删除容器及数据卷（警告：会删除所有数据）

```bash
docker-compose down -v
```

### 重新构建镜像

```bash
# 重新构建所有服务
docker-compose build

# 重新构建特定服务
docker-compose build backend

# 重新构建并启动
docker-compose up -d --build
```

### 离线/镜像包导出与部署

#### 1. 导出镜像包 (tar)
```bash
docker save -o invoice-backend.tar invoice-backend:latest
docker save -o invoice-frontend.tar invoice-frontend:latest
```

#### 2. 在目标服务器导入与启动
将 `invoice-backend.tar`、`invoice-frontend.tar`、`docker-compose.deploy.yml`（或 `docker-compose.yml`）与 `.env` 文件上传到目标机器，执行：
```bash
# 导入镜像
docker load -i invoice-backend.tar
docker load -i invoice-frontend.tar

# 使用离线 compose 配置启动服务
docker compose -f docker-compose.deploy.yml up -d
```

## 开发模式

如果需要在开发中使用 Docker，可以修改 docker-compose.yml 添加本地文件挂载：

### 后端开发模式

```yaml
backend:
  # ... 其他配置
  volumes:
    - ./backend/src:/app/src:ro  # 只读挂载源代码
    - backend-uploads:/app/uploads
```

### 前端开发模式

前端需要使用 Vite 开发服务器，建议直接在本地运行：

```bash
cd frontend
npm install
npm run dev
```

## 故障排查

### 后端无法连接数据库

检查 MySQL 是否已启动：

```bash
docker-compose logs mysql
```

等待 MySQL 健康检查通过后，后端会自动启动。

### 前端无法访问后端 API

检查网络配置，确保前后端在同一个 Docker network 中：

```bash
docker network ls
docker network inspect <network-name>
```

### 查看容器资源使用情况

```bash
docker stats
```

### 进入容器调试

```bash
# 进入后端容器
docker-compose exec backend sh

# 进入 MySQL 容器
docker-compose exec mysql mysql -u root -p
```

## 生产环境部署建议

1. **修改环境变量**: 使用强密码和安全的 JWT 密钥
2. **配置 HTTPS**: 在 Nginx 前添加反向代理（如 Traefik 或 Nginx）
3. **数据备份**: 定期备份 MySQL 数据卷
4. **资源限制**: 在 docker-compose.yml 中添加资源限制
5. **日志管理**: 配置日志驱动和日志轮转
6. **监控**: 添加健康检查和监控告警

### 资源限制示例

```yaml
services:
  backend:
    # ... 其他配置
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

## 清理

删除所有未使用的 Docker 资源：

```bash
docker system prune -a
```

删除未使用的 volumes：

```bash
docker volume prune
```
