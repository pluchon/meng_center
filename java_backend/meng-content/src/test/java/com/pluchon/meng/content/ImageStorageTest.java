package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.service.ImageStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;

import javax.imageio.ImageIO;

class ImageStorageTest {

    @TempDir Path root;

    ImageStorage storage() throws Exception {
        var properties =
                (new ContentProperties(
                        root.toString(),
                        4,
                        Duration.ofSeconds(60),
                        20,
                        100,
                        "Asia/Taipei",
                        Duration.ofSeconds(3),
                        1,
                        3,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        30,
                        100,
                        5));
        var storage = new ImageStorage();
        ReflectionTestUtils.setField(storage, "properties", properties);
        ReflectionTestUtils.invokeMethod(storage, "initialize");
        return storage;
    }

    @Test
    void rejectsFakeImageAndTraversal() throws Exception {
        var s = storage();
        assertThrows(BusinessException.class, () -> s.save("not a picture renamed.png".getBytes()));
        assertThrows(BusinessException.class, () -> s.path("../../secret.png"));
    }

    @Test
    void shrinksRealImageAndKeepsDimensions() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(960, 600, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var s = storage();
        var image = s.save(bytes.toByteArray());
        assertEquals(960, image.width());
        assertEquals(600, image.height());
        assertEquals(480, ImageIO.read(s.path(image.thumbName()).toFile()).getWidth());
        assertEquals(300, ImageIO.read(s.path(image.thumbName()).toFile()).getHeight());
    }

    @Test
    void rejectsOversize() throws Exception {
        byte[] bytes = new byte[(int) ImageStorage.MAX_BYTES + 1];
        assertThrows(BusinessException.class, () -> storage().save(bytes));
    }
}
