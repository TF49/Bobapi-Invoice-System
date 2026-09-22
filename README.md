# BOBAPI 发票管理系统

面向外部商户的开票服务系统（B2B）。用户注册后通过**预付费额度**提交开票申请，既可以在 Web 界面操作，
也可以通过 **OpenAPI** 对接自有系统；开票员/管理员在后台处理申请并上传发票图片，用户预览或下载。

- 后端：Spring Boot 3.1.5 + JDK 17，端口 `9090`，上下文路径 `/api`
- 前端：Vue 3 + TypeScript 5.3 + Vite 5，端口 `8080`
- 数据库：MySQL 8，结构由 Flyway 版本化管理（V1–V21）

---

## 1. 核心业务模型

### 1.1 额度与计费

系统采用**预付费额度**模式，所有金额单位为元，最多两位小数。

| 规则 | 取值 | 说明 |
| --- | --- | --- |
| 开票金额下限 | `0.01` | 以分为最小单位 |
| 普票（`NORMAL`）扣减 | 开票金额 × 1 | 按开票金额等额扣除 |
| 专票（`VAT_SPECIAL`）扣减 | 开票金额 × **3** | 专票按 3 倍扣除额度，流水中标注"专票3倍额度" |
| 充值手续费率 | **3%**（`0.03`） | 申请额度 = 手续费 ÷ 0.03，向下取整到分 |
| 手续费区间 | `0.01` ~ `29999.99` | 对应申请额度上限 `999999.99` |
| 单笔充值区间 | `0.01` ~ `999999.99` | 管理员直接充值时的校验范围 |

**额度扣减时机**：发票申请创建成功时立即扣除。**退还时机**：取消申请、红冲完成、管理员调低发票金额时退还。
发票因用户额度不足而创建失败时，整个事务回滚。

### 1.2 充值流程（对公转账）

用户提交充值申请时需填写**手续费金额**并上传转账截图，系统按 3% 反算可获得的额度：

```
申请额度 = 手续费 ÷ 0.03（RoundingMode.DOWN，保留 2 位小数）
```

申请进入 `PENDING` 状态，由管理员审核：

- 审核通过（`APPROVED`）→ 自动为用户充值，幂等键为 `RECHARGE_REQUEST_{id}`，重复审核不会重复入账
- 审核驳回（`REJECTED`）→ 仅记录管理员备注，不变更额度

审核使用「状态条件更新」防止并发重复审核（受影响行数不为 1 时抛 409）。

### 1.3 发票状态与红冲

**发票状态**：`PENDING`（待开票）→ `COMPLETED`（已开票）／`CANCELLED`（已取消）

**红冲状态**（`red_flush_status`）：`NONE` → `PENDING` → `COMPLETED` ／ `REJECTED`

- 只有 `COMPLETED` 的发票可以申请红冲，原因必填、最多 500 字符
- 红冲确认后**自动退还**该发票对应的额度（专票按 3 倍退还），并记录操作人
- 红冲确认与驳回同样使用状态条件更新，避免并发重复处理

**其他状态约束**：

- 已开票（`COMPLETED`）的发票**不允许修改金额与票种**，以免与已归档的发票文件不一致
  （公司名称、税号、备注仍可修改）
- 已取消（`CANCELLED`）的发票不允许修改
- 存在待处理（`PENDING`）或已完成（`COMPLETED`）红冲的发票不允许修改
- 只有 `PENDING` 的发票可以取消，取消后退还额度
- 只有 `PENDING` 的发票可以上传发票文件

### 1.4 供应商结算

管理员可登记对外部供应商的结算记录：

```
当前未结款项 = 累计已开票金额 − 累计已结算金额（下限为 0）
```

结算金额**不得超过**当前未结款项，防止超额结算导致未结款项为负。记录冗余存储操作人名称，
即使用户被删除，历史操作人信息仍可追溯。

### 1.5 AI 智能识别

支持通过大模型从非结构化文本中提取发票字段，采用**两阶段**流程：

