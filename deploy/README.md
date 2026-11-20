# Bobapi Invoice System 离线部署包使用说明

本目录用于在目标服务器上快速离线部署 Bobapi 发票管理系统。

## 部署清单

将以下文件复制到目标服务器的同一目录中：
- `invoice-backend.tar`（后端 Docker 镜像离线包）
- `invoice-frontend.tar`（前端 Docker 镜像离线包）
- `docker-compose.yml`（或项目根目录的 `docker-compose.deploy.yml`）
- `.env`（可根据 `.env.example` 复制并修改）

> **不在部署包内**：服务器上的 `docker-compose.override.yml`（MySQL 内存优化、时区、关闭端口映射）由服务器单独维护，
> 部署时**不要上传、不要覆盖、不要删除**。`docker compose` 会自动合并同目录下的 override 文件。
> 仓库里的留档副本在 `deploy/server/docker-compose.override.yml`，仅供比对。
> 不要在 `docker-compose.yml` / `docker-compose.deploy.yml` 里给 mysql 加 `command`——override 的 `command` 会整体替换主文件的。

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

> **本版本（新增发票类型「资料打印服务费」、科技公司税率 6% 调整为 5%）**：无数据库迁移、compose 文件无变化，
> 只需替换两个镜像 tar 后 `docker compose up -d`。

> **上一版本数据库变更（供应商分类型结算）**：后端启动时 Flyway 自动执行 V34、V35，仅给 `supplier_settlement`
> 增加列（结算通道 `seller_company` / `invoice_category`，作废信息 `voided_at` / `voided_by` / `voided_by_name` / `void_reason`），
> 不改动已有数据；历史结算记录全部归属「普通公司·普票」。升级前建议先备份数据库：
> `docker exec invoice-mysql mysqldump -uroot -p<密码> invoice_system > backup-$(date +%F).sql`
> 本版本 compose 文件无变化，只需替换两个镜像 tar 后 `docker compose up -d`。

### 4. 访问系统
- 前端入口：`http://<服务器IP>:8080`
- 后端 API：`http://<服务器IP>:9090/api`
- API 文档：`http://<服务器IP>:9090/api/doc.html`
