# 发票管理系统

基于 Spring Boot 3.1.5、Vue 3 和 MySQL 8 的前后端分离发票管理系统。普通用户提交开票申请并下载已完成发票，管理员查看申请并上传 PDF 或图片发票。

## 已实现功能

### 普通用户

- 注册、登录、记住登录状态和退出登录
- 提交公司名称、税号和开票金额
- 查看自己的申请历史
- 下载自己的已开票文件

### 管理员

- 查看及筛选所有发票申请
- 上传 PDF、JPG、JPEG 或 PNG 发票
- 下载已上传文件

### 安全与可靠性

- Spring Security、JWT 和 USER/ADMIN 角色权限
- 用户只能下载自己的发票，管理员可以下载全部发票
- 创建发票使用 `Idempotency-Key` 和数据库唯一索引防止重复提交
- 登录每个 IP+用户名每分钟最多 10 次，连续失败 5 次锁定 15 分钟
- 发票申请每个用户每分钟最多 10 次
- 后端同时校验上传文件大小、扩展名、MIME 和文件头
- 统一错误响应、HTTP 状态码、`traceId` 和 `X-Trace-Id` 响应头

## 技术栈

- 后端：JDK 17、Spring Boot 3.1.5、Spring Security、MyBatis-Plus、MySQL、JWT、Knife4j
- 前端：Vue 3、TypeScript 5.3、Vite 5、Element Plus、Pinia、Vue Router、Axios
- 测试：JUnit 5、Mockito、MockMvc、Vitest

## 本机启动

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

当前本机数据库配置位于 `backend/src/main/resources/application.yml`：

```yaml
username: root
password: 200
```

后端第一次启动会自动创建 `invoice_system` 数据库、数据表和默认账号。SQL 可重复执行，不会重复插入账号。

默认账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 普通用户 | `user` | `user123` |

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

除文件下载外，接口统一返回：

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

发票文件保存在后端工作目录的 `uploads` 文件夹，只能通过鉴权下载接口访问。单文件最大 10MB，支持 PDF、JPG、JPEG 和 PNG。
