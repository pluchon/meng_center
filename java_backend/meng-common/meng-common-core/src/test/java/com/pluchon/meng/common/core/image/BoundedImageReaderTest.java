package com.pluchon.meng.common.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BoundedImageReaderTest {

    private static BufferedImage roundTrip(int width, int height, int maxEdge) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input);
                return BoundedImageReader.read(reader, maxEdge);
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void imagesWithinLimitKeepOriginalSize() throws IOException {
        var image = roundTrip(400, 300, 400);
        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
    }

    @Test
    void longEdgeIsBoundedAndAspectKept() throws IOException {
        var image = roundTrip(9000, 30, 4096);
        assertTrue(image.getWidth() <= 4096);
        assertEquals(3000, image.getWidth());
        assertEquals(10, image.getHeight());
    }
}
