package com.pluchon.meng.verification;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.CrudResourceStore;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import cloud.tianai.captcha.validator.impl.SimpleImageCaptchaValidator;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CaptchaProbeTest.Application.class)
class CaptchaProbeTest {
    @SpringBootApplication
    static class Application {}

    @Autowired ImageCaptchaApplication captcha;
    @Autowired ResourceStore resourceStore;

    @Test
    void starterGeneratesRealSliderAndConsumesValidationState(@TempDir Path directory) throws Exception {
        // The starter includes cutout templates, but backgrounds belong to the caller.
        Path background = directory.resolve("background.png");
        BufferedImage fixture = new BufferedImage(600, 360, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < fixture.getWidth(); x++) {
            for (int y = 0; y < fixture.getHeight(); y++) {
                fixture.setRGB(x, y, ((x * 13) & 255) << 16 | ((y * 7) & 255) << 8 | ((x + y) & 255));
            }
        }
        ImageIO.write(fixture, "png", background.toFile());
        var resources = (CrudResourceStore) resourceStore;
        resources.addResource(CaptchaTypeConstant.SLIDER, new Resource("file", background.toString()));
        var generated = captcha.generateCaptcha();
        assertTrue(generated.isSuccess());
        var image = generated.getData();
        assertNotNull(image.getBackgroundImage());
        assertNotNull(image.getTemplateImage());
        // Test code reads library state to exercise validation without a browser.
        // This state is never exposed by a product endpoint.
        String key = "captcha:" + image.getId();
        AnyMap answer = captcha.getCacheStore().getCache(key);
        assertNotNull(answer);
        Float percentage = answer.getFloat(SimpleImageCaptchaValidator.PERCENTAGE_KEY);
        var track = new ImageCaptchaTrack();
        track.setBgImageWidth(image.getBackgroundImageWidth());
        track.setTrackList(List.of(new ImageCaptchaTrack.Track(0f, 0f, 0f, "move"),
                new ImageCaptchaTrack.Track(percentage * image.getBackgroundImageWidth(), 0f, 1000f, "move")));
        assertTrue(captcha.matching(image.getId(), track).isSuccess());
        assertFalse(captcha.matching(image.getId(), track).isSuccess());
    }
}
