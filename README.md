# 发票管理系统

基于 Spring Boot 3.1.5、Vue 3 和 MySQL 8 的前后端分离发票管理系统。普通用户提交开票申请并预览、下载已完成发票，管理员查看申请并粘贴或选择图片上传。

## 已实现功能

### 普通用户

- 注册、登录、记住登录状态和退出登录
- 提交公司名称、税号和开票金额
- 查看自己的申请历史
- 预览和下载自己的已开票图片

### 管理员

- 查看及筛选所有发票申请
- 粘贴或选择 JPG、JPEG、PNG 发票图片
- 下载已上传文件

### 安全与可靠性

- Spring Security、JWT 和 USER/ADMIN 角色权限
- 用户只能下载自己的发票，管理员可以下载全部发票
- 创建发票使用 `Idempotency-Key` 和数据库唯一索引防止重复提交
- 登录每个 IP+用户名每分钟最多 10 次，连续失败 5 次锁定 15 分钟
- 发票申请、图片上传、预览和下载均有独立限流
- 后端校验文件大小、扩展名、MIME、文件头、真实解码格式、图片尺寸和总像素数
- 统一错误响应、HTTP 状态码、`traceId` 和 `X-Trace-Id` 响应头

## 技术栈

- 后端：JDK 17、Spring Boot 3.1.5、Spring Security、MyBatis-Plus、MySQL、Flyway、JWT、Knife4j
- 前端：Vue 3、TypeScript 5.3、Vite 5、Element Plus、Pinia、Vue Router、Axios
- 测试：JUnit 5、Mockito、MockMvc、Vitest

## 本机启动

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

启动后端前必须配置数据库密码和 JWT 密钥。其中 `DB_PASSWORD` 已在 **系统环境变量** 中配置，无需每次手动设置。

本机开发推荐使用项目根目录的启动脚本。脚本会在首次启动时生成随机 JWT 密钥，保存到被 Git 忽略的 `backend/.local/jwt-secret`，后续启动自动复用：

```powershell
.\start-local.ps1
```

生产环境不要使用本地密钥文件，必须通过部署平台或密钥管理服务注入 `JWT_SECRET`。

如果不使用启动脚本，也可以手动注入：

```powershell
# 如未在系统环境变量中配置，则需手动注入
$env:DB_PASSWORD = '<本机 MySQL 密码>'
$env:JWT_SECRET  = '<至少 32 字节的随机密钥>'
```

环境变量说明：

| 变量 | 默认值 | 是否必填 | 用途 |
| --- | --- | --- | --- |
| `DB_PASSWORD` | 无 | **必填**（系统级已配置） | 数据库密码 |
| `JWT_SECRET` | 本地脚本自动生成；生产环境无默认值 | **必填** | JWT 签名密钥（至少 32 字节） |
| `FILE_UPLOAD_PATH` | `./uploads` | 可选 | 发票图片存储目录 |
| `CORS_ALLOWED_ORIGINS` | 本机前端两个地址 | 可选 | 允许的前端来源，逗号分隔 |
| `INVOICE_IMAGE_MAX_WIDTH` | `8000` | 可选 | 图片最大宽度（像素） |
| `INVOICE_IMAGE_MAX_HEIGHT` | `8000` | 可选 | 图片最大高度（像素） |
| `INVOICE_IMAGE_MAX_PIXELS` | `30000000` | 可选 | 图片最大总像素数 |

后端第一次启动会由 Flyway 按 V1→V4 创建数据表和默认账号；后续结构升级记录在
`backend/src/main/resources/db/migration`，不会覆盖已修改的账号密码。新环境只需预先创建空数据库，
不要先执行 `init.sql`。

默认账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 普通用户 | `user` | `user123` |

#### 数据库部署方式

- **推荐的新服务器部署**：创建字符集为 `utf8mb4`、排序规则为 `utf8mb4_unicode_ci` 的空数据库
  `invoice_system`，配置 `DB_PASSWORD` 和 `JWT_SECRET` 后启动后端。Flyway 会依次执行 V1、V2、V3、V4。
- **已有数据库升级**：先备份数据库，再直接启动新版后端；Flyway 只执行尚未应用的增量脚本。
- **手工全量初始化**：`backend/src/main/resources/init.sql` 已整合 V1-V4 的最终结构，但会先删除
  `invoice_system` 及其全部数据。执行后，首次启动后端前临时设置
  `SPRING_FLYWAY_BASELINE_VERSION=4`，待 Flyway 建立版本记录后即可移除该变量。

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

- API：`http://localhost:9090/api`
- Knife4j：`http://localhost:9090/api/doc.html`

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:8080`。开发服务器会将 `/api` 代理到后端 `9090` 端口。

## 测试与构建

```powershell
cd backend
mvn test

cd ../frontend
npm test
npm run build
```

## API 约定

除文件预览和下载外，接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "request-trace-id"
}
```

创建发票时必须发送长度为 16-64 的 `Idempotency-Key` 请求头。相同用户使用同一 Key 和同一请求体会得到第一次创建的结果；同一 Key 对应不同请求体会返回 HTTP 409。

## 文件存储

发票图片默认保存在后端工作目录的 `uploads` 文件夹，只能通过鉴权预览或下载接口访问。单文件最大 10MB，仅支持 JPG、JPEG 和 PNG；扩展名、MIME、文件头与真实解码格式必须一致。
