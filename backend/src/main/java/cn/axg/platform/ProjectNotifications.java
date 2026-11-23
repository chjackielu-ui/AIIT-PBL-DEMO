package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.regex.Pattern;

import static cn.axg.platform.PlatformService.require;
import static cn.axg.platform.PlatformService.str;

final class ProjectNotifications {
    private static final Set<String> TYPES = Set.of("mentionMe", "mentionAll", "reply", "fileUpload", "codeUpdate");
    private static final Pattern MENTION_ALL = Pattern.compile("(?<![@\\p{L}\\p{N}_])@全体(?![\\p{L}\\p{N}_])");

    private ProjectNotifications() {}

    static void preference(ObjectNode user, JsonNode body) {
        String type = str(body, "type");
        require(TYPES.contains(type) && body.path("enabled").isBoolean(), "无效的通知设置");
        user.withObjectProperty("notificationPreferences").put(type, body.path("enabled").asBoolean());
    }

    static void mute(ObjectNode user, String projectId, JsonNode body) {
        require(body.path("muted").isBoolean(), "无效的免打扰设置");
        ObjectNode projects = user.withObjectProperty("mutedDiscussions");
        if (body.path("muted").asBoolean()) projects.put(projectId, true);
        else projects.remove(projectId);
    }

    private static boolean accepts(JsonNode user, String type) {
        return user.path("notificationPreferences").path(type).asBoolean(true);
    }

    private static boolean participant(PlatformService service, JsonNode user, JsonNode sender, JsonNode project) {
        return !str(user, "id").equals(str(sender, "id")) && !str(user, "role").equals("admin")
            && user.path("enabled").asBoolean() && service.canProject(user, project);
    }

    static void discussion(PlatformService service, JsonNode sender, JsonNode project, JsonNode message,
                           JsonNode selected, JsonNode reply, boolean code) {
        require(selected.isMissingNode() || selected.isArray() && selected.size() <= 100, "提及成员列表无效");
        String text = str(message, "text"), projectId = str(project, "id");
        boolean all = MENTION_ALL.matcher(text).find();
        for (JsonNode user : service.list("users")) {
            String recipient = str(user, "id");
            if (!participant(service, user, sender, project) || user.path("mutedDiscussions").path(projectId).asBoolean()) continue;
            String body = text.isBlank() ? str(message, "codeName") : text;
            String path = "/" + str(user, "role") + "/discussions/" + projectId + "?message=" + str(message, "id");
            String author = str(sender, "name");
            boolean mentioned = (!selected.isArray() || service.contains(selected, recipient)) && text.contains("@" + str(user, "name"))
                && !str(user, "name").equals("全体");
            if (mentioned && accepts(user, "mentionMe"))
                service.notice(recipient, author + " 在项目讨论中提及你", body, path, "mentionMe");
            else if (reply != null && recipient.equals(str(reply, "userId")) && accepts(user, "reply"))
                service.notice(recipient, author + " 回复了你的讨论", body, path, "reply");
            else if (all && accepts(user, "mentionAll"))
                service.notice(recipient, author + " 在项目讨论中 @全体", body, path, "mentionAll");
            else if (code && accepts(user, "codeUpdate"))
                service.notice(recipient, author + " 分享了代码", body, path, "codeUpdate");
        }
    }

    static void upload(PlatformService service, JsonNode sender, JsonNode project, JsonNode file) {
        boolean code = str(file, "category").equals("代码");
        String type = code ? "codeUpdate" : "fileUpload";
        String title = str(sender, "name") + (code ? " 上传了代码" : " 上传了项目文件");
        String projectId = str(project, "id");
        String classId = str(service.find("tasks", str(project, "taskId")), "classId");
        for (JsonNode user : service.list("users")) {
            if (!participant(service, user, sender, project) || !accepts(user, type)) continue;
            String role = str(user, "role"), fileId = str(file, "id");
            String workspace = role.equals("teacher") ? "/teacher/classes/" + classId : "/student";
            String path = code ? "/" + role + "/discussions/" + projectId + "?code=" + fileId
                : workspace + "/projects/" + projectId + "?tab=files&file=" + fileId;
            service.notice(str(user, "id"), title, str(file, "name"), path, type);
        }
    }

    static void courseUpload(PlatformService service, String recipient, String title, String filename, String classId) {
        JsonNode user = service.findOrNull("users", recipient);
        if (user != null && user.path("enabled").asBoolean() && accepts(user, "fileUpload"))
            service.notice(recipient, title, filename, "/student/classes/" + classId + "/resources", "fileUpload");
    }
}
