package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.axg.platform.PlatformService.*;
import static cn.axg.platform.Seed.*;

final class ProjectFolders {
    private ProjectFolders() {}

    static void migrate(ObjectNode project) {
        if (project.has("folders")) return;
        ArrayNode folders = project.putArray("folders");
        Map<String, String> categories = new LinkedHashMap<>();
        for (JsonNode node : project.path("files")) {
            String category = str(node, "category");
            if (category.isBlank()) category = "其他";
            String folderId = categories.computeIfAbsent(category, name -> {
                String key = id();
                folders.add(o("id", key, "name", name, "parentId", ""));
                return key;
            });
            ((ObjectNode) node).put("folderId", folderId);
        }
    }

    static void checkTarget(PlatformService service, ObjectNode project, String id) {
        if (!id.isEmpty()) service.nested(project, "folders", id);
    }

    static ObjectNode change(PlatformService service, ObjectNode project, JsonNode actor, ObjectNode body) {
        String op = str(body, "op");
        String folderId = str(body, "folderId");
        if (op.equals("moveFile")) {
            checkTarget(service, project, folderId);
            ObjectNode file = service.nested(project, "files", str(body, "fileId"));
            String from = str(file, "folderId");
            if (!from.equals(folderId)) {
                file.put("folderId", folderId);
                LibraryActivity.record(project, actor, "移动资料", file, from, folderId);
            }
            return o("ok", true);
        }
        if (!project.has("folders")) project.putArray("folders");
        ArrayNode folders = (ArrayNode) project.get("folders");
        boolean creating = op.equals("createFolder");
        ObjectNode folder = creating ? o("id", id()) : service.nested(project, "folders", folderId);
        if (op.equals("deleteFolder")) {
            require(folders.valueStream().noneMatch(f -> str(f, "parentId").equals(folderId))
                && project.path("files").valueStream().noneMatch(f -> str(f, "folderId").equals(folderId)),
                "请先移出文件和子文件夹");
            for (int index = 0; index < folders.size(); index++) {
                if (folders.get(index) == folder) { folders.remove(index); break; }
            }
            LibraryActivity.record(project, actor, "删除文件夹", folder, str(folder, "parentId"));
            return o("ok", true);
        }
        String name = str(body, "name").strip();
        require(!name.isEmpty() && name.length() <= 80 && !name.equals(".") && !name.equals("..")
            && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\'), "请输入有效的文件夹名称（最多 80 字）");
        String parent = creating ? str(body, "parentId") : str(folder, "parentId");
        checkTarget(service, project, parent);
        require(folders.valueStream().noneMatch(f -> f != folder && str(f, "parentId").equals(parent)
            && str(f, "name").equalsIgnoreCase(name)), "同一位置已有同名文件夹");
        if (creating || !str(folder, "name").equals(name)) {
            folder.put("name", name).put("parentId", parent);
            if (creating) {
                folder.put("time", now()).put("userId", str(actor, "id"));
                folders.add(folder);
            }
            LibraryActivity.record(project, actor, creating ? "新建文件夹" : "重命名文件夹", folder, parent);
        }
        return o("ok", true, "folderId", str(folder, "id"));
    }
}