1. **提取**：从原文中抽取公司名称、税号、金额、开票类型
2. **核查**：将初步结果与原文一并回传，由模型二次校对修正

所有 AI 返回字段均经过服务端校验后才采信：开票类型必须命中白名单，金额必须在
`0.01 ~ 9999999999.99` 且不超过两位小数，超出范围一律置空。识别结果附带
`confidence`（`HIGH`/`LOW`）与未识别字段提示。

---

## 2. 角色与权限

| 角色 | 标识 | 权限 |
| --- | --- | --- |
| 普通用户 | `USER` | 提交/取消开票申请、查看自己的申请、预览下载自己的发票、申请红冲、充值申请、查看自己的额度与流水、管理自己的 API Key |
| 开票员 | `INVOICE_CLERK` | 查看和筛选全部申请、上传发票图片、下载文件、处理红冲 |
| 管理员 | `ADMIN` | 上述全部权限，外加用户管理、额度管理、充值审核、数据看板、供应商结算 |

**额度归属**：只有 `USER` 角色拥有额度。管理员对额度的充值/调整操作会校验目标用户角色，
防止通过隐藏前端按钮绕过业务边界。

**下载隔离**：用户只能下载自己的发票，管理员可下载全部；跨用户访问返回 403。

权限同时在两处强制：Spring Security 的 URL 规则（`SecurityConfig`）与服务层的业务校验。

---

## 3. 接口概览

所有业务接口（除文件预览/下载外）统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "request-trace-id"
}
```

响应头包含 `X-Trace-Id`，便于链路排查。

### 3.1 认证 `/auth`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 登录，支持"记住我"（7 天）与普通登录（24 小时） |
| POST | `/auth/register` | 注册 |

### 3.2 发票 `/invoices`

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| POST | `/invoices` | USER | 提交单个开票申请 |
| POST | `/invoices/batch` | USER | 批量提交（最多 100 条） |
| GET | `/invoices/my` | USER | 我的申请列表 |
| POST | `/invoices/{id}/cancel` | USER | 取消待开票申请 |
| POST | `/invoices/{id}/red-flush/apply` | USER | 申请红冲 |
| PUT | `/invoices/{id}/processed` | USER | 标记处理状态 |
| GET | `/invoices/{id}/preview` | 本人/管理员 | 预览发票图片 |
| GET | `/invoices/{id}/download` | 本人/管理员 | 下载发票图片 |
| GET | `/invoices/admin/all` | ADMIN / CLERK | 全部申请（可筛选） |
| PUT | `/invoices/admin/{id}` | ADMIN / CLERK | 修改发票信息 |
| POST | `/invoices/admin/{id}/upload` | ADMIN / CLERK | 上传发票图片 |
| POST | `/invoices/admin/{id}/red-flush/confirm` | ADMIN / CLERK | 确认红冲 |
| POST | `/invoices/admin/{id}/red-flush/reject` | ADMIN / CLERK | 驳回红冲 |
| GET | `/invoices/admin/red-flush/pending-count` | ADMIN / CLERK | 待处理红冲数量 |
| PUT | `/invoices/batch/processed` | USER | 批量标记处理状态 |
| GET | `/invoices/admin/dashboard` | ADMIN | 数据看板 |

### 3.3 额度 `/users/quota`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/quota` | 当前额度 |
| GET | `/users/quota/transactions` | 额度流水（可按类型筛选：`RECHARGE`/`DEDUCT`/`ADJUST`） |

### 3.4 充值申请 `/recharge-requests`

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| POST | `/recharge-requests/upload-screenshot` | USER | 上传转账截图 |
| GET | `/recharge-requests/screenshot/{fileName}` | 公开 | 查看截图 |
| POST | `/recharge-requests` | USER | 提交充值申请 |
| GET | `/recharge-requests/my` | USER | 我的充值申请 |
| GET | `/recharge-requests/admin/pending` | ADMIN | 待审核列表 |
| GET | `/recharge-requests/admin/all` | ADMIN | 全部充值申请 |
| PUT | `/recharge-requests/admin/{id}/review` | ADMIN | 审核（`APPROVED`/`REJECTED`） |
| GET | `/recharge-requests/admin/pending-count` | ADMIN | 待审核数量 |

