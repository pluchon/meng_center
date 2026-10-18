package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

// 图片重新编码后存到仓库外，公开路径只接受数据库生成的文件名
@Component
public class WorkStorage {
    private final Path root;
    public WorkStorage() {
        String configured = System.getenv("MENG_COMPANION_WORK_ROOT");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("Missing MENG_COMPANION_WORK_ROOT");
        root = Path.of(configured).toAbsolutePath().normalize();
    }

    public record Saved(String name, String thumbnail) {}

    public Saved save(byte[] data) {
        if (data.length == 0 || data.length > 10_000_000) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid image");
            var reader = readers.next();
            BufferedImage original;
            try {
                reader.setInput(input);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > 24_000_000) throw new IOException("Invalid dimensions");
                original = reader.read(0);
            } finally { reader.dispose(); }
            Files.createDirectories(root);
            String name = UUID.randomUUID() + ".png";
            Path target = path(name);
            try {
                ImageIO.write(original, "png", target.toFile());
                double scale = Math.min(1, 512.0 / Math.max(original.getWidth(), original.getHeight()));
                var thumbnail = new BufferedImage(Math.max(1, (int) (original.getWidth() * scale)), Math.max(1, (int) (original.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = thumbnail.createGraphics();
                try {
                    graphics.setColor(java.awt.Color.WHITE);
                    graphics.fillRect(0, 0, thumbnail.getWidth(), thumbnail.getHeight());
                    graphics.drawImage(original, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), null);
                } finally { graphics.dispose(); }
                var out = new ByteArrayOutputStream();
                ImageIO.write(thumbnail, "jpg", out);
                return new Saved(name, "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
            } catch (Exception error) { Files.deleteIfExists(target); throw error; }
        } catch (Exception error) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
    }

    public String thumbnail(String name) {
        try {
            var image = ImageIO.read(path(name).toFile());
            double scale = Math.min(1, 512.0 / Math.max(image.getWidth(), image.getHeight()));
            var thumb = new BufferedImage(Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            var g = thumb.createGraphics();
            try { g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, thumb.getWidth(), thumb.getHeight()); g.drawImage(image, 0, 0, thumb.getWidth(), thumb.getHeight(), null); }
            finally { g.dispose(); }
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(thumb, "jpg", bytes);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) { throw new BusinessException(CommonErrorCode.NOT_FOUND); }
    }

    public Path path(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.png")) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        Path path = root.resolve(name).normalize();
        if (!path.getParent().equals(root)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return path;
    }

    public void delete(String name) {
        try { Files.deleteIfExists(path(name)); } catch (IOException ignored) { }
    }
}
