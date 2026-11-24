package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.axg.platform.PlatformService.id;
import static cn.axg.platform.PlatformService.now;
import static cn.axg.platform.PlatformService.str;
import static cn.axg.platform.Seed.*;

final class LibraryActivity {
    private LibraryActivity() {}

    static void record(ObjectNode scope, JsonNode user, String action, ObjectNode item, String... folders) {
        String time = now(), actor = str(user, "id"), name = str(item, "name");
        item.put("modifiedAt", time).put("modifiedBy", actor);
        scope.put("modifiedAt", time).put("modifiedBy", actor);
        Set<String> visited = new HashSet<>();
        for (String folderId : folders) {
            while (!folderId.isBlank() && visited.add(folderId)) {
                String target = folderId;
                JsonNode folder = scope.path("folders").valueStream()
                    .filter(child -> str(child, "id").equals(target)).findFirst().orElse(null);
                if (!(folder instanceof ObjectNode node)) break;
                node.put("modifiedAt", time).put("modifiedBy", actor);
                folderId = str(node, "parentId");
            }
        }
        scope.withArray("activity").insert(0, o("id", id(), "action", action, "itemId", str(item, "id"),
            "name", name, "text", action + "：" + name, "time", time, "userId", actor));
    }

    static void collect(List<ObjectNode> entries, ObjectNode scope, String projectId) {
        Set<String> recordedUploads = new HashSet<>();
        int legacyIndex = 0;
        for (JsonNode event : scope.path("activity")) {
            String action = str(event, "action");
            if (action.isBlank()) {
                String text = str(event, "text"), prefix = "编辑资料：";
                if (text.startsWith(prefix))
                    entries.add(o("id", projectId + ":edit:" + legacyIndex++, "action", "编辑资料",
                        "name", text.substring(prefix.length()), "time", str(event, "time"),
                        "userId", str(event, "userId"), "projectId", projectId));
                continue;
            }
            if (action.equals("上传资料") || action.equals("复制资料")) recordedUploads.add(str(event, "itemId"));
            entries.add(((ObjectNode) event.deepCopy()).put("projectId", projectId));
        }
        for (JsonNode file : scope.path("files")) {
            String fileId = str(file, "id"), time = str(file, "time");
            if (time.isBlank() || recordedUploads.contains(fileId)) continue;
            entries.add(o("id", projectId + ":upload:" + fileId, "action", "上传资料", "itemId", fileId,
                "name", str(file, "name"), "time", time, "userId", str(file, "userId"), "projectId", projectId));
        }
    }

    static ObjectNode page(List<ObjectNode> entries, int offset) {
        entries.sort(Comparator.comparing((ObjectNode event) -> str(event, "time")).reversed()
            .thenComparing(event -> str(event, "id")));
        int start = Math.min(entries.size(), offset);
        int end = start + Math.min(50, entries.size() - start);
        ArrayNode rows = a();
        entries.subList(start, end).forEach(rows::add);
        return o("entries", rows, "hasMore", end < entries.size());
    }
}
