package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

import static cn.axg.platform.PlatformService.*;
import static cn.axg.platform.Seed.*;

final class CourseImport {
    private static String className(String value) {
        String name = value.strip().replaceFirst("^软件[-－]", "");
        return Set.of("尖兵班", "精兵班").contains(name) ? "坚冰班" : name;
    }

    static ObjectNode roster(PlatformService service, JsonNode rows, boolean commit) {
        require(rows.isArray() && !rows.isEmpty() && rows.size() <= 1000, "每次导入 1–1000 行");
        ArrayNode preview = a();
        Map<String,String> seen = new HashMap<>();
        Map<String,JsonNode> accounts = new HashMap<>();
        for (JsonNode u : service.list("users")) if (!str(u, "number").isBlank()) accounts.put(str(u, "number"), u);
        int index = 1;
        for (JsonNode raw : rows) {
            String number = str(raw,"number").strip(), name = str(raw,"name").strip();
            String className = className(str(raw,"className"));
            ObjectNode data = o("number", number, "name", name, "className", className);
            String error = "";
            try {
                require(number.matches("[0-9]{10}") && !name.isBlank() && name.length() <= 60, "请填写姓名和 10 位学号");
                List<JsonNode> classes = service.list("teachingClasses").valueStream().filter(c ->
                    str(c,"name").equals(className) && str(c,"academicYear").startsWith("2026")
                      && str(c,"semester").equals("小学期")).toList();
                require(classes.size() == 1, "请先导入唯一匹配的 2026 年小学期教学班");
                String classId = str(classes.get(0),"id");
                JsonNode account = accounts.get(number);
                if (account != null) {
                    service.only(account, "student");
                    require(str(account,"name").equals(name), "同一学号的姓名与已保存账号不一致");
                }
                require(seen.putIfAbsent(number, classId) == null, "同一学号在本批名单中重复");
                data.put("studentId", account == null ? "" : str(account,"id")).put("classId",classId);
            } catch (ResponseStatusException e) { error=e.getReason(); }
            preview.add(o("row", ++index, "data", data, "error", error));
        }
        if (!commit) return o("rows",preview,"count",0);
        for (JsonNode row : preview) require(str(row,"error").isBlank(), "名单包含错误行，请重新预览并修正");
        int added = 0;
        for (JsonNode row : preview) {
            JsonNode d = row.path("data");
            String id = str(d,"studentId"), classId = str(d,"classId");
            if (id.isBlank()) {
                id = id();
                service.list("users").add(o("id",id,"name",str(d,"name"),
                    "number",str(d,"number"),"role","student","enabled",true));
            }
            if (!service.enrolled(id,classId)) {
                service.list("enrollments").add(o("studentId",id,"classId",classId));
                added++;
            }
        }
        return o("rows",preview,"count",added);
    }

    static ObjectNode process(PlatformService service, JsonNode rows, boolean commit) {
        require(rows.isArray() && !rows.isEmpty() && rows.size() <= 1000, "每次导入 1–1000 行");
        ArrayNode preview = a();
        Set<String> ids = new HashSet<>(), groups = new HashSet<>();
        int index = 1;
        for (JsonNode raw : rows) {
            String key = str(raw, "id").strip(), name = className(str(raw,"className"));
            ObjectNode data = o("id", key, "className", name, "teacherId", str(raw,"teacherId").strip(),
                "academicYear", str(raw,"academicYear").strip(), "semester", str(raw,"semester").strip(),
                "status", str(raw,"status").strip(), "plannedStart", str(raw,"plannedStart").strip(),
                "plannedEnd", str(raw,"plannedEnd").strip(), "actualStart", str(raw,"actualStart").strip(),
                "actualEnd", str(raw,"actualEnd").strip(), "gradingRule", raw.path("gradingRule"));
            String error = "";
            try {
                require(key.matches("[A-Za-z0-9_-]{1,60}") && ids.add(key), "课程标识无效或重复");
                require(service.findOrNull("courses", key) == null && service.findOrNull("teachingClasses", key) == null, "课程或教学班标识已存在");
                require(!name.isBlank() && name.length() <= 60, "请填写教学班名");
                var teacher = service.user(str(data,"teacherId"));
                service.only(teacher, "teacher");
                Migrations.validateTerm(data);
                require(str(data,"semester").equals("小学期"), "请填写小学期");
                String year = str(data,"academicYear");
                require(groups.add(year + ":" + name) && service.list("teachingClasses").valueStream().noneMatch(c ->
                    str(c,"academicYear").equals(year) && str(c,"semester").equals("小学期") && str(c,"name").equals(name)),
                    "该学年已有同名小学期教学班");
                require(List.of("未开始","进行中","已结束").contains(str(data,"status")), "课程状态应为未开始、进行中或已结束");
                LocalDateTime start = LocalDateTime.parse(str(data,"plannedStart").replace(' ', 'T'));
                LocalDateTime end = LocalDateTime.parse(str(data,"plannedEnd").replace(' ', 'T'));
                require(end.isAfter(start), "计划结束时间应晚于开始时间");
                data.put("plannedStart",start.toString()).put("plannedEnd",end.toString());
                LocalDateTime actualStart = optionalTime(data, "actualStart"), actualEnd = optionalTime(data, "actualEnd");
                require(actualStart == null || actualEnd == null || actualEnd.isAfter(actualStart), "实际结束时间应晚于实际开始时间");
                require(data.path("gradingRule").isObject(), "请填写评分规则");
                data.set("gradingRule", CourseGrading.normalize(data.path("gradingRule")));
                data.put("courseName", "安信工24级小学期_" + name + "_软件_专业技术综合实践_" + str(teacher,"name"));
            } catch (ResponseStatusException e) {
                error = e.getReason();
            } catch (DateTimeParseException e) {
                error = "计划时间格式应为 yyyy-MM-dd HH:mm";
            }
            preview.add(o("row", ++index, "data", data, "error", error));
        }
        if (commit) {
            for (JsonNode row : preview) require(str(row, "error").isBlank(), "导入包含错误行，请重新预览并修正");
            for (JsonNode row : preview) {
                JsonNode d = row.path("data");
                String key = str(d,"id");
                ObjectNode course = o("id", key, "name", str(d,"courseName"), "status", str(d,"status"),
                    "plannedStart", str(d,"plannedStart"), "plannedEnd", str(d,"plannedEnd"));
                for (String field : List.of("actualStart","actualEnd")) if (!str(d,field).isBlank()) course.set(field,d.path(field));
                service.list("courses").add(course);
                service.list("teachingClasses").add(o("id", key, "name", str(d,"className"), "courseId", key,
                    "teacherIds", a(str(d,"teacherId")), "academicYear", str(d,"academicYear"), "semester", str(d,"semester"),
                    "gradingRule", d.path("gradingRule"), "resources", a()));
            }
        }
        return o("rows", preview, "count", commit ? preview.size() : 0);
    }

    private static LocalDateTime optionalTime(ObjectNode data, String field) {
        String value = str(data,field);
        if (value.isBlank()) return null;
        LocalDateTime time = LocalDateTime.parse(value.replace(' ', 'T'));
        data.put(field, time.toString());
        return time;
    }
}
