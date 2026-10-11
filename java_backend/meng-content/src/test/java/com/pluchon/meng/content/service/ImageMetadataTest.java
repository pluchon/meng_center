package com.pluchon.meng.content.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

class ImageMetadataTest {

    @Test
    void removesJpegLocationMetadataAndKeepsImageDecodable() throws Exception {
        var image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", encoded);
        byte[] jpeg = encoded.toByteArray();
        byte[] marker = "Exif\0\0private-location".getBytes(StandardCharsets.US_ASCII);
        var withMetadata = new ByteArrayOutputStream();
        withMetadata.write(jpeg, 0, 2);
        withMetadata.write(new byte[] {(byte) 0xff, (byte) 0xe1, 0, (byte) (marker.length + 2)});
        withMetadata.write(marker);
        withMetadata.write(jpeg, 2, jpeg.length - 2);
        byte[] clean = ImageMetadata.strip(withMetadata.toByteArray(), ".jpg", image);
        assertFalse(new String(clean, StandardCharsets.ISO_8859_1).contains("private-location"));
        assertEquals(12, ImageIO.read(new ByteArrayInputStream(clean)).getWidth());
    }

    @Test
    void stripsWebpMetadataWithoutChangingFrameData() throws Exception {
        var chunks = new ByteArrayOutputStream();
        chunk(chunks, "VP8X", new byte[] {0x0e, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        chunk(chunks, "ANIM", new byte[] {1, 2, 3, 4, 5, 6});
        chunk(chunks, "ANMF", new byte[] {7, 8, 9});
        chunk(chunks, "EXIF", new byte[] {10, 11, 12});
        chunk(chunks, "XMP ", new byte[] {13, 14});
        var file = new ByteArrayOutputStream();
        file.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        file.write(
                ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(chunks.size() + 4)
                        .array());
        file.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        file.write(chunks.toByteArray());
        byte[] clean = ImageMetadata.stripWebp(file.toByteArray());
        assertEquals(
                clean.length - 8, ByteBuffer.wrap(clean).order(ByteOrder.LITTLE_ENDIAN).getInt(4));
        assertEquals(2, clean[20]);
        assertArrayEquals(new byte[] {7, 8, 9}, java.util.Arrays.copyOfRange(clean, 52, 55));
        String text = new String(clean, StandardCharsets.ISO_8859_1);
        assertFalse(text.contains("EXIF"));
        assertFalse(text.contains("XMP "));
        byte[] truncated = java.util.Arrays.copyOf(file.toByteArray(), file.size() - 1);
        assertThrows(IOException.class, () -> ImageMetadata.stripWebp(truncated));
    }

    private void chunk(ByteArrayOutputStream output, String type, byte[] data) throws IOException {
        output.write(type.getBytes(StandardCharsets.US_ASCII));
        output.write(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
        output.write(data);
        if ((data.length & 1) != 0) output.write(0);
    }
}
