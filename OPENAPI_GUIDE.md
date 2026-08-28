# 发票系统 OpenAPI 开发者对接指南

本文档专为对接发票系统的外部业务系统（如「高冷马铃薯」等合作企业）开发者编写。通过本接口，您可以将您业务系统中的用户发票申请直接自动化推送到发票系统，实现开票、额度自动扣减、状态回查及发票下载。

---

## 1. 快速接入指引

### 1.1 获取 API Key
1. 登录发票系统后台。
2. 进入 **我的额度 -> OpenAPI 开发者密钥**（或由系统管理员在「用户管理」中为您分配与管理）。
3. 点击生成并复制 **API Key**（格式如：`bk_live_4a1e9b2c8d...`）。

### 1.2 认证机制
所有 OpenAPI 请求必须在 HTTP 请求头中携带 `X-API-KEY`：
```http
X-API-KEY: your_api_key_here
```
> 若未提供或密钥无效/被禁用，接口将返回 HTTP `401 Unauthorized`（业务错误码 `40101`）。

---

## 2. 接口列表与规范

统一接口前缀：`/open/v1`

| HTTP 方法 | 接口路径 | 功能描述 | 核心特性 |
| :--- | :--- | :--- | :--- |
| `POST` | `/open/v1/invoices` | 提交单条发票申请 | 支持 `outTradeNo` 幂等防重，前置校验并实时扣减额度 |
| `POST` | `/open/v1/invoices/batch` | 批量提交发票申请 | 单次最大支持 100 条，事务级原子扣减额度并返回明细 |
| `GET` | `/open/v1/invoices/{id}` | 按系统发票ID查询状态 | 返回发票状态（`PENDING`/`COMPLETED`）与下载可用标识 |
| `GET` | `/open/v1/invoices/by-out-trade-no/{outTradeNo}` | 按外部商户单号查询状态 | **最核心接口**：直接用商户自身单号回查，无需维护内部 ID 映射 |
| `GET` | `/open/v1/invoices/{id}/download` | 按系统发票ID下载发票文件 | 验证租户数据隔离，直接输出已开票图片二进制流 |
| `GET` | `/open/v1/invoices/by-out-trade-no/{outTradeNo}/download` | 按外部商户单号下载发票文件 | 直接凭商户单号下载发票，便捷自动化集成 |
| `GET` | `/open/v1/quota` | 查询账户额度信息 | 返回当前可用余额、累计充值与累计消耗，方便前置预警 |

---

### 2.1 提交发票申请 (单条)

- **请求路径**：`POST /open/v1/invoices`
- **请求方式**：`POST`
- **Content-Type**：`application/json`
- **请求头**：
  - `X-API-KEY`: `bk_live_your_api_key` (必填)
  - `Idempotency-Key`: 16-64位幂等键 (选填，若未传则默认使用 `open_` + `outTradeNo`)

#### 请求参数 (JSON Body)

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
| :--- | :--- | :--- | :--- | :--- |
| `outTradeNo` | String | 否 (推荐填) | 外部商户订单号 (单用户下唯一，重试时自动幂等，防重复扣款) | `ORDER_202608280001` |
| `companyName` | String | **是** | 开票抬头 / 购买方公司名称 (最多 200 字) | `北京极客科技有限公司` |
| `taxNumber` | String | 否 | 购买方纳税人识别号 (个人或无税号可不传，最长 100 字符) | `91110000MA00000000` |
| `amount` | Number | **是** | 开票金额 (单位：元，最小 0.01，最多两位小数) | `500.00` |
| `invoiceType` | String | 否 | 开票类型。可选值：`技术服务费`、`AI订阅服务费`、`计算服务费` (默认 `技术服务费`) | `技术服务费` |
| `remark` | String | 否 | 发票备注 / 用户申请说明 (最多 500 字) | `系统自动化推单` |

#### 响应示例 (成功)

```json
{
  "code": 200,
  "message": "申请成功",
  "data": {
    "id": 1024,
    "outTradeNo": "ORDER_20260828001",
    "companyName": "北京极客科技有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "系统自动化推单",
    "status": "PENDING",
    "downloadable": false,
    "fileName": null,
    "createdAt": "2026-08-28T12:30:00",
    "completedAt": null
  }
}
```

---

### 2.2 批量提交发票申请

- **请求路径**：`POST /open/v1/invoices/batch`
- **请求方式**：`POST`
- **Content-Type**：`application/json`
- **请求头**：
  - `X-API-KEY`: `bk_live_your_api_key` (必填)
  - `Idempotency-Key`: 16-64位批次幂等键 (选填)

#### 请求参数 (JSON Body)

```json
{
  "items": [
    {
      "rowNumber": 2,
      "companyName": "北京极客科技有限公司",
      "taxNumber": "91110000MA00000000",
      "amount": "500.00",
      "invoiceType": "技术服务费",
      "remark": "第1条备注"
    },
    {
      "rowNumber": 3,
      "companyName": "上海数据网络有限公司",
      "taxNumber": "91310000MA12345678",
      "amount": "300.00",
      "invoiceType": "AI订阅服务费",
      "remark": "第2条备注"
    }
  ]
}
```

---

### 2.3 按外部订单号查询发票状态

- **请求路径**：`GET /open/v1/invoices/by-out-trade-no/{outTradeNo}`
- **请求方式**：`GET`
- **请求头**：`X-API-KEY: bk_live_your_api_key`

