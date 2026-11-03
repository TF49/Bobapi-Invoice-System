package com.invoice.security;

import com.invoice.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 拼图滑块验证码：生成挑战、校验坐标与拖动轨迹、发放一次性 captchaToken。
 *
 * <p>防自动化措施：
 * <ul>
 *   <li>背景图同一行附近绘制形状不同的干扰缺口，拼图块形状/阴影强度随机，并在挖孔后叠加噪声线，
 *       使“找暗块”“像素模板精确匹配”类脚本失效；</li>
 *   <li>服务端计时：挑战签发后必须经过最短作答时间，且客户端声称的拖动耗时不得超过服务端实际经过时间；</li>
 *   <li>必须提交拖动轨迹，校验点数、起止位置、时间单调、单步跳变与时间间隔分布；</li>
 *   <li>挑战与凭证均一次性、绑定 IP、有过期时间，内存条目数设上限。</li>
 * </ul>
 * 任何纯前端验证码都无法完全阻止定制化攻击，这里的目标是显著提高批量注册成本，
 * 配合 IP / 网段 / 全局限流与失败锁定一起使用。
 */
@Service
public class SlideCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(SlideCaptchaService.class);

    public static final int MASTER_WIDTH = 300;
    public static final int MASTER_HEIGHT = 160;
    public static final int TILE_SIZE = 48;
    public static final int TOLERANCE_PX = 5;
    public static final int MAX_OFFSET_X = MASTER_WIDTH - TILE_SIZE;

    /** 挑战签发后到提交校验的最短时间（服务端计时） */
    public static final long MIN_SOLVE_MS = 600L;
    public static final int MIN_TRACK_POINTS = 6;
    public static final int MAX_TRACK_POINTS = 400;
    public static final long MIN_DRAG_MS = 250L;
    public static final long MAX_DRAG_MS = 60_000L;
    /** 轨迹相邻两点之间允许的最大横向跳变（主图像素） */
    public static final int MAX_STEP_PX = 80;
    public static final int TRACK_START_TOLERANCE_PX = 10;
    public static final int TRACK_END_TOLERANCE_PX = 3;
    public static final int MAX_TRACK_ABS_Y = 200;
    /** 客户端轨迹耗时可超出服务端实际经过时间的容差（网络与计时误差） */
    private static final long CLOCK_SLACK_MS = 1_000L;

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);
    /** 记录只含坐标、时间与 IP（不存图片），单条仅数百字节；上限放宽，避免被批量占满后拒绝服务 */
    private static final int MAX_PENDING_ENTRIES = 100_000;
    private static final float JPEG_QUALITY = 0.82f;
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_MS = 30_000L;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenRecord> tokens = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveGenerateFailures = new AtomicInteger();
    private final LongSupplier clock;
    private volatile long circuitOpenUntilMs;

    public SlideCaptchaService() {
        this(System::currentTimeMillis);
    }

    SlideCaptchaService(LongSupplier clock) {
        this.clock = clock;
    }

    public CaptchaChallenge generate(String clientIp) {
        ensureCircuitClosed();
        ensureCapacity(challenges);
        try {
            int answerX = 40 + random.nextInt(MASTER_WIDTH - TILE_SIZE - 50);
            int answerY = 10 + random.nextInt(MASTER_HEIGHT - TILE_SIZE - 20);

            PuzzleImages images = renderPuzzle(answerX, answerY);

            String captchaId = UUID.randomUUID().toString();
            long now = clock.getAsLong();
            challenges.put(captchaId, new ChallengeRecord(
                    answerX, answerY, now, now + CHALLENGE_TTL.toMillis(), normalizeIp(clientIp)));

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

    /**
     * 校验滑块位置与拖动轨迹，通过后签发一次性凭证。
     *
     * @param track 拖动轨迹，每个点为 {@code [x, y, t]}：x 为拼图块横向偏移（主图像素），
     *              y 为指针相对按下点的纵向偏移（显示像素），t 为相对按下时刻的毫秒数
     */
    public String verifyAndIssueToken(String captchaId, int x, int y, List<List<Integer>> track, String clientIp) {
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

        String ip = normalizeIp(clientIp);
        ChallengeRecord challenge = challenges.remove(captchaId);
        long now = clock.getAsLong();
        if (challenge == null || now > challenge.expiresAt()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "验证码已过期，请重新完成安全验证");
        }
        if (!challenge.clientIp().equals(ip)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "验证码无效，请重新完成安全验证");
        }

        if (Math.abs(x - challenge.answerX()) > TOLERANCE_PX) {
            // 位置错误属于正常用户失误，不记录日志
            throw verifyFailed();
        }
        long elapsed = now - challenge.issuedAt();
        String rejectReason = elapsed < MIN_SOLVE_MS
                ? "solved-too-fast(" + elapsed + "ms)"
                : validateTrack(track, x, elapsed);
        if (rejectReason != null) {
            // 统一对外错误信息，不向脚本暴露具体拒绝原因
            log.info("Captcha rejected as automated: ip={}, reason={}", ip, rejectReason);
            throw verifyFailed();
        }

        ensureCapacity(tokens);
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenRecord(now + TOKEN_TTL.toMillis(), ip));
        return token;
    }

    public void consumeToken(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "请先完成安全验证");
        }
        TokenRecord record = tokens.remove(token);
        if (record == null || clock.getAsLong() > record.expiresAt()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "安全验证已失效，请重新完成");
        }
        if (!record.clientIp().equals(normalizeIp(clientIp))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40020, "安全验证无效，请重新完成");
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanExpired() {
        long now = clock.getAsLong();
        challenges.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        tokens.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    /**
     * 校验拖动轨迹，返回拒绝原因；通过返回 {@code null}。
     */
    static String validateTrack(List<List<Integer>> track, int submittedX, long serverElapsedMs) {
        if (track == null || track.size() < MIN_TRACK_POINTS || track.size() > MAX_TRACK_POINTS) {
            return "track-size(" + (track == null ? 0 : track.size()) + ")";
        }
        Set<Integer> intervals = new HashSet<>();
        int firstX = 0;
        int firstT = 0;
        int prevX = 0;
        int prevT = 0;
        for (int i = 0; i < track.size(); i++) {
            List<Integer> point = track.get(i);
            if (point == null || point.size() != 3
                    || point.get(0) == null || point.get(1) == null || point.get(2) == null) {
                return "track-format";
            }
            int px = point.get(0);
            int py = point.get(1);
            int pt = point.get(2);
            if (px < -TRACK_START_TOLERANCE_PX || px > MAX_OFFSET_X + TRACK_START_TOLERANCE_PX
                    || Math.abs(py) > MAX_TRACK_ABS_Y || pt < 0 || pt > MAX_DRAG_MS) {
                return "track-range";
            }
            if (i == 0) {
                firstX = px;
                firstT = pt;
            } else {
                if (pt < prevT) {
                    return "track-time-order";
                }
                if (Math.abs(px - prevX) > MAX_STEP_PX) {
                    return "track-jump";
                }
                intervals.add(pt - prevT);
            }
            prevX = px;
            prevT = pt;
        }
        if (Math.abs(firstX) > TRACK_START_TOLERANCE_PX) {
            return "track-start";
        }
        if (Math.abs(prevX - submittedX) > TRACK_END_TOLERANCE_PX) {
            return "track-end";
        }
        long duration = (long) prevT - firstT;
        if (duration < MIN_DRAG_MS || duration > MAX_DRAG_MS) {
            return "track-duration(" + duration + "ms)";
        }
        if (duration > serverElapsedMs + CLOCK_SLACK_MS) {
            return "track-longer-than-elapsed(" + duration + "/" + serverElapsedMs + "ms)";
        }
        // 真实输入事件的时间间隔总有抖动；完全等间隔的轨迹基本来自脚本插值
        if (intervals.size() < 3) {
            return "track-uniform-timing";
        }
        return null;
    }

    private BusinessException verifyFailed() {
        return new BusinessException(HttpStatus.BAD_REQUEST, 40021, "验证失败，请重试");
    }

    private void ensureCapacity(Map<String, ?> map) {
        if (map.size() < MAX_PENDING_ENTRIES) {
            return;
        }
        cleanExpired();
        if (map.size() >= MAX_PENDING_ENTRIES) {
            log.warn("Captcha store is full ({} entries), rejecting new entries", map.size());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50321, "安全验证繁忙，请稍后再试");
        }
    }

    private void ensureCircuitClosed() {
        if (clock.getAsLong() < circuitOpenUntilMs) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 50320,
                    "安全验证暂不可用，请稍后再试");
        }
    }

    private void tripCircuitOnFailure() {
        if (consecutiveGenerateFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntilMs = clock.getAsLong() + CIRCUIT_OPEN_MS;
            consecutiveGenerateFailures.set(0);
        }
    }

    private PuzzleImages renderPuzzle(int holeX, int holeY) {
        BufferedImage master = new BufferedImage(MASTER_WIDTH, MASTER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = master.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g);

            int variant = random.nextInt(4);
            Shape piece = createPuzzleShape(holeX, holeY, TILE_SIZE, variant);

            // 拼图块取自挖孔前的干净背景
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

            paintHole(g, piece);

            // 干扰缺口：同一行附近、不同朝向，人眼可按形状区分
            int[] decoy = pickDecoyPosition(holeX, holeY);
            if (decoy != null) {
                int decoyVariant = (variant + 1 + random.nextInt(3)) % 4;
                paintHole(g, createPuzzleShape(decoy[0], decoy[1], TILE_SIZE, decoyVariant));
            }

            // 挖孔后再叠加噪声，使缺口区域与拼图块像素不再完全一致
            paintNoise(g);

            return new PuzzleImages(toJpegDataUrl(master), toPngDataUrl(tile));
        } finally {
            g.dispose();
        }
    }

    private void paintHole(Graphics2D g, Shape shape) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 95 + random.nextInt(41)));
        g.fill(shape);
        g.setStroke(new BasicStroke(1.0f + random.nextFloat() * 0.6f));
        g.setColor(new Color(255, 255, 255, 70 + random.nextInt(51)));
        g.draw(shape);
    }

    private int[] pickDecoyPosition(int answerX, int answerY) {
        int minGap = TILE_SIZE + 6;
        int leftLo = 8;
        int leftHi = answerX - minGap;
        int rightLo = answerX + minGap;
        int rightHi = MAX_OFFSET_X - 4;
        int leftLen = Math.max(0, leftHi - leftLo + 1);
        int rightLen = Math.max(0, rightHi - rightLo + 1);
        if (leftLen + rightLen == 0) {
            return null;
        }
        int pick = random.nextInt(leftLen + rightLen);
        int x = pick < leftLen ? leftLo + pick : rightLo + (pick - leftLen);
        int y = answerY + random.nextInt(13) - 6;
        y = Math.max(4, Math.min(MASTER_HEIGHT - TILE_SIZE - 4, y));
        return new int[]{x, y};
    }

    private void paintBackground(Graphics2D g) {
        Color from = new Color(18 + random.nextInt(30), 100 + random.nextInt(40), 80 + random.nextInt(30));
        Color to = new Color(190 + random.nextInt(40), 130 + random.nextInt(40), 30 + random.nextInt(40));
        GradientPaint gradient = new GradientPaint(
                0, random.nextInt(MASTER_HEIGHT), from,
                MASTER_WIDTH, random.nextInt(MASTER_HEIGHT), to);
        g.setPaint(gradient);
        g.fillRect(0, 0, MASTER_WIDTH, MASTER_HEIGHT);

        for (int i = 0; i < 22; i++) {
            int size = 16 + random.nextInt(70);
            int x = random.nextInt(MASTER_WIDTH);
            int y = random.nextInt(MASTER_HEIGHT);
            Color color = new Color(
                    40 + random.nextInt(180),
                    60 + random.nextInt(160),
                    50 + random.nextInt(150),
                    70 + random.nextInt(90));
            g.setColor(color);
            switch (i % 3) {
                case 0 -> g.fill(new Ellipse2D.Double(x, y, size, size));
                case 1 -> g.fill(new RoundRectangle2D.Double(x, y, size, size * 0.7, 12, 12));
                default -> {
                    Path2D tri = new Path2D.Double();
                    tri.moveTo(x, y + size);
                    tri.lineTo(x + size / 2.0, y);
                    tri.lineTo(x + size, y + size);
                    tri.closePath();
                    g.fill(tri);
                }
            }
        }
    }

    private void paintNoise(Graphics2D g) {
        int lines = 3 + random.nextInt(3);
        for (int i = 0; i < lines; i++) {
            boolean light = random.nextBoolean();
            g.setColor(light
                    ? new Color(255, 255, 255, 40 + random.nextInt(41))
                    : new Color(0, 0, 0, 30 + random.nextInt(31)));
            g.setStroke(new BasicStroke(1.0f + random.nextFloat() * 1.2f));
            g.draw(new CubicCurve2D.Double(
                    -10, random.nextInt(MASTER_HEIGHT),
                    random.nextInt(MASTER_WIDTH), random.nextInt(MASTER_HEIGHT),
                    random.nextInt(MASTER_WIDTH), random.nextInt(MASTER_HEIGHT),
                    MASTER_WIDTH + 10, random.nextInt(MASTER_HEIGHT)));
        }
    }

    /**
     * 拼图块：主体内缩，两侧凸榫外伸，轮廓始终落在 [x,y,size,size] 内。
     * variant 0-3 表示绕方框中心旋转 0/90/180/270 度。
     */
    private Shape createPuzzleShape(int x, int y, int size, int variant) {
        double tab = size * 0.18;
        double body = size - tab;
        Path2D.Double path = new Path2D.Double();
        // 从左上主体角开始，顶边 → 向上凸榫 → 右边 → 向右凸榫 → 底边 → 左边
        path.moveTo(x, y + tab);
        path.lineTo(x + body * 0.35, y + tab);
        path.curveTo(x + body * 0.35, y,
                x + body * 0.65, y,
                x + body * 0.65, y + tab);
        path.lineTo(x + body, y + tab);
        path.lineTo(x + body, y + tab + body * 0.35);
        path.curveTo(x + size, y + tab + body * 0.35,
                x + size, y + tab + body * 0.65,
                x + body, y + tab + body * 0.65);
        path.lineTo(x + body, y + size);
        path.lineTo(x, y + size);
        path.closePath();
        if (variant == 0) {
            return path;
        }
        AffineTransform rotate = AffineTransform.getRotateInstance(
                Math.toRadians(90.0 * variant), x + size / 2.0, y + size / 2.0);
        return rotate.createTransformedShape(path);
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

    private record ChallengeRecord(int answerX, int answerY, long issuedAt, long expiresAt, String clientIp) {
    }

    private record TokenRecord(long expiresAt, String clientIp) {
    }

    private record PuzzleImages(String masterDataUrl, String tileDataUrl) {
    }
}
