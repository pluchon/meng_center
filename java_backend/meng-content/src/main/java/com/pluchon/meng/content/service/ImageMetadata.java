package com.pluchon.meng.content.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

final class ImageMetadata {

    private ImageMetadata() {}

    static byte[] strip(byte[] original, String suffix, BufferedImage image)
        throws IOException {
        if (suffix.equals(".gif")) return original;
        if (suffix.equals(".webp")) return stripWebp(original);
        var output = new ByteArrayOutputStream();
        if (
            !ImageIO.write(
                image,
                suffix.equals(".jpg") ? "jpeg" : "png",
                output
            )
        ) {
            throw new IOException("Image encoder unavailable");
        }
        return output.toByteArray();
    }

    // Preserve WebP frame and color data; discard EXIF/XMP without re-encoding animation.
    static byte[] stripWebp(byte[] original) throws IOException {
        if (original.length < 12) throw new IOException("Invalid WebP header");
        var input = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        long end = Integer.toUnsignedLong(input.getInt(4)) + 8;
        if (end != original.length) throw new IOException(
            "Invalid WebP length"
        );
        var output = new ByteArrayOutputStream(original.length);
        output.write(original, 0, 12);
        int offset = 12;
        while (offset < end) {
            if (offset + 8L > end) throw new IOException("Invalid WebP chunk");
            String kind = new String(
                original,
                offset,
                4,
                StandardCharsets.US_ASCII
            );
            long size = Integer.toUnsignedLong(input.getInt(offset + 4));
            long next = offset + 8L + size + (size & 1);
            if (next > end) throw new IOException("Invalid WebP chunk length");
            if (!kind.equals("EXIF") && !kind.equals("XMP ")) {
                byte[] chunk = java.util.Arrays.copyOfRange(
                    original,
                    offset,
                    (int) next
                );
                if (kind.equals("VP8X")) {
                    if (size != 10) throw new IOException(
                        "Invalid extended WebP header"
                    );
                    chunk[8] &= ~0x0c;
                }
                output.write(chunk);
            }
            offset = (int) next;
        }
        byte[] result = output.toByteArray();
        ByteBuffer.wrap(result)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(4, result.length - 8);
        return result;
    }
}