### 3.5 用户管理 `/users/admin`（仅 ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/admin` | 用户列表 |
| POST | `/users/admin` | 创建用户 |
| PUT | `/users/admin/{id}/role` | 调整角色 |
| PUT | `/users/admin/{id}/status` | 启用/禁用 |
| PUT | `/users/admin/{id}/password` | 重置密码 |
| PUT | `/users/admin/{id}/remark` | 修改备注 |
| POST | `/users/admin/{id}/quota/recharge` | 充值额度 |
| PUT | `/users/admin/{id}/quota/adjust` | 调整额度 |
| GET | `/users/admin/{id}/quota` | 查看用户额度 |
| GET | `/users/admin/{id}/quota/transactions` | 查看用户流水 |
| POST | `/users/admin/{id}/api-key/generate` | 生成用户 API Key |
| PUT | `/users/admin/{id}/api-key/status` | 启用/禁用 API Key |

### 3.6 用户自助 API Key `/users/api-key`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/api-key` | 查看自己的 API Key |
| POST | `/users/api-key/generate` | 生成 |
| PUT | `/users/api-key/status` | 启用/禁用 |

### 3.7 OpenAPI `/open/v1`（API Key 或 JWT 认证）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/open/v1/invoices` | 提交开票申请 |
| POST | `/open/v1/invoices/batch` | 批量提交 |
| GET | `/open/v1/invoices/{id}` | 按内部 ID 查询 |
| GET | `/open/v1/invoices/by-out-trade-no/{outTradeNo}` | 按外部商户单号查询 |
| GET | `/open/v1/invoices/{id}/download` | 按内部 ID 下载 |
| GET | `/open/v1/invoices/by-out-trade-no/{outTradeNo}/download` | 按外部商户单号下载 |
| POST | `/open/v1/invoices/{id}/cancel` | 按内部 ID 取消 |
| POST | `/open/v1/invoices/by-out-trade-no/{outTradeNo}/cancel` | 按外部商户单号取消 |
| GET | `/open/v1/quota` | 查询当前额度 |

**外部商户单号**（`outTradeNo`）：调用方可传入自己的订单号，`(user_id, out_trade_no)` 建有唯一索引，
既可用内部 ID 也可用该单号查询、下载和取消，便于业务系统对齐。

### 3.8 AI 识别 `/ai`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/ai/parse-invoice` | 从文本提取发票字段 |
| POST | `/ai/verify-invoice` | 核查并修正已提取字段 |

### 3.9 供应商结算 `/api/admin/supplier-settlement`（仅 ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/supplier-settlement` | 创建结算记录 |
| GET | `/api/admin/supplier-settlement` | 结算历史 |

> ⚠️ 见第 8 节「已知问题」：该控制器路径与全局上下文路径重复。

---

## 4. 幂等与限流

### 4.1 幂等

除批量接口外，创建类接口均要求 `Idempotency-Key` 请求头，格式为 `^[A-Za-z0-9._:-]{16,64}$`。

| 场景 | 行为 |
| --- | --- |
| 同一用户 + 同一 Key + 同一请求体 | 返回首次创建的结果，不重复创建 |
| 同一用户 + 同一 Key + **不同**请求体 | 返回 HTTP 409 |
| 发票创建 | 应用层幂等键 + 数据库唯一索引双重保障 |
| 额度操作 | 幂等键落在 `user_quota_transaction` 表 |
| 充值申请 | 手续费/截图/备注任一不同即视为不同请求 |

并发下若触发唯一索引冲突（`DuplicateKeyException`），服务会回查已存在记录并返回，
保证同一 Key 只产生一条数据。

### 4.2 限流

