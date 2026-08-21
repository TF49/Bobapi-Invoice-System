package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.invoice.dto.BatchInvoiceItemRequest;
import com.invoice.dto.BatchInvoiceItemResult;
import com.invoice.dto.BatchInvoiceResponse;
import com.invoice.dto.BatchInvoiceRowError;
import com.invoice.dto.InvoiceResponse;
import com.invoice.entity.Invoice;
import com.invoice.entity.InvoiceBatch;
import com.invoice.exception.BatchValidationException;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceBatchMapper;
import com.invoice.mapper.InvoiceMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class InvoiceService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Pattern TAX_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{15,20}$");
    private static final Pattern DECIMAL_AMOUNT_PATTERN = Pattern.compile("^\\d+(?:\\.\\d{1,2})?$");
    private static final BigDecimal MIN_INVOICE_AMOUNT = new BigDecimal("0.01");

    private final InvoiceMapper invoiceMapper;
    private final InvoiceBatchMapper invoiceBatchMapper;
    private final UserQuotaService userQuotaService;
    private final Path uploadRoot;

    @Value("${file.image.max-width:8000}")
    private int maxImageWidth = 8000;

    @Value("${file.image.max-height:8000}")
    private int maxImageHeight = 8000;

    @Value("${file.image.max-pixels:30000000}")
    private long maxImagePixels = 30_000_000L;

    @Value("${app.invoice.batch.max-items:100}")
    private int maxBatchItems = 100;

    public InvoiceService(InvoiceMapper invoiceMapper, InvoiceBatchMapper invoiceBatchMapper, 
                          UserQuotaService userQuotaService, @Value("${file.upload-path}") String uploadDirectory) {
        this.invoiceMapper = invoiceMapper;
        this.invoiceBatchMapper = invoiceBatchMapper;
        this.userQuotaService = userQuotaService;
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initializeUploadDirectory() {
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建发票上传目录", exception);
        }
    }

    @Transactional
    public InvoiceResponse createInvoice(Long userId, String idempotencyKey, String companyName,
                                         String taxNumber, BigDecimal amount,
                                         String invoiceType, String remark) {
        String normalizedCompanyName = normalizeCompanyName(companyName);
        String normalizedTaxNumber = normalizeTaxNumber(taxNumber);
        String normalizedInvoiceType = normalizeInvoiceType(invoiceType);
        validateSingleInvoice(normalizedCompanyName, normalizedTaxNumber, amount, normalizedInvoiceType);
        BigDecimal normalizedAmount = normalizeAmount(amount);

        Invoice existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return validateRepeatedRequest(
                    existing, normalizedCompanyName, normalizedTaxNumber, normalizedAmount, normalizedInvoiceType);
        }

        Invoice invoice = new Invoice();
        invoice.setCompanyName(normalizedCompanyName);
        invoice.setTaxNumber(normalizedTaxNumber);
        invoice.setAmount(normalizedAmount);
        invoice.setInvoiceType(normalizedInvoiceType);
        invoice.setRemark(remark == null ? null : remark.trim());
        invoice.setStatus("PENDING");
        invoice.setUserId(userId);
        invoice.setIdempotencyKey(idempotencyKey);

        try {
            invoiceMapper.insert(invoice);
            // 插入发票成功后扣除额度并关联发票ID
            userQuotaService.deductQuota(userId, normalizedAmount, invoice.getId());
            return InvoiceResponse.from(invoice);
        } catch (DuplicateKeyException exception) {
            Invoice concurrentlyCreated = findByIdempotencyKey(userId, idempotencyKey);
            if (concurrentlyCreated == null) {
                throw exception;
            }
            return validateRepeatedRequest(
                    concurrentlyCreated, normalizedCompanyName, normalizedTaxNumber, normalizedAmount, normalizedInvoiceType);
        }
    }

    /**
     * 标准化公司名称：去除首尾空白
     */
    private String normalizeCompanyName(String companyName) {
        return companyName == null ? "" : companyName.trim();
    }

    /**
     * 标准化税号：去除首尾空白并转大写
     */
    private String normalizeTaxNumber(String taxNumber) {
        return taxNumber == null ? "" : taxNumber.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 标准化开票类型：去除首尾空白
     */
    private String normalizeInvoiceType(String invoiceType) {
        return invoiceType == null ? "" : invoiceType.trim();
    }

    /**
     * 标准化金额：设置为两位小数
     */
    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void validateSingleInvoice(String companyName, String taxNumber, BigDecimal amount,
                                       String invoiceType) {
        String validationMessage = validateCompanyName(companyName);
        if (validationMessage == null) {
            validationMessage = validateTaxNumber(taxNumber);
        }
        if (validationMessage == null) {
            validationMessage = validateAmount(amount);
        }
        if (validationMessage == null) {
            validationMessage = validateInvoiceType(invoiceType);
        }
        if (validationMessage != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, validationMessage);
        }
    }

    @Transactional
    public BatchInvoiceResponse createInvoicesBatch(Long userId, String idempotencyKey,
                                                     List<BatchInvoiceItemRequest> items) {
        List<NormalizedBatchItem> normalizedItems = validateAndNormalizeBatch(items);
        String requestHash = computeRequestHash(normalizedItems);

        InvoiceBatch existingBatch = findBatchByIdempotencyKey(userId, idempotencyKey);
        if (existingBatch != null) {
            return handleExistingBatch(existingBatch, requestHash);
        }

        BigDecimal totalAmount = normalizedItems.stream()
                .map(NormalizedBatchItem::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        InvoiceBatch batch = new InvoiceBatch();
        batch.setUserId(userId);
        batch.setIdempotencyKey(idempotencyKey);
        batch.setRequestHash(requestHash);
        batch.setTotalCount(normalizedItems.size());
        batch.setTotalAmount(totalAmount);
        batch.setStatus("COMPLETED");

        try {
            invoiceBatchMapper.insert(batch);
        } catch (DuplicateKeyException exception) {
            InvoiceBatch concurrentlyCreated = invoiceBatchMapper.selectByIdempotencyKeyForUpdate(
                    userId, idempotencyKey);
            if (concurrentlyCreated == null) {
                throw exception;
            }
            return handleExistingBatch(concurrentlyCreated, requestHash);
        }

        List<Invoice> invoices = normalizedItems.stream().map(item -> {
            Invoice invoice = new Invoice();
            invoice.setCompanyName(item.companyName());
            invoice.setTaxNumber(item.taxNumber());
            invoice.setAmount(item.amount());
            invoice.setInvoiceType(item.invoiceType());
            invoice.setRemark(item.remark());
            invoice.setStatus("PENDING");
            invoice.setUserId(userId);
            invoice.setBatchId(batch.getId());
            invoice.setBatchRowNumber(item.rowNumber());
            invoice.setIdempotencyKey(null);
            return invoice;
        }).toList();

        try {
            if (invoiceMapper.insertBatch(invoices) != invoices.size()) {
                throw new IllegalStateException("批量写入数量不一致");
            }
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50000,
                    "批量创建失败，已回滚所有记录");
        }

        return buildBatchResponse(batch, invoiceMapper.selectByBatchId(batch.getId()));
    }

    private InvoiceBatch findBatchByIdempotencyKey(Long userId, String idempotencyKey) {
        LambdaQueryWrapper<InvoiceBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvoiceBatch::getUserId, userId)
                .eq(InvoiceBatch::getIdempotencyKey, idempotencyKey);
        return invoiceBatchMapper.selectOne(wrapper);
    }

    private List<NormalizedBatchItem> validateAndNormalizeBatch(List<BatchInvoiceItemRequest> items) {
        if (items == null || items.isEmpty() || items.size() > maxBatchItems) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001,
                    "单次批量申请数量为 1～" + maxBatchItems + " 条");
        }

        List<BatchInvoiceRowError> errors = new ArrayList<>();
        List<NormalizedBatchItem> normalizedItems = new ArrayList<>(items.size());
        Set<Integer> rowNumbers = new HashSet<>();
        Set<String> rowFingerprints = new HashSet<>();

        for (int index = 0; index < items.size(); index++) {
            BatchInvoiceItemRequest item = items.get(index);
            int rowNumber = item != null && item.getRowNumber() != null
                    ? item.getRowNumber() : index + 2;
            int initialErrorCount = errors.size();

            if (rowNumber < 2) {
                addBatchError(errors, rowNumber, "rowNumber", "原始行号必须大于等于 2");
            }
            if (!rowNumbers.add(rowNumber)) {
                addBatchError(errors, rowNumber, "rowNumber", "原始行号在批次内重复");
            }

            String companyName = normalizeCompanyName(
                    item == null ? null : item.getCompanyName());
            String companyNameError = validateCompanyName(companyName);
            if (companyNameError != null) {
                addBatchError(errors, rowNumber, "companyName", companyNameError);
            }

            String taxNumber = normalizeTaxNumber(
                    item == null ? null : item.getTaxNumber());
            String taxNumberError = validateTaxNumber(taxNumber);
            if (taxNumberError != null) {
                addBatchError(errors, rowNumber, "taxNumber", taxNumberError);
            }

            String amountValue = item == null || item.getAmount() == null
                    ? "" : item.getAmount().trim();
            BigDecimal amount = null;
            BigDecimal normalizedAmount = null;
            String amountError;
            if (amountValue.isEmpty()) {
                amountError = "开票金额不能为空";
            } else if (!DECIMAL_AMOUNT_PATTERN.matcher(amountValue).matches()) {
                amountError = "开票金额格式不正确，应为最多 10 位整数和 2 位小数";
            } else {
                amount = new BigDecimal(amountValue);
                amountError = validateAmount(amount);
            }
            if (amountError != null) {
                addBatchError(errors, rowNumber, "amount", amountError);
            } else {
                normalizedAmount = normalizeAmount(amount);
            }

            String invoiceType = normalizeInvoiceType(
                    item == null ? null : item.getInvoiceType());
            String invoiceTypeError = validateInvoiceType(invoiceType);
            if (invoiceTypeError != null) {
                addBatchError(errors, rowNumber, "invoiceType", invoiceTypeError);
            }

            String remark = item == null || item.getRemark() == null
                    ? null : item.getRemark().trim();

            if (errors.size() == initialErrorCount) {
                String fingerprint = companyName + '\u0000' + taxNumber + '\u0000'
                        + normalizedAmount.toPlainString() + '\u0000' + invoiceType;
                if (!rowFingerprints.add(fingerprint)) {
                    addBatchError(errors, rowNumber, "row", "该行与批次内其他行完全重复");
                }
                normalizedItems.add(new NormalizedBatchItem(
                        rowNumber, companyName, taxNumber, normalizedAmount, invoiceType, remark));
            }
        }

        if (!errors.isEmpty()) {
            throw new BatchValidationException(errors);
        }
        return List.copyOf(normalizedItems);
    }
    /**
     * 校验开票类型，返回错误信息或 null
     */
    private String validateInvoiceType(String invoiceType) {
        if (invoiceType == null || invoiceType.isEmpty()) {
            return "开票类型不能为空";
        }
        if (invoiceType.length() > 100) {
            return "开票类型不能超过 100 个字符";
        }
        return null;
    }

    /**
     * 校验公司名称，返回错误信息或 null
     */
    private String validateCompanyName(String companyName) {
        if (companyName == null || companyName.isEmpty()) {
            return "公司名称不能为空";
        }
        if (companyName.length() > 200) {
            return "公司名称不能超过 200 个字符";
        }
        return null;
    }

    /**
     * 校验税号，返回错误信息或 null
     */
    private String validateTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.isEmpty()) {
            return "税号不能为空";
        }
        if (!TAX_NUMBER_PATTERN.matcher(taxNumber).matches()) {
            return "税号应为 15～20 位字母或数字";
        }
        return null;
    }

    /**
     * 校验金额，返回错误信息或 null
     */
    private String validateAmount(BigDecimal amount) {
        if (amount == null) {
            return "开票金额不能为空";
        }
        if (amount.compareTo(MIN_INVOICE_AMOUNT) < 0) {
            return "开票金额必须大于等于 0.01";
        }
        if (amount.scale() > 2) {
            return "开票金额最多 2 位小数";
        }
        if (Math.max(0, amount.precision() - amount.scale()) > 10) {
            return "开票金额最多 10 位整数";
        }
        return null;
    }


    private void addBatchError(List<BatchInvoiceRowError> errors, int rowNumber,
                               String field, String message) {
        errors.add(new BatchInvoiceRowError(rowNumber, field, 42202, message));
    }

    private String computeRequestHash(List<NormalizedBatchItem> items) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (NormalizedBatchItem item : items) {
                updateDigest(digest, Integer.toString(item.rowNumber()));
                updateDigest(digest, item.companyName());
                updateDigest(digest, item.taxNumber());
                updateDigest(digest, item.amount().toPlainString());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private BatchInvoiceResponse handleExistingBatch(InvoiceBatch existingBatch,
                                                      String currentHash) {
        if (!existingBatch.getRequestHash().equals(currentHash)) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902,
                    "该幂等键已用于不同的批次内容");
        }

        return buildBatchResponse(
                existingBatch, invoiceMapper.selectByBatchId(existingBatch.getId()));
    }

    private BatchInvoiceResponse buildBatchResponse(InvoiceBatch batch, List<Invoice> invoices) {
        if (invoices.size() != batch.getTotalCount()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50000,
                    "批次结果不完整，请联系管理员");
        }
        List<BatchInvoiceItemResult> results = invoices.stream()
                .map(invoice -> new BatchInvoiceItemResult(
                        invoice.getBatchRowNumber(),
                        invoice.getId(),
                        "SUCCESS",
                        "申请成功"
                ))
                .toList();

        BatchInvoiceResponse response = new BatchInvoiceResponse();
        response.setBatchId(batch.getId());
        response.setTotal(batch.getTotalCount());
        response.setSuccessCount(results.size());
        response.setFailureCount(0);
        response.setTotalAmount(batch.getTotalAmount().setScale(2).toPlainString());
        response.setItems(results);
        return response;
    }

    private record NormalizedBatchItem(
            int rowNumber,
            String companyName,
            String taxNumber,
            BigDecimal amount,
            String invoiceType,
            String remark
    ) {
    }

    public List<InvoiceResponse> getInvoicesByUserId(Long userId) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Invoice::getUserId, userId).orderByDesc(Invoice::getCreatedAt);
        return invoiceMapper.selectList(wrapper).stream()
                .map(invoice -> InvoiceResponse.from(invoice, uploadRoot)).toList();
    }

    public List<InvoiceResponse> getAllInvoices() {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Invoice::getCreatedAt);
        return invoiceMapper.selectList(wrapper).stream()
                .map(invoice -> InvoiceResponse.from(invoice, uploadRoot)).toList();
    }

    public com.invoice.dto.DashboardStats getDashboardStats() {
        // 1. 使用一条 SQL 一次性查询总体统计指标（总数、待开数、已开数、已完成总金额），保证并发一致性并减少 RTT 开销。
        // 注意：totalAmount 仅统计 COMPLETED 状态发票金额。AdminInvoice 页面展示的「申请总金额」包含所有状态，两者口径不同。
        InvoiceMapper.OverallStat overallStat = invoiceMapper.selectOverallStat();

        // 2. 查询各用户统计
        List<InvoiceMapper.UserInvoiceStat> userStats = invoiceMapper.selectUserInvoiceStats();

        // 3. 批量一次性查询所有用户的时间线数据（已限制最近 90 天，仅 COMPLETED 状态），消除 N+1 问题。
        Map<Long, List<InvoiceMapper.TimelineStatWithUser>> timelineByUser =
                invoiceMapper.selectAllTimelineStats().stream()
                        .collect(Collectors.groupingBy(InvoiceMapper.TimelineStatWithUser::userId));

        List<com.invoice.dto.DashboardStats.UserInvoiceStats> userInvoiceStats = userStats.stream()
                .map(stat -> {
                    List<com.invoice.dto.DashboardStats.TimelineData> timeline =
                            timelineByUser.getOrDefault(stat.userId(), List.of()).stream()
                                    .map(ts -> new com.invoice.dto.DashboardStats.TimelineData(
                                            ts.date(),
                                            ts.count(),
                                            ts.amount()
                                    ))
                                    .toList();
                    return new com.invoice.dto.DashboardStats.UserInvoiceStats(
                            stat.userId(),
                            stat.username(),
                            stat.completedCount(),
                            stat.pendingCount(),
                            stat.totalAmount(),
                            timeline
                    );
                })
                .toList();

        return new com.invoice.dto.DashboardStats(
                overallStat.totalInvoices(),
                overallStat.pendingInvoices(),
                overallStat.completedInvoices(),
                overallStat.totalAmount(),
                userInvoiceStats
        );
    }

    public InvoiceResponse uploadInvoiceFile(Long invoiceId, MultipartFile file) {
        Invoice invoice = requireInvoice(invoiceId);
        if (!"PENDING".equals(invoice.getStatus())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, 42201, "只有待开票申请可以上传文件");
        }

        ValidatedFile validatedFile = validateFile(file);
        String storedFileName = UUID.randomUUID() + "." + validatedFile.storedExtension();
        Path target = resolveStoredFile(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target);

            LocalDateTime completedAt = LocalDateTime.now();
            LambdaUpdateWrapper<Invoice> update = new LambdaUpdateWrapper<>();
            update.eq(Invoice::getId, invoiceId)
                    .eq(Invoice::getStatus, "PENDING")
                    .set(Invoice::getFilePath, storedFileName)
                    .set(Invoice::getFileName, validatedFile.originalFileName())
                    .set(Invoice::getStatus, "COMPLETED")
                    .set(Invoice::getCompletedAt, completedAt)
                    .set(Invoice::getUpdatedAt, completedAt);

            if (invoiceMapper.update(null, update) != 1) {
                Files.deleteIfExists(target);
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, 42201,
                        "该发票已被处理，请刷新后重试");
            }
            return InvoiceResponse.from(requireInvoice(invoiceId), uploadRoot);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            deleteFileQuietly(target, exception);
            throw exception;
        } catch (IOException exception) {
            deleteFileQuietly(target, exception);
            throw new IllegalStateException("保存发票文件失败", exception);
        }
    }

    public InvoiceDownload previewInvoiceFile(Long invoiceId, Long currentUserId, boolean admin) {
        return loadInvoiceFile(invoiceId, currentUserId, admin);
    }

    public InvoiceDownload downloadInvoiceFile(Long invoiceId, Long currentUserId, boolean admin) {
        return loadInvoiceFile(invoiceId, currentUserId, admin);
    }

    private InvoiceDownload loadInvoiceFile(Long invoiceId, Long currentUserId, boolean admin) {
        Invoice invoice = requireInvoice(invoiceId);
        if (!admin && !Objects.equals(invoice.getUserId(), currentUserId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, 40301, "不能下载其他用户的发票");
        }
        if (invoice.getFilePath() == null || invoice.getFileName() == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "发票文件不存在");
        }

        Path storedFile = resolveStoredFile(invoice.getFilePath());
        if (!Files.isRegularFile(storedFile)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "发票文件不存在");
        }

        AllowedFileType fileType = AllowedFileType.fromExtension(
                StringUtils.getFilenameExtension(invoice.getFileName()));
        if (fileType == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40402, "发票文件类型不受支持");
        }

        try {
            return new InvoiceDownload(
                    new FileSystemResource(storedFile),
                    invoice.getFileName(),
                    fileType.mediaType,
                    Files.size(storedFile)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("读取发票文件失败", exception);
        }
    }

    private Invoice requireInvoice(Long invoiceId) {
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 40401, "发票申请不存在");
        }
        return invoice;
    }

    private Invoice findByIdempotencyKey(Long userId, String idempotencyKey) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Invoice::getUserId, userId)
                .eq(Invoice::getIdempotencyKey, idempotencyKey);
        return invoiceMapper.selectOne(wrapper);
    }

    private InvoiceResponse validateRepeatedRequest(Invoice existing, String companyName,
                                                    String taxNumber, BigDecimal amount,
                                                    String invoiceType) {
        boolean samePayload = existing.getCompanyName().equals(companyName)
                && existing.getTaxNumber().equals(taxNumber)
                && existing.getAmount().compareTo(amount) == 0
                && Objects.equals(existing.getInvoiceType(), invoiceType);
        if (!samePayload) {
            throw new BusinessException(HttpStatus.CONFLICT, 40902,
                    "Idempotency-Key 已用于其他发票申请");
        }
        return InvoiceResponse.from(existing, uploadRoot);
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "请选择要上传的发票文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, 41300, "文件大小不能超过 10MB");
        }

        String originalName = sanitizeOriginalFileName(file.getOriginalFilename());
        AllowedFileType expectedType = AllowedFileType.fromExtension(
                StringUtils.getFilenameExtension(originalName));
        if (expectedType == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "只支持 JPG、JPEG 和 PNG 图片文件");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!expectedType.acceptsContentType(contentType)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件扩展名与 MIME 类型不匹配");
        }

        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(8);
            if (!expectedType.matchesSignature(header)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件内容与声明的类型不匹配");
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "无法读取上传文件");
        }

        validateDecodedImage(file, expectedType);
        return new ValidatedFile(originalName, expectedType.storedExtension);
    }

    private void validateDecodedImage(MultipartFile file, AllowedFileType expectedType) {
        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {
            if (imageInput == null) {
                throw invalidImage("图片内容无法解码");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage("图片内容无法解码");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (!expectedType.matchesFormat(reader.getFormatName())) {
                    throw invalidImage("图片实际格式与文件扩展名不匹配");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0) {
                    throw invalidImage("图片尺寸不合法");
                }
                if (width > maxImageWidth || height > maxImageHeight || pixels > maxImagePixels) {
                    throw new BusinessException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            41301,
                            "图片尺寸过大，最大允许 " + maxImageWidth + "x" + maxImageHeight
                                    + " 且总像素不超过 " + maxImagePixels
                    );
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw invalidImage("图片内容不完整或已损坏");
                }
            } finally {
                reader.dispose();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidImage("图片内容不完整或已损坏");
        }
    }

    private BusinessException invalidImage(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40003, message);
    }

    private String sanitizeOriginalFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件名不能为空");
        }
        String cleanName = StringUtils.cleanPath(originalFilename.trim());
        if (cleanName.contains("..") || cleanName.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件名不合法");
        }
        try {
            cleanName = Path.of(cleanName).getFileName().toString();
        } catch (InvalidPathException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件名不合法");
        }
        if (cleanName.length() > 255) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件名不能超过 255 个字符");
        }
        return cleanName;
    }

    private Path resolveStoredFile(String storedFileName) {
        Path resolved = uploadRoot.resolve(storedFileName).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "文件路径不合法");
        }
        return resolved;
    }

    private void deleteFileQuietly(Path file, Throwable originalException) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    public record InvoiceDownload(Resource resource, String fileName, String contentType, long contentLength) {
    }

    private record ValidatedFile(String originalFileName, String storedExtension) {
    }

    private enum AllowedFileType {
        JPEG("jpg", "image/jpeg", "JPEG", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("png", "image/png", "PNG", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        private final String storedExtension;
        private final String mediaType;
        private final String imageFormat;
        private final byte[] signature;

        AllowedFileType(String storedExtension, String mediaType, String imageFormat, byte[] signature) {
            this.storedExtension = storedExtension;
            this.mediaType = mediaType;
            this.imageFormat = imageFormat;
            this.signature = signature;
        }

        private static AllowedFileType fromExtension(String extension) {
            if (extension == null) {
                return null;
            }
            return switch (extension.toLowerCase(Locale.ROOT)) {
                case "jpg", "jpeg" -> JPEG;
                case "png" -> PNG;
                default -> null;
            };
        }

        private boolean acceptsContentType(String declaredContentType) {
            return mediaType.equals(declaredContentType)
                    || (this == JPEG && "image/jpg".equals(declaredContentType));
        }

        private boolean matchesFormat(String decodedFormat) {
            return imageFormat.equalsIgnoreCase(decodedFormat)
                    || (this == JPEG && "JPG".equalsIgnoreCase(decodedFormat));
        }

        private boolean matchesSignature(byte[] header) {
            if (header.length < signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if (header[index] != signature[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
