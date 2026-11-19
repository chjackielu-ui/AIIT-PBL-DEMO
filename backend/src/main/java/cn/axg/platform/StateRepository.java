package cn.axg.platform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;

interface StateRepository {
    Path file(String id, String owner);
    ObjectNode load() throws IOException;
    void save(ObjectNode state) throws IOException;
}
