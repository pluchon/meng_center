package com.pluchon.meng.user.config;

import static org.junit.jupiter.api.Assertions.*;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.cache.impl.LocalCacheStore;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.validator.common.constant.TrackTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 行为验证码的不变量：只出滑块、用自有背景、位置和轨迹都要过
class CaptchaConfigurationTest {

    // 模拟的客户端画布宽度，故意与原图宽度不同
    private static final int BOARD_WIDTH = 352;
    private static final int BOARD_HEIGHT = 211;

    private final CaptchaConfiguration configuration = new CaptchaConfiguration();
    private ImageCaptchaApplication captcha;

    @BeforeEach
    void setUp() {
        captcha = TACBuilder.builder()
            .setResourceStore(configuration.resourceStore())
            .setValidator(configuration.imageCaptchaValidator())
            .setCacheStore(new LocalCacheStore())
            .build();
    }

    @Test
    void generatesSliderFromOwnBackgrounds() {
        for (int round = 0; round < 12; round++) {
            ImageCaptchaVO image = generate();
            assertEquals(CaptchaTypeConstant.SLIDER, image.getType());
            assertEquals(600, image.getBackgroundImageWidth());
            assertEquals(360, image.getBackgroundImageHeight());
            assertNotNull(image.getBackgroundImage());
            assertNotNull(image.getTemplateImage());
        }
    }

    @Test
    void otherCaptchaTypesCannotBeGenerated() {
        for (String type : List.of(
            CaptchaTypeConstant.ROTATE,
            CaptchaTypeConstant.CONCAT,
            CaptchaTypeConstant.WORD_IMAGE_CLICK
        )) {
            assertFalse(generatedSuccessfully(type), type);
        }
    }

    @Test
    void humanLikeDragToTheGapPasses() {
        ImageCaptchaVO image = generate();
        assertTrue(captcha.matching(image.getId(), humanTrack(answer(image), 900)).isSuccess());
    }

    @Test
    void solutionIsConsumedOnce() {
        ImageCaptchaVO image = generate();
        ImageCaptchaTrack track = humanTrack(answer(image), 900);
        assertTrue(captcha.matching(image.getId(), track).isSuccess());
        assertFalse(captcha.matching(image.getId(), track).isSuccess());
    }

    @Test
    void wrongPositionIsRejected() {
        ImageCaptchaVO image = generate();
        float wrong = answer(image) > 0.5f ? answer(image) - 0.2f : answer(image) + 0.2f;
        assertFalse(captcha.matching(image.getId(), humanTrack(wrong, 900)).isSuccess());
    }

    @Test
    void instantJumpToTheGapIsRejected() {
        ImageCaptchaVO image = generate();
        float target = answer(image) * BOARD_WIDTH;
        ImageCaptchaTrack track = track(
            List.of(
                new ImageCaptchaTrack.Track(0f, 0f, 0f, TrackTypeConstant.DOWN),
                new ImageCaptchaTrack.Track(target, 0f, 1000f, TrackTypeConstant.UP)
            ),
            1000
        );
        assertFalse(captcha.matching(image.getId(), track).isSuccess());
    }

    @Test
    void tooFastDragIsRejected() {
        ImageCaptchaVO image = generate();
        assertFalse(captcha.matching(image.getId(), humanTrack(answer(image), 120)).isSuccess());
    }

    private ImageCaptchaVO generate() {
        var generated = captcha.generateCaptcha(CaptchaTypeConstant.SLIDER);
        assertTrue(generated.isSuccess());
        return generated.getData();
    }

    private boolean generatedSuccessfully(String type) {
        try {
            return captcha.generateCaptcha(type).isSuccess();
        } catch (RuntimeException missingTemplate) {
            return false;
        }
    }

    // 测试直接读验证码库的缓存取答案，产品接口从不暴露这个值
    private float answer(ImageCaptchaVO image) {
        return captcha.getCacheStore().getCache("captcha:" + image.getId()).getFloat(
            SimpleImageCaptchaValidator.PERCENTAGE_KEY
        );
    }

    // 先快后慢、带轻微上下抖动的拖动轨迹
    private ImageCaptchaTrack humanTrack(float percentage, int durationMillis) {
        int steps = 30;
        float distance = percentage * BOARD_WIDTH;
        List<ImageCaptchaTrack.Track> points = new ArrayList<>();
        points.add(new ImageCaptchaTrack.Track(0f, 0f, 0f, TrackTypeConstant.DOWN));
        for (int step = 1; step <= steps; step++) {
            float progress = (float) step / steps;
            float eased = 1 - (1 - progress) * (1 - progress);
            points.add(
                new ImageCaptchaTrack.Track(
                    distance * eased,
                    (float) (step % 3 - 1),
                    durationMillis * progress,
                    TrackTypeConstant.MOVE
                )
            );
        }
        points.add(new ImageCaptchaTrack.Track(distance, 0f, (float) durationMillis, TrackTypeConstant.UP));
        return track(points, durationMillis);
    }

    private ImageCaptchaTrack track(List<ImageCaptchaTrack.Track> points, int durationMillis) {
        long stop = System.currentTimeMillis();
        ImageCaptchaTrack track = new ImageCaptchaTrack();
        track.setBgImageWidth(BOARD_WIDTH);
        track.setBgImageHeight(BOARD_HEIGHT);
        track.setStartTime(stop - durationMillis);
        track.setStopTime(stop);
        track.setTrackList(points);
        return track;
    }
}
