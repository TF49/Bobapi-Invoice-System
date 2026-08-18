package com.invoice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.invoice.dto.InvoiceResponse;
import com.invoice.entity.Invoice;
import com.invoice.exception.BusinessException;
import com.invoice.mapper.InvoiceMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final InvoiceMapper invoiceMapper;
    private final Path uploadRoot;

    @Value("${file.image.max-width:8000}")
    private int maxImageWidth = 8000;

    @Value("${file.image.max-height:8000}")
    private int maxImageHeight = 8000;

    @Value("${file.image.max-pixels:30000000}")
    private long maxImagePixels = 30_000_000L;

    public InvoiceService(InvoiceMapper invoiceMapper, @Value("${file.upload-path}") String uploadDirectory) {
        this.invoiceMapper = invoiceMapper;
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

    public InvoiceResponse createInvoice(Long userId, String idempotencyKey, String companyName,
                                         String taxNumber, BigDecimal amount) {
        String normalizedCompanyName = companyName.trim();
        String normalizedTaxNumber = taxNumber.trim().toUpperCase(Locale.ROOT);

        Invoice existing = findByIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return validateRepeatedRequest(existing, normalizedCompanyName, normalizedTaxNumber, amount);
        }

        Invoice invoice = new Invoice();
        invoice.setCompanyName(normalizedCompanyName);
        invoice.setTaxNumber(normalizedTaxNumber);
        invoice.setAmount(amount);
        invoice.setStatus("PENDING");
        invoice.setUserId(userId);
        invoice.setIdempotencyKey(idempotencyKey);

        try {
            invoiceMapper.insert(invoice);
            return InvoiceResponse.from(invoice);
        } catch (DuplicateKeyException exception) {
            Invoice concurrentlyCreated = findByIdempotencyKey(userId, idempotencyKey);
            if (concurrentlyCreated == null) {
                throw exception;
            }
            return validateRepeatedRequest(concurrentlyCreated, normalizedCompanyName, normalizedTaxNumber, amount);
        }
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

            LambdaUpdateWrapper<Invoice> update = new LambdaUpdateWrapper<>();
            update.eq(Invoice::getId, invoiceId)
                    .eq(Invoice::getStatus, "PENDING")
                    .set(Invoice::getFilePath, storedFileName)
                    .set(Invoice::getFileName, validatedFile.originalFileName())
                    .set(Invoice::getStatus, "COMPLETED")
                    .set(Invoice::getUpdatedAt, LocalDateTime.now());

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
                                                    String taxNumber, BigDecimal amount) {
        boolean samePayload = existing.getCompanyName().equals(companyName)
                && existing.getTaxNumber().equals(taxNumber)
                && existing.getAmount().compareTo(amount) == 0;
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
