package cn.axg.platform;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.IIOException;
import org.springframework.web.multipart.MultipartFile;

import static cn.axg.platform.PlatformService.require;

final class ProfileAvatar {
    private ProfileAvatar() {}

    static void save(Path target, MultipartFile file) throws IOException {
        require(!file.isEmpty() && file.getSize() <= 2 * 1024 * 1024, "请选择 2 MB 以内的头像图片");
        BufferedImage original;
        try (var input = file.getInputStream(); var image = ImageIO.createImageInputStream(input)) {
            require(image != null, "图片无法读取，请重新选择");
            var readers = ImageIO.getImageReaders(image);
            require(readers.hasNext(), "请选择有效的 JPG 或 PNG 图片");
            var reader = readers.next();
            try {
                require(Set.of("jpeg", "png").contains(reader.getFormatName().toLowerCase(Locale.ROOT)), "请选择 JPG 或 PNG 图片");
                reader.setInput(image, true, true);
                require(reader.getWidth(0) <= 2048 && reader.getHeight(0) <= 2048, "图片最长边不能超过 2048 像素");
                original = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IIOException e) {
            require(false, "图片无法读取，请重新选择");
            return;
        }

        double scale = Math.min(1.0, 384.0 / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        Files.createDirectories(target.getParent());
        Path pending = Files.createTempFile(target.getParent(), "avatar-", ".png");
        try {
            if (!ImageIO.write(resized, "png", pending.toFile())) throw new IOException("无法编码头像图片");
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
