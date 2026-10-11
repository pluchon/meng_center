package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.content.config.ContentProperties;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

// 图片格式校验、缩略图生成与磁盘存储
@Component
public class ImageStorage {

    // 单张图片的字节数上限
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    // 原图像素总数上限
    private static final long MAX_PIXELS = 40_000_000;

    // 缩略图最大宽度
    private static final int THUMB_WIDTH = 480;

    // 配图存储目录
    private Path root;

    // 保存后的原图、缩略图文件名和尺寸
    public record Stored(
            // 原图文件名
            String fileName,
            // 缩略图文件名
            String thumbName,
            // 原图宽度
            int width,
            // 原图高度
            int height) {}

    // 校验图片、生成缩略图并写入存储目录
    public Stored save(byte[] bytes) {
        String suffix = type(bytes);
        if (bytes.length > MAX_BYTES || suffix == null)
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        String name = UUID.randomUUID().toString() + suffix;
        String thumb = UUID.randomUUID() + ".png";
        try (ImageInputStream input =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
            var reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > MAX_PIXELS)
                    throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
            int width = Math.min(THUMB_WIDTH, source.getWidth());
            int height =
                    Math.max(
                            1,
                            (int)
                                    Math.round(
                                            ((double) source.getHeight() * width)
                                                    / source.getWidth()));
            var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            Files.write(
                    path(name),
                    ImageMetadata.strip(bytes, suffix, source),
                    StandardOpenOption.CREATE_NEW);
            if (!ImageIO.write(resized, "png", path(thumb).toFile()))
                throw new IOException("Thumbnail encoder unavailable");
            return new Stored(name, thumb, source.getWidth(), source.getHeight());
        } catch (BusinessException invalid) {
            throw invalid;
        } catch (IOException | RuntimeException invalid) {
            delete(name, thumb);
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
    }

    // 解析图片路径并拒绝越出存储目录
    public Path path(String name) {
        if (!name.matches("[a-f0-9-]{36}\\.(jpg|png|gif|webp)"))
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        var target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return target;
    }

    // 清除原图和缩略图文件
    public void delete(String name, String thumb) {
        try {
            Files.deleteIfExists(path(name));
            Files.deleteIfExists(path(thumb));
        } catch (IOException ignored) {
            throw new IllegalStateException("Image cleanup failed");
        }
    }

    // 依据文件头识别允许的图片格式
    private String type(byte[] b) {
        if (b.length < 12) return null;
        if ((b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255) return ".jpg";
        if (Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
            return ".png";
        String head = new String(b, 0, 12, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (head.startsWith("GIF87a") || head.startsWith("GIF89a")) return ".gif";
        if (head.startsWith("RIFF") && head.substring(8, 12).equals("WEBP")) return ".webp";
        return null;
    }

    // 当前业务域配置
    @Autowired
    private ContentProperties properties;

    // 初始化配图存储目录
    @PostConstruct
    void initialize() throws IOException {
        root = Path.of(properties.imageRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }
}
