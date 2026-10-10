package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.content.config.ContentProperties;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000;
    private static final int THUMB_WIDTH = 480;
    private final Path root;

    public record Stored(
        String fileName,
        String thumbName,
        int width,
        int height
    ) {}

    public ImageStorage(ContentProperties p) throws IOException {
        root = Path.of(p.imageRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Stored save(byte[] bytes) {
        String suffix = type(bytes);
        if (
            bytes.length > MAX_BYTES || suffix == null
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        String name = UUID.randomUUID().toString() + suffix,
            thumb = UUID.randomUUID() + ".png";
        try (
            ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes)
            )
        ) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(
                CommonErrorCode.INVALID_ARGUMENT
            );
            var reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input);
                int w = reader.getWidth(0),
                    h = reader.getHeight(0);
                if (
                    w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS
                ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
            int width = Math.min(THUMB_WIDTH, source.getWidth()),
                height = Math.max(
                    1,
                    (int) Math.round(
                        ((double) source.getHeight() * width) /
                            source.getWidth()
                    )
                );
            var resized = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
            );
            var graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                );
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            Files.write(path(name), ImageMetadata.strip(bytes, suffix, source), StandardOpenOption.CREATE_NEW);
            if (
                !ImageIO.write(resized, "png", path(thumb).toFile())
            ) throw new IOException("Thumbnail encoder unavailable");
            return new Stored(
                name,
                thumb,
                source.getWidth(),
                source.getHeight()
            );
        } catch (BusinessException invalid) {
            throw invalid;
        } catch (IOException | RuntimeException invalid) {
            delete(name, thumb);
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
    }

    public Path path(String name) {
        if (
            !name.matches("[a-f0-9-]{36}\\.(jpg|png|gif|webp)")
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return target;
    }

    public void delete(String name, String thumb) {
        try {
            Files.deleteIfExists(path(name));
            Files.deleteIfExists(path(thumb));
        } catch (IOException ignored) {
            throw new IllegalStateException("Image cleanup failed");
        }
    }

    private String type(byte[] b) {
        if (b.length < 12) return null;
        if (
            (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255
        ) return ".jpg";
        if (
            Arrays.equals(Arrays.copyOf(b, 8), new byte[] {
                (byte) 137,
                80,
                78,
                71,
                13,
                10,
                26,
                10,
            })
        ) return ".png";
        String head = new String(
            b,
            0,
            12,
            java.nio.charset.StandardCharsets.ISO_8859_1
        );
        if (
            head.startsWith("GIF87a") || head.startsWith("GIF89a")
        ) return ".gif";
        if (
            head.startsWith("RIFF") && head.substring(8, 12).equals("WEBP")
        ) return ".webp";
        return null;
    }
}
