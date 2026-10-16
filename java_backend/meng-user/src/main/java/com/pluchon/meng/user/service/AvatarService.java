package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.security.AuthorizationService;
import jakarta.annotation.PostConstruct;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {

    @Autowired
    private ProfileModerationService moderation;

    @Value("${meng.user.avatar-root}")
    private String directory;

    @Autowired
    private UserMapper users;

    @Autowired
    private AuthorizationService authorization;

    private Path root;

    @PostConstruct
    void initialize() throws IOException {
        root = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    private Path path(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.webp")) throw new BusinessException(
            CommonErrorCode.NOT_FOUND
        );
        return root.resolve(name);
    }

    public Resource read(String name) {
        UserContext.require();
        Path file = path(name);
        if (!Files.isRegularFile(file)) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return new FileSystemResource(file);
    }

    private void remove(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.webp")) return;
        try {
            Files.deleteIfExists(path(name));
        } catch (IOException error) {
            throw new IllegalStateException("Avatar cleanup failed", error);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        byte[] bytes = file.getBytes();
        boolean allowed =
            bytes.length >= 12 &&
            (((bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) ||
                (bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) ||
                (bytes[0] == 82 &&
                    bytes[1] == 73 &&
                    bytes[2] == 70 &&
                    bytes[3] == 70 &&
                    bytes[8] == 87 &&
                    bytes[9] == 69 &&
                    bytes[10] == 66 &&
                    bytes[11] == 80));
        if (!allowed) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        BufferedImage source;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
            var reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0),
                    h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > 40_000_000) throw new BusinessException(
                    CommonErrorCode.INVALID_ARGUMENT
                );
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.min(w, h) / 512);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                source = reader.read(0, parameters);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalid) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        var user = authorization.requireActive(users.lockById(UserContext.require().userId()));
        var avatar = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = avatar.createGraphics();
        int edge = Math.min(source.getWidth(), source.getHeight()),
            x = (source.getWidth() - edge) / 2,
            y = (source.getHeight() - edge) / 2;
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            g.drawImage(source, 0, 0, 256, 256, x, y, x + edge, y + edge, null);
        } finally {
            g.dispose();
        }
        String name = UUID.randomUUID() + ".webp",
            old = user.getAvatar();
        try {
            if (!ImageIO.write(avatar, "webp", path(name).toFile())) throw new IOException(
                "WebP encoder unavailable"
            );
        } catch (IOException | RuntimeException error) {
            remove(name);
            throw error;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        if (!moderation.retainAvatar(old)) remove(old);
                    } else remove(name);
                }
            }
        );
        user.setAvatar(name);
        users.updateById(user);
        moderation.updated(user, true);
        return "/user/avatars/" + name;
    }
}