限流为进程内滑动窗口实现（`RateLimitService`），**单实例有效**，多实例部署需改用集中式存储。

| 接口 | 维度 | 限制 |
| --- | --- | --- |
| 登录 | IP | 每分钟 30 次 |
| 登录 | IP + 用户名 | 每分钟 10 次 |
| 登录失败锁定 | 用户名 | 连续失败 5 次锁定 15 分钟 |
| 注册 | IP | 每分钟 5 次 |
| 发票提交 | 用户 | 每分钟 10 次 |
| 批量提交 | 用户 | 每分钟 3 次（可配） |
| 批量提交 | IP | 每 5 分钟 20 次（可配） |
| OpenAPI 创建 | 用户 | 每分钟 60 次 |
| OpenAPI 批量 | 用户 | 每分钟 20 次 |
| OpenAPI 查询 / 下载 | 用户 | 每分钟 120 次 |
| OpenAPI 取消 | 用户 | 每分钟 30 次 |
| 额度查询 | 用户 | 每分钟 60 次 |
| AI 识别 | 用户 | 每分钟 20 次 |
| 用户管理写入 | 管理员 | 每分钟 30 次 |
| 密码重置 | 管理员 | 每分钟 10 次 |
| 额度管理 | 管理员 | 每分钟 30 次 |
| API Key 生成 | 用户 | 每分钟 10 次 |

触发限流返回 HTTP 429，响应体含重试等待秒数。

---

## 5. 文件存储与校验

发票图片默认存放在后端工作目录的 `uploads` 文件夹，仅能通过鉴权接口访问。

**校验层次**（全部通过才会落盘）：

1. 文件大小 ≤ 10MB
2. 扩展名仅允许 JPG / JPEG / PNG
3. 扩展名与声明的 MIME 类型一致
4. 文件头（魔数）与声明类型一致
5. 真实解码格式合法
6. 图片宽高 ≤ 8000 像素，总像素数 ≤ 3000 万（均可配）

**文件名安全**：拒绝空文件名、`..`、路径分隔符及非法字符，长度不超过 255 字符，
避免路径穿越。充值转账截图走独立的存储与访问逻辑。

---

## 6. 环境变量

| 变量 | 默认值 | 必填 | 用途 |
| --- | --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | 否 | 数据库地址 |
| `DB_PORT` | `3306` | 否 | 数据库端口 |
| `DB_NAME` | `invoice_system` | 否 | 数据库名 |
| `DB_USERNAME` | `root` | 否 | 数据库用户 |
| `DB_PASSWORD` | 无 | **是** | 数据库密码 |
| `JWT_SECRET` | 本地脚本生成 | **是** | JWT 签名密钥（≥32 字节） |
| `JWT_EXPIRATION` | `86400000` | 否 | 令牌有效期（24 小时） |
| `JWT_REMEMBER_ME_EXPIRATION` | `604800000` | 否 | 记住我有效期（7 天） |
| `FILE_UPLOAD_PATH` | `./uploads` | 否 | 发票图片目录 |
| `CORS_ALLOWED_ORIGINS` | 本机前端地址 | 否 | 允许的前端来源，逗号分隔 |
| `INVOICE_IMAGE_MAX_WIDTH` | `8000` | 否 | 图片最大宽度 |
| `INVOICE_IMAGE_MAX_HEIGHT` | `8000` | 否 | 图片最大高度 |
| `INVOICE_IMAGE_MAX_PIXELS` | `30000000` | 否 | 最大总像素数 |
| `INVOICE_BATCH_MAX_ITEMS` | `100` | 否 | 单次批量最大条数 |
| `INVOICE_BATCH_MAX_REQUEST_BYTES` | `524288` | 否 | 批量请求体上限 |
| `INVOICE_BATCH_USER_LIMIT` | `3` | 否 | 批量提交用户维度次数 |
| `INVOICE_BATCH_USER_WINDOW_MINUTES` | `1` | 否 | 批量提交用户维度窗口（分钟） |
| `INVOICE_BATCH_IP_LIMIT` | `20` | 否 | 批量提交 IP 维度次数 |
| `INVOICE_BATCH_IP_WINDOW_MINUTES` | `5` | 否 | 批量提交 IP 维度窗口（分钟） |
| `AI_ENABLED` | `true` | 否 | 是否启用 AI 识别 |
| `AI_PROVIDER` | `deepseek` | 否 | `deepseek` / `openai` / `gemini` / `qwen` |
| `AI_API_KEY` | 空 | 启用 AI 时必填 | 模型服务密钥 |
| `AI_API_URL` | 空 | 否 | 自定义接口地址；留空时按 provider 用默认地址 |
| `AI_MODEL` | `deepseek-v4-flash` | 否 | 模型名称 |
| `AI_TIMEOUT_SECONDS` | `20` | 否 | AI 请求超时（1–120 秒） |
| `MYBATIS_SQL_LOG_IMPL` | `NoLoggingImpl` | 否 | SQL 日志实现，默认关闭（见下） |

