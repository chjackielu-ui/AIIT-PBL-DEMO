package cn.axg.platform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import static cn.axg.platform.Seed.M;

/** 本地演示存储边界；业务服务负责同步和失败回滚，仓储负责状态读写。 */
final class JsonStateRepository implements StateRepository {
    private final Path directory;

    JsonStateRepository(String path) throws IOException {
        directory = Path.of(path).toAbsolutePath();
        Files.createDirectories(directory.resolve("files"));
    }

    public Path file(String id, String owner) {
        if (id.equals("profile-avatar")) {
            if (!owner.matches("[A-Za-z0-9_-]{1,60}")) throw new IllegalArgumentException("无效的账号标识");
            return directory.resolve("avatars").resolve(owner);
        }
        return directory.resolve("files").resolve(id);
    }

    public ObjectNode load() throws IOException {
        Path state = directory.resolve("state.json");
        return Files.exists(state) ? (ObjectNode) M.readTree(state.toFile()) : Seed.create();
    }

    public void save(ObjectNode state) throws IOException {
        Path temp = directory.resolve("state.tmp");
        M.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
        try {
            Files.move(temp, directory.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, directory.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
