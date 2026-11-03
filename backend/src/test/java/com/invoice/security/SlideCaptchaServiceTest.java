package com.invoice.security;

import com.invoice.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlideCaptchaServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private SlideCaptchaService service;

    @BeforeEach
    void setUp() {
        service = new SlideCaptchaService(now::get);
    }

    @Test
    void generateReturnsImagesAndDimensions() {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("127.0.0.1");

        assertThat(challenge.captchaId()).isNotBlank();
        assertThat(challenge.masterImage()).startsWith("data:image/jpeg;base64,");
        assertThat(challenge.tileImage()).startsWith("data:image/png;base64,");
        assertThat(challenge.masterWidth()).isEqualTo(SlideCaptchaService.MASTER_WIDTH);
        assertThat(challenge.masterHeight()).isEqualTo(SlideCaptchaService.MASTER_HEIGHT);
        assertThat(challenge.tileWidth()).isEqualTo(SlideCaptchaService.TILE_SIZE);
    }

    @Test
    void verifyWithinToleranceIssuesConsumableToken() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.1");
        int answerX = readAnswerX(challenge.captchaId());
        now.addAndGet(1_500);

        String token = service.verifyAndIssueToken(
                challenge.captchaId(), answerX, challenge.thumbDisplayY(), humanTrack(answerX), "10.0.0.1");
        assertThat(token).isNotBlank();

        service.consumeToken(token, "10.0.0.1");

        assertThatThrownBy(() -> service.consumeToken(token, "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失效");
    }

    @Test
    void verifyIgnoresWrongClientYWhenXIsCorrect() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.9");
        int answerX = readAnswerX(challenge.captchaId());
        int wrongY = Math.min(SlideCaptchaService.MASTER_HEIGHT - 1, challenge.thumbDisplayY() + 20);
        now.addAndGet(1_500);

        String token = service.verifyAndIssueToken(
                challenge.captchaId(), answerX, wrongY, humanTrack(answerX), "10.0.0.9");
        assertThat(token).isNotBlank();
    }

    @Test
    void verifyRejectsOutOfRangeX() {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.8");

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), -1, challenge.thumbDisplayY(), humanTrack(0), "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置无效");

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), SlideCaptchaService.MAX_OFFSET_X + 1,
                        challenge.thumbDisplayY(), humanTrack(0), "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置无效");
    }

    @Test
    void verifyRejectsWrongOffset() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.1");
        int answerX = readAnswerX(challenge.captchaId());
        int wrongX = Math.min(SlideCaptchaService.MAX_OFFSET_X, answerX + SlideCaptchaService.TOLERANCE_PX + 3);
        now.addAndGet(1_500);

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), wrongX, challenge.thumbDisplayY(), humanTrack(wrongX), "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证失败");
    }

    @Test
    void verifyRejectsCorrectOffsetWithoutPlausibleTrack() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.3");
        int answerX = readAnswerX(challenge.captchaId());
        now.addAndGet(1_500);

        // 脚本直接提交终点坐标、不带拖动过程
        List<List<Integer>> scripted = List.of(List.of(answerX, 0, 0));
        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), answerX, challenge.thumbDisplayY(), scripted, "10.0.0.3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证失败");
    }

    @Test
    void verifyRejectsSolveFasterThanServerMinimum() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.4");
        int answerX = readAnswerX(challenge.captchaId());
        now.addAndGet(100);

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), answerX, challenge.thumbDisplayY(), humanTrack(answerX), "10.0.0.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证失败");
    }

    @Test
    void challengeIsOneTimeUse() {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.2");
        assertThatThrownBy(() -> service.verifyAndIssueToken(challenge.captchaId(), 0, 0, humanTrack(0), "10.0.0.2"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), 100, challenge.thumbDisplayY(), humanTrack(100), "10.0.0.2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void consumeTokenRejectsBlankAndUnknown() {
        assertThatThrownBy(() -> service.consumeToken(null, "1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全验证");

        assertThatThrownBy(() -> service.consumeToken("not-exist", "1.1.1.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失效");
    }

    /** 模拟一次约 700ms、时间间隔有抖动的拖动 */
    private static List<List<Integer>> humanTrack(int targetX) {
        int[] intervals = {14, 17, 16, 19, 15, 22, 16, 18, 31, 17, 16, 20, 15, 24, 17, 16, 19, 26, 16, 18,
                17, 15, 21, 16, 19, 17, 23, 16, 18, 20, 16, 17, 25, 18, 16, 19, 17, 22, 16, 18};
        List<List<Integer>> track = new ArrayList<>();
        int t = 0;
        track.add(List.of(0, 0, 0));
        for (int i = 0; i < intervals.length; i++) {
            t += intervals[i];
            double progress = (i + 1) / (double) intervals.length;
            double eased = 1 - Math.pow(1 - progress, 3);
            track.add(List.of((int) Math.round(targetX * eased), i % 5 == 0 ? 1 : 0, t));
        }
        return track;
    }

    @SuppressWarnings("unchecked")
    private int readAnswerX(String captchaId) throws Exception {
        Field field = SlideCaptchaService.class.getDeclaredField("challenges");
        field.setAccessible(true);
        ConcurrentHashMap<String, ?> map = (ConcurrentHashMap<String, ?>) field.get(service);
        Object record = map.get(captchaId);
        assertThat(record).isNotNull();
        Field answerX = record.getClass().getDeclaredField("answerX");
        answerX.setAccessible(true);
        return (int) answerX.get(record);
    }
}