> **SQL 日志默认关闭**：`StdOutImpl` 会把含金额、公司名称、税号等敏感数据的完整 SQL 打印到标准输出。
> 仅在本地排查问题时临时开启：
> ```powershell
> $env:MYBATIS_SQL_LOG_IMPL = 'org.apache.ibatis.logging.stdout.StdOutImpl'
> ```

---

## 7. 本地启动

### 7.1 环境要求

JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.0+

### 7.2 一键启动（推荐）

```powershell
.\start-local.ps1
```

脚本会在首次启动时生成随机 JWT 密钥并保存到被 Git 忽略的 `backend/.local/jwt-secret`，后续自动复用；
同时按「进程环境变量 → 用户环境变量 → 机器环境变量 → `backend/.local/ai-api-key` 文件」的顺序
查找 `AI_API_KEY`，找到时自动启用 AI 识别。
生产环境**不要**使用本地密钥文件，必须通过部署平台或密钥管理服务注入 `JWT_SECRET`。

### 7.3 手动启动

```powershell
$env:DB_PASSWORD = '<本机 MySQL 密码>'
$env:JWT_SECRET  = '<至少 32 字节的随机密钥>'

# 后端 → http://localhost:9090/api，文档 http://localhost:9090/api/doc.html
cd backend; mvn spring-boot:run

# 前端 → http://localhost:8080（开发服务器将 /api 代理到 9090）
cd frontend; npm install; npm run dev
```

### 7.4 数据库初始化与升级

后端首次启动由 Flyway 依次执行 `V1` → `V21`，自动建表并写入默认账号。

- **新服务器部署（推荐）**：创建字符集 `utf8mb4`、排序规则 `utf8mb4_unicode_ci` 的**空**数据库
  `invoice_system`，配置 `DB_PASSWORD` 与 `JWT_SECRET` 后启动。**不要**预先执行 `init.sql`。
- **已有数据库升级**：先备份，再直接启动新版后端，Flyway 只执行未应用的增量脚本。
- **手工全量初始化**：`backend/src/main/resources/init.sql` 仅整合 **V1–V4** 的结构
  （`user`、`invoice`、`invoice_batch` 三张表），并且会**先删除 `invoice_system` 及其全部数据**。
  执行后首次启动前需临时设置 `SPRING_FLYWAY_BASELINE_VERSION=4`，Flyway 会从 V5 继续补齐
  额度、充值、OpenAPI、红冲、结算等后续结构。**除非确有需要，否则不推荐此方式。**
- **版本号 18 缺失**：源码中不包含 `V18` 迁移（`application.yml` 通过
  `ignore-migration-patterns: "*:missing"` 忽略已执行但源码缺失的历史脚本），
  因此迁移编号存在断点，属预期行为。

### 7.5 默认账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin123` |
| 开票员 | `clerk` | `clerk123` |
| 普通用户 | `user` | `user123` |

