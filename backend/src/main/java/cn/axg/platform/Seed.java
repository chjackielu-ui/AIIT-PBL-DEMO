package cn.axg.platform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;

final class Seed {
    static final ObjectMapper M = new ObjectMapper();
    static ObjectNode o(Object... kv) {
        ObjectNode n = M.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) n.set(kv[i].toString(), M.valueToTree(kv[i + 1]));
        return n;
    }
    static ArrayNode a(Object... values) { return M.valueToTree(Arrays.asList(values)); }

    static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    /** 相对今天的天数偏移的日期字符串（种子数据用，避免截止日期过期）。 */
    static String d(int offset) { return today().plusDays(offset).toString(); }
    /** 相对今天偏移的日期时间字符串。 */
    static String dt(int offset, String time) { return today().plusDays(offset) + "T" + time; }

    static ObjectNode create() {
        ObjectNode s = o("version", "3",
            "users", a(),
            "courses", a(o("id", "c1", "name", "Web 应用开发实训"), o("id", "c2", "name", "软件工程实践"), o("id", "c3", "name", "数据可视化")),
            "classes", a(
                o("id", "b1", "name", "软件2401", "college", "计算机与软件工程学院", "major", "软件"),
                o("id", "b2", "name", "软件2402", "college", "计算机与软件工程学院", "major", "软件"),
                o("id", "b3", "name", "计科2401", "college", "计算机与软件工程学院", "major", "计科"),
                o("id", "b4", "name", "网络2401", "college", "计算机与软件工程学院", "major", "网络"),
                o("id", "b5", "name", "人工智能2401", "college", "大数据与人工智能学院", "major", "人工智能"),
                o("id", "b6", "name", "大数据2401", "college", "大数据与人工智能学院", "major", "大数据"),
                o("id", "b7", "name", "智科2401", "college", "大数据与人工智能学院", "major", "智科")),
            "teachingClasses", a(
                o("id", "tc1", "name", "Web 实训 A 班", "courseId", "c1", "teacherIds", a("t1"), "academicYear", Migrations.currentAcademicYear(), "semester", Migrations.currentSemester()),
                o("id", "tc2", "name", "软件工程 B 班", "courseId", "c2", "teacherIds", a("t1", "t2"), "academicYear", Migrations.currentAcademicYear(), "semester", Migrations.currentSemester())),
            "enrollments", a(), "tasks", a(), "projects", a(), "notifications", a(), "history", a());
        ArrayNode users = (ArrayNode) s.get("users");
        users.add(o("id", "a1", "name", "系统管理员", "role", "admin", "enabled", true));
        users.add(o("id", "t1", "name", "陈老师", "role", "teacher", "enabled", true));
        users.add(o("id", "t2", "name", "周老师", "role", "teacher", "enabled", true));
        String[] names = {"林同学", "许知远", "沈佳宁", "陈思齐", "王沐阳", "李可欣", "周子墨", "张予安", "刘星辰", "赵一诺"};
        for (int i = 1; i <= 10; i++) {
            users.add(o("id", "s" + i, "name", names[i - 1], "role", "student", "classId", i <= 5 ? "b1" : "b2",
                "number", "32430" + String.format("%05d", 12340 + i), "enabled", true));
            ((ArrayNode) s.get("enrollments")).add(o("studentId", "s" + i, "classId", i <= 5 ? "tc1" : "tc2"));
        }
        ArrayNode phases = a(
            o("id", "m1", "name", "需求与原型", "description", "完成用户调研、业务流程与界面原型。",
                "due", d(18), "materials", "需求文档与原型文件", "criteria", "业务闭环清晰，关键页面完整"),
            o("id", "m2", "name", "开发与交付", "description", "实现核心功能并完成测试。",
                "due", d(53), "materials", "源代码、测试报告与演示截图", "criteria", "应用可运行，权限与数据一致"));
        ((ArrayNode) s.get("tasks")).add(o("id", "ct1", "name", "校园数字服务 · Web 应用实训", "teacherId", "t1",
            "courseId", "c1", "classId", "tc1",
            "description", "围绕校园真实场景，完成一款可运行的 Web 应用。提交需求原型、完整源代码和项目报告。",
            "crossClass", false, "min", 2, "max", 5, "teamDue", d(13), "due", d(15), "status", "已发布", "milestones", phases));
        ((ArrayNode) s.get("tasks")).add(o("id", "ct2", "name", "开放选题 · 软件工程综合实践", "teacherId", "t1",
            "courseId", "c2", "classId", "tc2",
            "description", "以小组为单位实践需求分析、系统设计、开发测试和最终交付。支持跨班协作。",
            "crossClass", true, "min", 2, "max", 5, "teamDue", d(23), "due", d(25), "status", "已发布", "milestones", phases.deepCopy()));
        String[] projectNames = {"校园失物招领平台", "实验室预约系统", "校园二手书交换", "课程学习资源导航"};
        String[] statuses = {"进行中", "待审核", "结项待审核", "已归档"};
        String[][] members = {{"s1", "s2", "s3"}, {"s4", "s5"}, {"s6", "s7"}, {"s8", "s9", "s10"}};
        for (int i = 0; i < 4; i++) {
            ArrayNode cs = a();
            for (int j = 0; j < members[i].length; j++)
                cs.add(o("userId", members[i][j], "role", j == 0 ? "项目负责人" : "开发成员",
                    "description", j == 0 ? "需求分析、系统架构与项目统筹" : "功能开发、测试与文档编写",
                    "percent", members[i].length == 2 ? 50 : (j == 0 ? 40 : 30),
                    "evidence", "需求文档、代码提交与测试报告",
                    "score", i == 3 ? 92 - j * 2 : null,
                    "comment", i == 3 ? "完成情况良好，能够主动推进任务。" : ""));
            String code = "export function findItems(keyword) {\n  return items.filter(item => item.title.includes(keyword));\n}\n";
            ArrayNode files = a(
                o("id", "f" + i + "a", "name", "项目需求说明.md", "category", "文档", "size", 200, "type", "text/markdown",
                    "userId", members[i][0], "time", dt(-8 + i, "10:00"), "seedText", "# " + projectNames[i] + "\n\n项目目标：改善校园服务效率。\n\n包含需求分析、系统设计和验收标准。"),
                o("id", "f" + i + "b", "name", "main.js", "category", "代码", "size", code.length(), "type", "text/javascript",
                    "userId", members[i][0], "time", dt(-6 + i, "14:00"), "seedText", code),
                o("id", "f" + i + "c", "name", "成果界面.svg", "category", "图片", "size", 600, "type", "image/svg+xml",
                    "userId", members[i][0], "time", dt(-5 + i, "14:00"), "public", i == 3,
                    "seedText", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"500\"><rect width=\"1000\" height=\"500\" fill=\"white\"/><rect x=\"20\" y=\"20\" width=\"960\" height=\"60\" fill=\"#eff6ff\"/><text x=\"50\" y=\"60\" font-family=\"sans-serif\" font-size=\"24\" fill=\"#2563eb\">Campus Service / Project Demo</text><rect x=\"40\" y=\"120\" width=\"920\" height=\"320\" fill=\"none\" stroke=\"#e2e8f0\"/><text x=\"80\" y=\"210\" font-family=\"sans-serif\" font-size=\"20\" fill=\"#1f2937\">Search / Manage / Collaborate</text><path d=\"M80 260H880M80 320H880M80 380H600\" stroke=\"#e2e8f0\" stroke-width=\"14\"/></svg>"));
            ObjectNode p = o("id", "p" + (i + 1), "taskId", i < 2 ? "ct1" : "ct2", "name", projectNames[i],
                "description", "面向全校师生的" + projectNames[i] + "，提供清晰的操作流程和便捷的信息服务。",
                "goal", "通过数字化流程提升校园服务效率，完成可运行的课程项目。",
                "tags", "Vue 3 / Java / Spring Boot",
                "leaderId", members[i][0], "memberIds", members[i], "status", statuses[i], "created", d(-10 + i),
                "share", i == 3, "shareId", "demo-p" + (i + 1), "reviewComment", i == 0 ? "选题合理，按计划推进。" : "",
                "items", a(
                    o("id", "w" + i + "a", "title", "梳理业务流程与权限", "ownerId", members[i][0], "status", "已完成", "due", d(4 + i), "source", "个人任务"),
                    o("id", "w" + i + "b", "title", "实现项目核心页面", "ownerId", members[i][1], "status", "进行中", "due", d(13 + i), "source", "教师任务")),
                "files", files,
                "messages", a(o("id", "d" + i, "userId", members[i][0], "text", "需求说明已整理到资料库，请大家确认本周任务。", "code", "", "fileId", "f" + i + "a", "time", dt(-6 + i, "10:00"))),
                "submissions", a(o("id", "sub" + i, "milestoneId", "m1", "description", "已完成需求分析和交互原型，请老师验收。",
                    "fileIds", a("f" + i + "a"), "status", i == 0 ? "退回修改" : i == 1 ? "待验收" : "已通过",
                    "score", i == 0 ? 75 : 90, "comment", i == 0 ? "请补充异常流程与权限说明。" : "需求分析完整。",
                    "time", dt(-4 + i, "11:00"), "reviewerId", "t1", "reviewTime", dt(-3 + i, "09:00"))),
                "contributions", cs, "finalHistory", a(),
                "activity", a(
                    o("text", "项目申请已提交", "time", dt(-8 + i, "10:00"), "userId", members[i][0]),
                    o("text", "上传项目需求说明", "time", dt(-6 + i, "14:00"), "userId", members[i][0])));
            if (i >= 2)
                p.set("final", o("summary", "已完成核心功能、测试和文档，系统支持主要业务流程。",
                    "description", "交付可运行应用、完整源代码和成果界面。",
                    "fileIds", a("f" + i + "b"), "imageIds", a("f" + i + "c"), "link", "",
                    "contributions", cs.deepCopy(), "expertName", "", "expertOrg", "", "expertComment", "",
                    "comment", i == 3 ? "项目完成度高，团队分工清晰。" : "", "approved", i == 3,
                    "projectScore", i == 3 ? 90 : null, "reviewerId", i == 3 ? "t1" : "",
                    "reviewTime", i == 3 ? dt(-2, "15:00") : ""));
            if (i == 1) p.set("submissions", a());
            if (i != 1)
                ((ArrayNode) p.get("submissions")).add(o("id", "sub" + i + "m2", "milestoneId", "m2",
                    "description", "提交核心代码与最终测试报告。", "fileIds", a("f" + i + "b"),
                    "status", i == 0 ? "待验收" : "已通过", "score", i == 0 ? null : 92,
                    "comment", i == 0 ? "" : "核心功能完整，满足交付要求。",
                    "time", dt(-3 + i, "11:00"), "reviewerId", i == 0 ? "" : "t1", "reviewTime", i == 0 ? "" : dt(-2 + i, "09:00")));
            ((ArrayNode) s.get("projects")).add(p);
        }
        ((ArrayNode) s.get("notifications")).add(o("id", "n1", "userId", "s1", "title", "阶段成果退回修改",
            "text", "校园失物招领平台：请补充异常流程与权限说明。", "path", "/student/projects/p1?tab=milestones",
            "read", false, "time", dt(-3, "09:00")));
        ((ArrayNode) s.get("notifications")).add(o("id", "n2", "userId", "t1", "title", "新的项目申请",
            "text", "实验室预约系统等待审核。", "path", "/teacher/projects/p2", "read", false, "time", dt(-4, "09:00")));
        ((ArrayNode) s.get("notifications")).add(o("id", "n3", "userId", "s1", "title", "陈老师在讨论中提及你",
            "text", "@林同学 请及时完善需求文档。", "path", "/student/projects/p1?tab=discussion", "read", false, "time", dt(-3, "10:00")));
        ((ArrayNode) s.get("notifications")).add(o("id", "n4", "userId", "s8", "title", "结项成绩已确认",
            "text", "课程学习资源导航已归档，你的个人成绩为 92。", "path", "/student/projects/p4?tab=final", "read", false, "time", dt(-2, "15:00")));
        ((ArrayNode) s.get("notifications")).add(o("id", "n5", "userId", "s1", "title", "阶段截止提醒",
            "text", "需求与原型将于 " + d(18) + " 截止。", "path", "/student/projects/p1?tab=milestones", "read", false, "time", dt(-1, "09:00")));
        ((ArrayNode) s.get("history")).add(o("id", "h1", "userId", "s1", "name", "社团活动报名工具",
            "year", "2025–2026", "date", d(-130), "role", "前端开发",
            "description", "独立实现活动报名与名单管理。", "contribution", "完成页面开发和浏览器兼容性测试。",
            "summary", "交付静态应用与使用说明。"));
        for (JsonNode t : s.path("tasks")) PlatformService.ensureFinal((ObjectNode) t);
        return s;
    }
}