#### 响应示例 (开票完成)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1024,
    "outTradeNo": "ORDER_20260828001",
    "companyName": "北京极客科技有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "系统自动化推单",
    "status": "COMPLETED",
    "downloadable": true,
    "fileName": "invoice_1024.png",
    "createdAt": "2026-08-28T12:30:00",
    "completedAt": "2026-08-28T12:45:00"
  }
}
```

#### 状态枚举值 (`status`)
- `PENDING`：待开票（管理员/开票员正在处理中）
- `COMPLETED`：已开票（发票文件已上传，`downloadable` 为 `true`）

---

### 2.4 下载发票文件

当发票状态为 `COMPLETED` 且 `downloadable: true` 时，可通过以下接口直接下载已开具的发票图片：

- **方式一（按系统发票 ID 下载）**：
  `GET /open/v1/invoices/{id}/download`
- **方式二（按外部商户订单号下载）**：
  `GET /open/v1/invoices/by-out-trade-no/{outTradeNo}/download`
- **请求头**：`X-API-KEY: bk_live_your_api_key`
- **响应**：直接返回图片二进制流（`image/jpeg` 或 `image/png`），附带 `Content-Disposition: attachment; filename="xxx.png"`。

---

### 2.5 查询账户额度信息

用于外部系统在推单前预先检查自身额度余额。

- **请求路径**：`GET /open/v1/quota`
- **请求方式**：`GET`
- **请求头**：`X-API-KEY: bk_live_your_api_key`

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "balance": 18500.00,
    "totalRecharged": 20000.00,
    "totalDeducted": 1500.00
  }
}
```

---

## 3. 错误码与异常处理

系统所有接口统一遵循 `{ "code": number, "message": string, "data": null }` 结构。

| HTTP 状态码 | 业务 Code | 错误说明 | 应对建议 |
| :--- | :--- | :--- | :--- |
| `401 Unauthorized` | `40101` | 缺少 `X-API-KEY` 请求头或 API Key 无效/被禁用 | 检查请求头 `X-API-KEY` 拼写与后台密钥启用状态 |
| `400 Bad Request` | `40001` | 缺少必要参数（如公司名称为空、外部单号为空） | 核对请求字段是否完整 |
| `400 Bad Request` | `40002` | **账户额度不足** | 提示开发者额度已用尽，请在后台充值后重试 |
| `400 Bad Request` | `40003` | 开票类型或参数校验失败 | 确保 `invoiceType` 属于支持的品名枚举 |
| `404 Not Found` | `40401` | 发票记录不存在（或不属于该 API Key 对应账号） | 核对 `outTradeNo` 或 `id` 是否正确 |
| `404 Not Found` | `40402` | 发票尚未开具完成，文件暂不存在 | 先轮询查询 `downloadable: true` 再发起下载 |
| `429 Too Many Requests` | `42902` / `42905` | 接口调用频率超限 | 降低请求并发，依据响应头或稍后重试 |

---

## 4. 多语言调用示例代码

### 4.1 cURL 示例

```bash
# 1. 提交单条发票 (自动扣减额度)
curl -X POST "http://your-server-host/open/v1/invoices" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: bk_live_your_api_key_here" \
  -d '{
    "outTradeNo": "MY_ORDER_20260828001",
    "companyName": "北京极客科技有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "平台用户自助申请"
  }'

# 2. 查询发票状态
curl -X GET "http://your-server-host/open/v1/invoices/by-out-trade-no/MY_ORDER_20260828001" \
  -H "X-API-KEY: bk_live_your_api_key_here"

# 3. 下载发票文件
curl -X GET "http://your-server-host/open/v1/invoices/by-out-trade-no/MY_ORDER_20260828001/download" \
  -H "X-API-KEY: bk_live_your_api_key_here" \
  -o "invoice.png"

# 4. 查询账户额度
curl -X GET "http://your-server-host/open/v1/quota" \
  -H "X-API-KEY: bk_live_your_api_key_here"
```

### 4.2 Python (requests) 示例

```python
import requests

BASE_URL = "http://your-server-host"
API_KEY = "bk_live_your_api_key_here"

headers = {
    "X-API-KEY": API_KEY,
    "Content-Type": "application/json"
}

# 1. 提交发票申请
invoice_data = {
    "outTradeNo": "MY_ORDER_20260828001",
    "companyName": "北京极客科技有限公司",
    "taxNumber": "91110000MA00000000",
    "amount": 500.00,
    "invoiceType": "技术服务费",
    "remark": "用户自助开票"
}
res = requests.post(f"{BASE_URL}/open/v1/invoices", headers=headers, json=invoice_data)
print("提交发票响应:", res.json())

# 2. 查询开票状态
res = requests.get(f"{BASE_URL}/open/v1/invoices/by-out-trade-no/MY_ORDER_20260828001", headers=headers)
status_data = res.json()
print("开票状态:", status_data)

# 3. 如果开票完成，直接下载发票图片
if status_data.get("data", {}).get("downloadable"):
    download_res = requests.get(f"{BASE_URL}/open/v1/invoices/by-out-trade-no/MY_ORDER_20260828001/download", headers=headers)
    with open("invoice.png", "wb") as f:
        f.write(download_res.content)
    print("发票文件下载成功: invoice.png")
```

### 4.3 Node.js / TypeScript (axios) 示例

```typescript
import axios from 'axios'

const client = axios.create({
  baseURL: 'http://your-server-host',
  headers: {
    'X-API-KEY': 'bk_live_your_api_key_here'
  }
})

async function main() {
  // 1. 提交发票
  const createRes = await client.post('/open/v1/invoices', {
    outTradeNo: 'MY_ORDER_20260828001',
    companyName: '北京极客科技有限公司',
    taxNumber: '91110000MA00000000',
    amount: 500.00,
    invoiceType: '技术服务费',
    remark: '用户自助申请'
  })
  console.log('提交成功:', createRes.data)

  // 2. 查询额度
  const quotaRes = await client.get('/open/v1/quota')
  console.log('剩余可用额度:', quotaRes.data.data.balance)
}

main().catch(console.error)
```