> ⚠️ 首次部署后请立即修改默认密码。

---

## 8. 测试与构建

```powershell
cd backend;  mvn test          # JUnit 5 + Mockito + MockMvc
cd frontend; npm test          # Vitest
             npm run test:e2e  # Playwright
             npm run build     # 类型检查 + 生产构建
```

后端测试覆盖控制器、安全过滤器（JWT / API Key / 批量体积）、限流、登录锁定、额度、
充值审核、供应商结算与工具类；前端测试覆盖 API 层、工具函数与主要视图。

### Docker 部署

```powershell
docker compose up -d                    # 开发用，读取根目录 .env
cd deploy; docker compose up -d         # 离线部署包，含 start.sh
```

三容器：MySQL + 后端 + 前端 Nginx。详见 `DOCKER.md` 与 `deploy/README.md`。

---

## 9. 已知问题

以下为源码审查中发现、**尚未修复**的问题，请知悉：

1. **供应商结算接口路径重复 `/api`**
   `SupplierSettlementController` 声明 `@RequestMapping("/api/admin/supplier-settlement")`，
   而全局 `server.servlet.context-path` 已是 `/api`，实际路径变为 `/api/api/admin/supplier-settlement`。
   前端 `frontend/src/api/supplierSettlement.ts` 中 `baseURL` 为 `/api`，请求路径又写了 `/api/admin/...`，
   拼接后同样指向 `/api/api/...`，两侧"错得一致"因而可能在开发环境偶然可用。
   但 `SecurityConfig` 中的 `/api/admin/**` 规则**永远无法匹配**，该接口实际落入
   `anyRequest().authenticated()`，即**任何已登录用户**（含普通用户）都能调用，
   与方法上的 `@PreAuthorize("hasRole('ADMIN')")` 形成双重保险但配置意图失效。
   **建议**：将控制器路径改为 `/admin/supplier-settlement`，前端同步改为 `/admin/supplier-settlement`。

2. **限流为单实例内存实现**
   `RateLimitService` 使用进程内 `ConcurrentHashMap` 滑动窗口，多实例部署时各实例独立计数，
   实际限流阈值为「配置值 × 实例数」。横向扩展前需替换为 Redis 等集中式方案。

3. **`init.sql` 内容陈旧且具破坏性**
   脚本头部注释即写明仅覆盖 V1–V4，缺少 `user_quota`、`recharge_request`、
   `supplier_settlement` 等后续表，且会 `DROP` 整个数据库不可恢复。
   推荐始终使用「建空库 + Flyway 自动迁移」的方式，或将该脚本同步更新到最新版本号。

4. **默认账号与示例密钥**
   `docker-compose.yml` 中 `JWT_SECRET` 与 `DB_PASSWORD` 提供了可用的开发默认值，
   生产部署必须通过环境变量覆盖。

5. **仓库体积**
   根目录 `invoice-backend.tar`（约 102MB）与 `invoice-frontend.tar`（约 26MB）为构建产物，
   建议移出仓库或改用镜像仓库分发。

---

## 10. 安全设计小结

- **认证**：JWT（`Authorization: Bearer`）与 API Key 双通道，均无状态；密码使用 BCrypt 存储
- **授权**：URL 规则 + 方法级 `@PreAuthorize` + 服务层业务校验三层防护
- **越权防护**：下载、取消、红冲申请均校验资源归属，跨用户返回 403
- **金额安全**：全流程使用 `BigDecimal`，禁止浮点数；金额精度与区间双重校验
- **并发安全**：额度操作对用户行与额度行加 `SELECT ... FOR UPDATE` 锁；
  状态流转使用条件更新（乐观并发），冲突返回 409
- **账实相符**：`syncQuotaFromTransactions()` 从流水反向推导余额与累计值，
  自动修复因异常中断导致的不一致
- **可追溯**：`traceId` 贯穿响应与日志；额度流水记录操作人、操作类型与前后余额；
  结算记录冗余操作人名称
