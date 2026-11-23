package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import static cn.axg.platform.PlatformService.require;
import static cn.axg.platform.PlatformService.str;

final class TaskSchedule {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TaskSchedule() {}

    static boolean scheduled(JsonNode task) {
        return !str(task, "startAt").isBlank();
    }

    private static LocalDateTime parse(String value, boolean end) {
        if (value.length() == 10) return LocalDate.parse(value).atTime(end ? LocalTime.MAX : LocalTime.MIN);
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    static void validate(JsonNode task) {
        try {
            require(parse(str(task, "due"), true).isAfter(parse(str(task, "startAt"), false)),
                "结束时间必须晚于开始时间");
        } catch (DateTimeParseException e) {
            require(false, "请填写有效的开始时间和结束时间");
        }
    }

    static void validatePhase(JsonNode task, JsonNode phase) {
        try {
            LocalDateTime due = parse(str(phase, "due"), true);
            require(!due.isBefore(parse(str(task, "startAt"), false))
                && !due.isAfter(parse(str(task, "due"), true)), "阶段截止时间必须在项目起止时间内");
        } catch (DateTimeParseException e) {
            require(false, "请填写有效的阶段截止时间");
        }
    }

    static boolean started(JsonNode task) {
        return !scheduled(task) || !LocalDateTime.now(ZONE).isBefore(parse(str(task, "startAt"), false));
    }

    static boolean beforeDeadline(String due) {
        try {
            return !LocalDateTime.now(ZONE).isAfter(parse(due, true));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    static boolean dueSoon(String due) {
        try {
            LocalDateTime end=parse(due,true), now=LocalDateTime.now(ZONE);
            return !end.isBefore(now) && !end.isAfter(now.plusDays(3));
        } catch (DateTimeParseException e) { return false; }
    }

    static Boolean onTime(String submitted, String due) {
        try {
            return !parse(submitted, false).isAfter(parse(due, true));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
