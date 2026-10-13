package com.invoice.security;

import com.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拼图滑块验证码：生成挑战、校验坐标、发放一次性 captchaToken。
 */
@Service
public class SlideCaptchaService {

    public static final int MASTER_WIDTH = 300;
    public static final int MASTER_HEIGHT = 160;
    public static final int TILE_SIZE = 48;
    public static final int TOLERANCE_PX = 5;
    public static final int MAX_OFFSET_X = MASTER_WIDTH - TILE_SIZE;

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    private static final float JPEG_QUALITY = 0.82f;
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_MS = 30_000L;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenRecord> tokens = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveGenerateFailures = new AtomicInteger();
    private volatile long circuitOpenUntilMs;

    public CaptchaChallenge generate(String clientIp) {
        ensureCircuitClosed();
        try {
            int answerX = 40 + random.nextInt(MASTER_WIDTH - TILE_SIZE - 50);
            int answerY = 10 + random.nextInt(MASTER_HEIGHT - TILE_SIZE - 20);

            PuzzleImages images = renderPuzzle(answerX, answerY);

            String captchaId = UUID.randomUUID().toString();
            challenges.put(captchaId, new ChallengeRecord(
                    answerX, answerY, System.currentTimeMillis() + CHALLENGE_TTL.toMillis(), normalizeIp(clientIp)));

            consecutiveGenerateFailures.set(0);
            return new CaptchaChallenge(
                    captchaId,
                    images.masterDataUrl(),
                    images.tileDataUrl(),
                    TILE_SIZE,
                    TILE_SIZE,
                    MASTER_WIDTH,
                    MASTER_HEIGHT,
                    0,
                    answerY
            );
        } catch (BusinessException ex) {
            tripCircuitOnFailure();
            throw ex;
        } catch (RuntimeException ex) {
            tripCircuitOnFailure();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50020, "验证码生成失败");
        }
    }

    public String verifyAndIssueToken(String captchaId, int x, int y, String clientIp) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "验证码无效，请重新完成安全验证");
        }
        if (x < 0 || x > MAX_OFFSET_X) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40022, "滑块位置无效");
        }
        // 客户端 y 不可信（缺口纵坐标已通过拼图图像泄露），仅用服务端记录校验 X。
        if (y < 0 || y > MASTER_HEIGHT - 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40022, "滑块位置无效");
        }

        ChallengeRecord challenge = challenges.remove(captchaId);
        if (challenge == null || System.currentTimeMillis() > challenge.expiresAt()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "验证码已过期，请重新完成安全验证");
        }
        if (!challenge.clientIp().equals(normalizeIp(clientIp))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "验证码无效，请重新完成安全验证");
        }

        if (Math.abs(x - challenge.answerX()) > TOLERANCE_PX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40021, "验证失败，请重试");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenRecord(
                System.currentTimeMillis() + TOKEN_TTL.toMillis(), normalizeIp(clientIp)));
        return token;
    }

    public void consumeToken(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "请先完成安全验证");
        }
        TokenRecord record = tokens.remove(token);
        if (record == null || System.currentTimeMillis() > record.expiresAt()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "安全验证已失效，请重新完成");
        }
        if (!record.clientIp().equals(normalizeIp(clientIp))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "安全验证无效，请重新完成");
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        tokens.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    private void ensureCircuitClosed() {
        if (System.currentTimeMillis() < circuitOpenUntilMs) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50320,
                    "安全验证暂不可用，请稍后再试");
        }
    }

    private void tripCircuitOnFailure() {
        if (consecutiveGenerateFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntilMs = System.currentTimeMillis() + CIRCUIT_OPEN_MS;
            consecutiveGenerateFailures.set(0);
        }
    }

    private PuzzleImages renderPuzzle(int holeX, int holeY) {
        BufferedImage master = new BufferedImage(MASTER_WIDTH, MASTER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = master.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g);
            Shape piece = createPuzzleShape(holeX, holeY, TILE_SIZE);

            BufferedImage tile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tile.createGraphics();
            try {
                tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                tg.translate(-holeX, -holeY);
                tg.setClip(piece);
                tg.drawImage(master, 0, 0, null);
                tg.setClip(null);
                tg.setStroke(new BasicStroke(1.5f));
                tg.setColor(new Color(255, 255, 255, 220));
                tg.draw(piece);
            } finally {
                tg.dispose();
            }

            Area hole = new Area(piece);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(hole);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(255, 255, 255, 90));
            g.draw(hole);

            return new PuzzleImages(toJpegDataUrl(master), toPngDataUrl(tile));
        } finally {
            g.dispose();
        }
    }

    private void paintBackground(Graphics2D g) {
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(18, 113, 91),
                MASTER_WIDTH, MASTER_HEIGHT, new Color(214, 154, 45));
        g.setPaint(gradient);
        g.fillRect(0, 0, MASTER_WIDTH, MASTER_HEIGHT);

        for (int i = 0; i < 18; i++) {
            int size = 20 + random.nextInt(70);
            int x = random.nextInt(MASTER_WIDTH);
            int y = random.nextInt(MASTER_HEIGHT);
            Color color = new Color(
                    40 + random.nextInt(180),
                    60 + random.nextInt(160),
                    50 + random.nextInt(150),
                    70 + random.nextInt(90));
            g.setColor(color);
            if (i % 3 == 0) {
                g.fill(new Ellipse2D.Double(x, y, size, size));
            } else if (i % 3 == 1) {
                g.fill(new RoundRectangle2D.Double(x, y, size, size * 0.7, 12, 12));
            } else {
                Path2D tri = new Path2D.Double();
                tri.moveTo(x, y + size);
                tri.lineTo(x + size / 2.0, y);
                tri.lineTo(x + size, y + size);
                tri.closePath();
                g.fill(tri);
            }
        }
    }

    /**
     * 拼图块：主体内缩，顶部/右侧凸榫外伸，轮廓仍落在 [x,y,size,size] 内。
     */
    private Shape createPuzzleShape(int x, int y, int size) {
        double tab = size * 0.18;
        double body = size - tab;
        Path2D.Double path = new Path2D.Double();
        // 从左上主体角开始，顶边 → 向上凸榫 → 右边 → 向右凸榫 → 底边 → 左边
        path.moveTo(x, y + tab);
        path.lineTo(x + body * 0.35, y + tab);
        // 顶部外凸
        path.curveTo(x + body * 0.35, y,
                x + body * 0.65, y,
                x + body * 0.65, y + tab);
        path.lineTo(x + body, y + tab);
        path.lineTo(x + body, y + tab + body * 0.35);
        // 右侧外凸
        path.curveTo(x + size, y + tab + body * 0.35,
                x + size, y + tab + body * 0.65,
                x + body, y + tab + body * 0.65);
        path.lineTo(x + body, y + size);
        path.lineTo(x, y + size);
        path.closePath();
        return path;
    }

    private String toJpegDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IOException("No JPEG writer");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50020, "验证码生成失败");
        }
    }

    private String toPngDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 50020, "验证码生成失败");
        }
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    public record CaptchaChallenge(
            String captchaId,
            String masterImage,
            String tileImage,
            int tileWidth,
            int tileHeight,
            int masterWidth,
            int masterHeight,
            int thumbDisplayX,
            int thumbDisplayY
    ) {
    }

    private record ChallengeRecord(int answerX, int answerY, long expiresAt, String clientIp) {
    }

    private record TokenRecord(long expiresAt, String clientIp) {
    }

    private record PuzzleImages(String masterDataUrl, String tileDataUrl) {
    }
}
