package com.invoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoice.dto.AiParseResponse;
import com.invoice.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 OpenAI 兼容协议的 AI 发票识别服务实现类
 * <p>
 * 支持 DeepSeek、阿里云通义千问、OpenAI 等标准兼容接口。
 * 当 application.yml 或环境变量中 {@code ai.enabled=true} 时自动启用本类替代 Stub。
 * </p>
 */
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class OpenAiCompatibleAiParseService implements AiParseService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiParseService.class);

    /** 允许的开票类型白名单（直接引用 InvoiceService 中的权威定义，两处保持同步） */
    private static final java.util.Set<String> ALLOWED_INVOICE_TYPES = InvoiceService.ALLOWED_INVOICE_TYPES;

    /** 第一阶段：从原文中提取发票字段 */
    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是一个专业的发票信息提取助手。请从用户提供的发票相关文本中提取关键信息。
            
            必须严格输出且只输出一个 JSON 对象，不得包含任何 Markdown 标记或多余文字，格式如下：
            {
              "companyName": "企业/公司完整名称（字符串，若未找到则为 null）",
              "taxNumber": "纳税人识别号/统一社会信用代码（字符串，若为个人、无税号或未找到则为 null）",
              "amount": 1234.56（开票金额数字，单位元，若未找到则为 null）,
              "invoiceType": "开票类型（仅限：技术服务费、AI订阅服务费、计算服务费、研发和技术服务之一；若文本中未明确提及任何一种则输出 null）"
            }
            """;

    /** 第二阶段：核查初步提取结果并修正 */
    private static final String VERIFY_SYSTEM_PROMPT = """
            你是一个专业的发票信息核查助手。用户已从文本中初步提取了发票信息，请根据原始文本对提取结果进行二次核查，确认准确性或进行修正。
            
            必须严格输出且只输出一个 JSON 对象，不得包含任何 Markdown 标记或多余文字，格式如下：
            {
              "companyName": "经核查确认或修正后的企业/公司完整名称（字符串，若文本中未找到则为 null）",
              "taxNumber": "经核查确认或修正后的纳税人识别号（字符串，若个人/无税号或文本中未找到则为 null）",
              "amount": 1234.56（经核查确认或修正后的开票金额，单位元，若文本中未找到则为 null）,
              "invoiceType": "经核查确认或修正后的开票类型（仅限：技术服务费、AI订阅服务费、计算服务费、研发和技术服务之一；若文本中未明确提及任何一种则输出 null）"
            }
            """;

    @Value("${ai.api-key:PLACEHOLDER}")
    private String apiKey;

    @Value("${ai.provider:deepseek}")
    private String provider;

    @Value("${ai.api-url:}")
    private String apiUrl;

    @Value("${ai.model:}")
    private String model;

    @Value("${ai.timeout-seconds:20}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleAiParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // 公开接口实现
    // -------------------------------------------------------------------------

    @Override
    public AiParseResponse parse(String text) {
        return callAiAndParse(EXTRACT_SYSTEM_PROMPT, text);
    }

    @Override
    public AiParseResponse verify(String text, String companyName, String taxNumber, BigDecimal amount, String invoiceType) {
        String userMessage = buildVerifyUserMessage(text, companyName, taxNumber, amount, invoiceType);
        return callAiAndParse(VERIFY_SYSTEM_PROMPT, userMessage);
    }

    // -------------------------------------------------------------------------
    // 私有辅助方法
    // -------------------------------------------------------------------------

    /**
     * 构建核查阶段的用户消息（原文 + 初步提取结果）
     */
    private String buildVerifyUserMessage(String text, String companyName, String taxNumber, BigDecimal amount, String invoiceType) {
        return String.format("""
                        原始文本：
                        %s
                        
                        初步提取结果（请根据原始文本核查以下信息是否准确，如有误请修正）：
                        - 公司名称：%s
                        - 纳税人识别号：%s
                        - 开票金额：%s 元
                        - 开票类型：%s
                        """,
                text,
                companyName != null ? companyName : "未识别",
                taxNumber != null ? taxNumber : "未识别",
                amount != null ? amount.toPlainString() : "未识别",
                invoiceType != null ? invoiceType : "未识别"
        );
    }

    /**
     * 通用 AI 调用 + 响应解析入口
     *
     * @param systemPrompt 系统提示词（提取 or 核查）
     * @param userMessage  用户消息
     * @return 解析后的发票字段响应
     */
    private AiParseResponse callAiAndParse(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank() || "PLACEHOLDER".equalsIgnoreCase(apiKey.trim())) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50301,
                    "AI 识别服务暂不可用，请联系管理员配置服务");
        }

        try {
            ensureSupportedProvider();
            String resolvedApiUrl = resolveApiUrl();
            String resolvedModel = resolveModel();

            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", resolvedModel);
            requestPayload.put("temperature", 0.1);
            requestPayload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));

            String requestBodyJson = objectMapper.writeValueAsString(requestPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolvedApiUrl))
                    .timeout(Duration.ofSeconds(Math.max(1, Math.min(timeoutSeconds, 120))))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("AI 接口返回非200状态码: provider={}, status={}", provider, response.statusCode());
                throw new BusinessException(HttpStatus.BAD_GATEWAY, 50201,
                        "AI 识别服务暂时不可用，请稍后重试");
            }

            return extractResponseFields(response.body());

        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 识别发票文本异常: provider={}, exception={}", provider, e.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50200,
                    "AI 识别服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 从 AI 响应体中提取、校验、清洗发票字段
     */
    private AiParseResponse extractResponseFields(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode choices = rootNode.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, 50202, "AI 接口未返回有效内容");
        }

        String content = choices.get(0).path("message").path("content").asText("").trim();
        // 清理可能包含的 markdown 代码块前缀 ```json ... ```
        if (content.startsWith("```")) {
            content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        JsonNode parsedJson = objectMapper.readTree(content);

        // 提取并校验 companyName
        String companyName = parsedJson.hasNonNull("companyName")
                ? parsedJson.get("companyName").asText().trim() : null;
        if (companyName != null && (companyName.isBlank() || companyName.length() > 200)) {
            companyName = null;
        }

        // 提取并校验 taxNumber（选填，最长 100 字符）
        String taxNumber = parsedJson.hasNonNull("taxNumber")
                ? parsedJson.get("taxNumber").asText().trim() : null;
        if (taxNumber != null) {
            if (taxNumber.isBlank() || "null".equalsIgnoreCase(taxNumber) || "无".equals(taxNumber)) {
                taxNumber = null;
            } else if (taxNumber.length() > 100) {
                taxNumber = taxNumber.substring(0, 100);
            }
        }

        // 提取并校验 amount
        BigDecimal amount = null;
        if (parsedJson.hasNonNull("amount")) {
            try {
                BigDecimal candidate = new BigDecimal(parsedJson.get("amount").asText());
                if (candidate.compareTo(new BigDecimal("0.01")) >= 0
                        && candidate.compareTo(new BigDecimal("9999999999.99")) <= 0
                        && candidate.scale() <= 2) {
                    amount = candidate.setScale(2);
                }
            } catch (Exception ignored) {
            }
        }

        // 提取并校验 invoiceType（白名单过滤，不在白名单内则返回 null）
        String invoiceType = null;
        if (parsedJson.hasNonNull("invoiceType")) {
            String candidate = parsedJson.get("invoiceType").asText("").trim();
            if (ALLOWED_INVOICE_TYPES.contains(candidate)) {
                invoiceType = candidate;
            }
        }

        // 计算置信度与提示：公司名称与金额为核心必选字段，税号为可选字段
        boolean hasCompany = companyName != null && !companyName.isBlank();
        boolean hasAmount = amount != null && amount.compareTo(BigDecimal.ZERO) > 0;

        String confidence = (hasCompany && hasAmount) ? "HIGH" : "LOW";
        StringBuilder hint = new StringBuilder();
        if (!hasCompany) hint.append("未识别到公司名称; ");
        if (!hasAmount) hint.append("未识别到有效金额; ");

        return new AiParseResponse(
                companyName,
                taxNumber,
                amount,
                invoiceType,
                confidence,
                hint.isEmpty() ? null : hint.toString().trim()
        );
    }

    private String resolveApiUrl() {
        if (apiUrl != null && !apiUrl.isBlank()) {
            return apiUrl.trim();
        }
        return switch (normalizedProvider()) {
            case "deepseek" -> "https://api.deepseek.com/chat/completions";
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            case "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            default -> throw unsupportedProvider();
        };
    }

    private String resolveModel() {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        return switch (normalizedProvider()) {
            case "deepseek" -> "deepseek-chat";
            case "openai" -> "gpt-4o-mini";
            case "gemini" -> "gemini-2.0-flash";
            case "qwen" -> "qwen-plus";
            default -> throw unsupportedProvider();
        };
    }

    private String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureSupportedProvider() {
        if (!List.of("deepseek", "openai", "gemini", "qwen").contains(normalizedProvider())) {
            throw unsupportedProvider();
        }
    }

    private BusinessException unsupportedProvider() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50302,
                "AI 识别服务配置不支持当前 provider");
    }
}
