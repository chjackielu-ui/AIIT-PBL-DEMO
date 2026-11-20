package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 演示数据的一次性升级迁移，带版本标记（state.version）。
 * 仅在版本落后时执行一次；与业务初始化分离，不常驻服务构造器。
 */
final class Migrations {

    /** 当前数据版本；state.version 落后于它时才执行对应迁移。 */
    static final String VERSION = "3";
    static final String THIRD_TERM = "第三学期";

    static String currentVersion(ObjectNode state) {
        return str(state, "version");
    }

    /** 执行全部待运行迁移；返回是否有变更（决定是否需要落盘）。 */
    static boolean migrate(ObjectNode state) {
        boolean changed = false;
        if (currentVersion(state).isBlank()) {
            // v1：旧演示数据升级 —— 教学班补学年学期、任务补最终验收阶段、
            //     演示行政班名与演示学号升级到新格式（仅匹配旧默认值的记录）。
            for (JsonNode c : list(state, "teachingClasses")) {
                ObjectNode tc = (ObjectNode) c;
                String year = str(tc, "academicYear");
                if (year.isBlank()) tc.put("academicYear", currentAcademicYear());
                if (str(tc, "semester").isBlank()) tc.put("semester", currentSemester());
                try {
                    validateTerm(tc);
                } catch (ResponseStatusException e) {
                    throw new IllegalStateException("教学班 " + str(tc, "id") + " 的学年学期数据无效：" + e.getReason());
                }
            }
            for (JsonNode c : list(state, "classes")) {
                if (str(c, "name").matches("软件工程 230[12]")) {
                    ((ObjectNode) c).put("name", "软件240" + str(c, "name").charAt(str(c, "name").length() - 1));
                    ((ObjectNode) c).put("college", "计算机与软件工程学院");
                    ((ObjectNode) c).put("major", "软件");
                }
            }
            for (JsonNode u : list(state, "users")) {
                if (role(u).equals("student") && str(u, "id").matches("s([1-9]|10)") && str(u, "number").matches("2023[0-9]{4}")) {
                    ((ObjectNode) u).put("number", "32430" + String.format("%05d", 12340 + Integer.parseInt(str(u, "id").substring(1))));
                }
            }
            changed = true;
        }
        if (currentVersion(state).isBlank() || currentVersion(state).equals("1")) {
            for (JsonNode t : list(state, "tasks")) PlatformService.ensureFinal((ObjectNode) t);
            state.put("version", "2");
            changed = true;
        }
        if (currentVersion(state).equals("2")) {
            for (JsonNode tc : list(state, "teachingClasses")) {
                if (str(tc, "semester").equals("小学期")) ((ObjectNode) tc).put("semester", THIRD_TERM);
            }
            state.put("version", VERSION);
            changed = true;
        }
        return changed;
    }

    static String currentAcademicYear() {
        LocalDate d = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        int y = d.getMonthValue() >= 8 ? d.getYear() : d.getYear() - 1;
        return y + "-" + (y + 1);
    }

    static String currentSemester() {
        int m = LocalDate.now(ZoneId.of("Asia/Shanghai")).getMonthValue();
        return m >= 2 && m <= 7 ? "第二学期" : "第一学期";
    }

    /** 学年学期格式与连续性校验（运行时业务与迁移共用）。 */
    static void validateTerm(ObjectNode c) {
        String year = str(c, "academicYear");
        require(year.matches("[0-9]{4}") ||
            (year.matches("[0-9]{4}-[0-9]{4}") && Integer.parseInt(year.substring(5)) == Integer.parseInt(year.substring(0, 4)) + 1),
            "学年格式应为 2026 或 2026-2027");
        String semester = str(c, "semester").strip();
        if (semester.equals("小学期")) semester = THIRD_TERM;
        require(java.util.List.of("第一学期", "第二学期", THIRD_TERM).contains(semester), "学期无效");
        c.put("semester", semester);
    }

    private static void require(boolean yes, String message) {
        if (!yes) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String str(JsonNode n, String k) {
        return n.path(k).asText("");
    }

    private static String role(JsonNode u) {
        return str(u, "role");
    }

    private static ArrayNode list(ObjectNode state, String key) {
        return (ArrayNode) state.get(key);
    }
}
