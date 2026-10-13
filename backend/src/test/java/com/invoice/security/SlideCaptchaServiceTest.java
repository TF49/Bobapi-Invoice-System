package com.invoice.security;

import com.invoice.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlideCaptchaServiceTest {

    private SlideCaptchaService service;

    @BeforeEach
    void setUp() {
        service = new SlideCaptchaService();
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

        String token = service.verifyAndIssueToken(
                challenge.captchaId(), answerX, challenge.thumbDisplayY(), "10.0.0.1");
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

        String token = service.verifyAndIssueToken(challenge.captchaId(), answerX, wrongY, "10.0.0.9");
        assertThat(token).isNotBlank();
    }

    @Test
    void verifyRejectsOutOfRangeX() {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.8");

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), -1, challenge.thumbDisplayY(), "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置无效");

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), SlideCaptchaService.MAX_OFFSET_X + 1,
                        challenge.thumbDisplayY(), "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("位置无效");
    }

    @Test
    void verifyRejectsWrongOffset() throws Exception {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.1");
        int answerX = readAnswerX(challenge.captchaId());
        int wrongX = Math.min(SlideCaptchaService.MAX_OFFSET_X, answerX + SlideCaptchaService.TOLERANCE_PX + 3);

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), wrongX, challenge.thumbDisplayY(), "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证失败");
    }

    @Test
    void challengeIsOneTimeUse() {
        SlideCaptchaService.CaptchaChallenge challenge = service.generate("10.0.0.2");
        assertThatThrownBy(() -> service.verifyAndIssueToken(challenge.captchaId(), 0, 0, "10.0.0.2"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.verifyAndIssueToken(
                        challenge.captchaId(), 100, challenge.thumbDisplayY(), "10.0.0.2"))
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
