package cn.axg.platform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;

import static cn.axg.platform.Seed.*;

@Service
public class PlatformService {
    private static final Set<String> TEACHER_PERMISSIONS = Set.of("tasks", "projectReview", "assessment",
        "courseGrades", "courseFiles", "projectFiles", "discussion", "roster", "sharing");
    private final StateRepository repository;
    private final boolean mysql;
    private ObjectNode state;

    public PlatformService(String path) throws IOException {
        this(new JsonStateRepository(path));
    }

    @Autowired
    PlatformService(@Value("${app.data-dir}") String path, @Value("${app.storage}") String storage,
                    @Value("${app.mysql.url}") String url, @Value("${app.mysql.user}") String dbUser,
                    @Value("${app.mysql.password}") String password, @Value("${app.bootstrap.password}") String adminPassword,
                    @Value("${app.bootstrap.demo-accounts}") boolean demoAccounts) throws IOException {
        this(switch (storage) {
            case "mysql" -> new MysqlStateRepository(path, url, dbUser, password);
            case "test-json" -> new JsonStateRepository(path);
            default -> throw new IllegalArgumentException("未知的存储模式");
        });
        if (mysql) bootstrap(adminPassword, demoAccounts);
    }

    private PlatformService(StateRepository repository) throws IOException {
        this.repository = repository;
        mysql = repository instanceof MysqlStateRepository;
        state = repository.load();
        // 一次性迁移（带版本标记）；仅在确有变更时落盘
        boolean changed = Migrations.migrate(state);
        for (JsonNode c : list("teachingClasses")) {
            if (!c.has("gradingRule")) { ((ObjectNode)c).set("gradingRule", o("attendance",20,"homework",20,"project",60)); changed = true; }
            if (!c.has("resources")) { ((ObjectNode)c).set("resources", a()); changed = true; }
        }
        if (changed) save();
    }

    /** 保证任务含唯一且置于末尾的最终验收阶段（种子与迁移共用）。 */
    static void ensureFinal(ObjectNode t) {
        if (exercise(t)) return;
        boolean found = false;
        for (JsonNode m : t.path("milestones")) if (m.path("isFinal").asBoolean()) found = true;
        if (!found) {
            String due = t.path("milestones").isEmpty()
                ? str(t, "due")
                : str(t.path("milestones").get(t.path("milestones").size() - 1), "due");
            ((ArrayNode) t.get("milestones")).add(o(
                "id", str(t, "id").isBlank() ? id() : "final-" + str(t, "id"),
                "name", "最终项目验收",
                "description", "提交项目成果与团队贡献比例，由教师评分确认。",
                "due", due,
                "materials", "项目成果材料",
                "criteria", "完成项目目标，成果完整可用",
                "isFinal", true,
                "requireMaterials", true));
        }
    }

    static boolean exercise(JsonNode task) { return task != null && str(task, "taskKind").equals("exercise"); }

    static List<JsonNode> projectPhases(JsonNode task, JsonNode project) {
        List<JsonNode> phases = new ArrayList<>();
        JsonNode shared = task.path("milestones");
        shared.forEach(m -> { if (!m.path("isFinal").asBoolean()) phases.add(m); });
        if (project != null) project.path("milestones").forEach(phases::add);
        shared.forEach(m -> { if (m.path("isFinal").asBoolean()) phases.add(m); });
        return phases;
    }

    private List<JsonNode> phases(JsonNode project) {
        return projectPhases(find("tasks", str(project, "taskId")), project);
    }

    private int validatePhases(JsonNode phases) {
        require(phases.isArray() && phases.size() <= 100, "阶段列表无效或超过 100 项");
        Map<String, JsonNode> nodes = new HashMap<>();
        int finals = 0;
        for (JsonNode phase : phases) {
            require(phase.isObject(), "阶段格式无效");
            if (phase.path("isFinal").asBoolean()) finals++;
            text(phase, "name", "description", "due", "criteria");
            if (str(phase, "id").isBlank()) ((ObjectNode) phase).put("id", id());
            require(nodes.putIfAbsent(str(phase, "id"), phase) == null, "阶段标识重复");
            validateDeliverableRules(phase);
            Grading.validate(phase);
        }
        for (JsonNode phase : phases) {
            Set<String> visited = new HashSet<>();
            visited.add(str(phase, "id"));
            String parentId = str(phase, "parentId");
            while (!parentId.isBlank()) {
                JsonNode parent = nodes.get(parentId);
                require(parent != null && !parent.path("isFinal").asBoolean(), "上级阶段不存在或属于最终验收");
                require(visited.add(parentId), "阶段分支不能形成循环");
                parentId = str(parent, "parentId");
            }
        }
        return finals;
    }

    // —— 基础工具 ——
    static String str(JsonNode n, String k) { return n.path(k).asText(""); }
    static String id() { return UUID.randomUUID().toString(); }
    static String now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString(); }
    static void require(boolean yes, String message) {
        if (!yes) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    ArrayNode list(String key) { return (ArrayNode) state.get(key); }
    ObjectNode find(String key, String id) {
        ObjectNode n = findOrNull(key, id);
        if (n == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return n;
    }
    ObjectNode findOrNull(String key, String id) {
        for (JsonNode n : list(key)) if (str(n, "id").equals(id)) return (ObjectNode) n;
        return null;
    }
    ObjectNode user(String id) {
        ObjectNode u = find("users", id);
        require(u.path("enabled").asBoolean(), "账号已停用");
        return u;
    }
    String role(JsonNode u) { return str(u, "role"); }
    void only(JsonNode u, String role) {
        if (!role(u).equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前身份无权执行此操作");
    }
    boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) if (n.asText().equals(value)) return true;
        return false;
    }
    boolean enrolled(String student, String tc) {
        for (JsonNode e : list("enrollments"))
            if (str(e, "studentId").equals(student) && str(e, "classId").equals(tc)) return true;
        return false;
    }

    // —— 权限 ——
    /** 悬空引用按“无关联”处理，避免单条坏数据击穿整个状态接口。 */
    boolean related(JsonNode u, JsonNode task) {
        ObjectNode tc = findOrNull("teachingClasses", str(task, "classId"));
        return tc != null && contains(tc.path("teacherIds"), str(u, "id"));
    }
    boolean canTask(JsonNode u, JsonNode t) {
        return role(u).equals("admin")
            || (role(u).equals("teacher") ? related(u, t) : !str(t, "status").equals("草稿") && enrolled(str(u, "id"), str(t, "classId")));
    }
    boolean canProject(JsonNode u, JsonNode p) {
        if (role(u).equals("admin")) return true;
        if (role(u).equals("teacher")) {
            ObjectNode t = findOrNull("tasks", str(p, "taskId"));
            return t != null && related(u, t);
        }
        return contains(p.path("memberIds"), str(u, "id"));
    }
    ObjectNode project(JsonNode u, String pid) {
        ObjectNode p = find("projects", pid);
        if (!canProject(u, p)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能访问有关联的项目");
        return p;
    }
    void teacher(JsonNode u, JsonNode p) {
        only(u, "teacher");
        require(canProject(u, p), "无关联项目权限");
    }
    /** 协作编辑白名单：申请锁定 / 退回修改 / 归档 / 关闭期间只读。 */
    void editable(JsonNode u, JsonNode p) {
        require(!role(u).equals("admin"), "管理员不参与项目业务操作");
        require(!List.of("已归档", "已关闭", "待审核", "待修改").contains(str(p, "status")),
            "当前项目只读，请先完成审核或由教师重新开放");
    }
    void leader(JsonNode u, JsonNode p) {
        only(u, "student");
        require(str(p, "leaderId").equals(str(u, "id")), "仅项目负责人可提交");
    }
    void text(JsonNode n, String... fields) {
        for (String key : fields) require(!str(n, key).isBlank(), key + " 为必填项");
    }
    ArrayNode filtered(ArrayNode src, Predicate<JsonNode> pred) {
        ArrayNode a = a();
        for (JsonNode n : src) if (pred.test(n)) a.add(n.deepCopy());
        return a;
    }

    // —— 持久化 ——
    void save() throws IOException {
        repository.save(state);
    }

    // —— 会话与状态 ——
    public synchronized ArrayNode accounts() {
        if (mysql) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ArrayNode result = a();
        for (JsonNode n : list("users"))
            if (n.path("enabled").asBoolean()) result.add(o("id", str(n, "id"), "name", str(n, "name"), "role", str(n, "role")));
        return result;
    }
    boolean mysql() { return mysql; }

    synchronized boolean sessionValid(String id, Object version) {
        if (!mysql) return true;
        ObjectNode account = findOrNull("users", id);
        return account != null && account.path("enabled").asBoolean() &&
            version instanceof Long n && n == account.path("sessionVersion").asLong();
    }

    public synchronized ObjectNode login(String id, String selectedRole, String password) {
        if (!mysql) return user(id).deepCopy();
        // The local test student also has a short account ID; roster users still sign in by student number.
        ObjectNode account = selectedRole.equals("student") && !id.equals("student")
            ? (ObjectNode) list("users").valueStream().filter(u -> role(u).equals("student") && str(u,"number").equals(id)).findFirst().orElse(null)
            : findOrNull("users", id);
        if (account == null || !account.path("enabled").asBoolean()
            || !role(account).equals(selectedRole) || !Passwords.matches(password, str(account, "passwordHash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号、密码或身份不正确");
        }
        return safeAccount(account);
    }

    private ObjectNode safeAccount(ObjectNode account) {
        ObjectNode safe = account.deepCopy();
        safe.put("hasPassword", !str(account, "passwordHash").isBlank());
        safe.remove("passwordHash");
        return safe;
    }

    private Path avatarPath(String uid) {
        return repository.file("profile-avatar", uid);
    }

    public synchronized long saveAvatar(String uid, MultipartFile file) throws IOException {
        user(uid);
        Path target = avatarPath(uid);
        ProfileAvatar.save(target, file);
        return Files.getLastModifiedTime(target).toMillis();
    }

    public synchronized void removeAvatar(String uid) throws IOException {
        user(uid);
        Files.deleteIfExists(avatarPath(uid));
    }

    public synchronized byte[] avatar(String uid) throws IOException {
        user(uid);
        Path path = avatarPath(uid);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Files.readAllBytes(path);
    }

    private ArrayNode profileTags(JsonNode tags) {
        require(tags.isArray() && tags.size() <= 10, "每组最多填写 10 个标签");
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode tag : tags) {
            String value = tag.asText("").strip();
            require(tag.isTextual() && !value.isBlank() && value.length() <= 30, "标签应为 1–30 个字符");
            names.add(value);
        }
        return M.valueToTree(names);
    }

    private void bootstrap(String password, boolean demo) throws IOException {
        boolean changed = false;
        if (!password.isBlank() && list("users").valueStream().noneMatch(u -> role(u).equals("admin"))) {
            validatePassword(password);
            require(findOrNull("users", "admin") == null, "初始管理员标识冲突");
            list("users").add(newAccount("admin", "总管理员", "admin", "", password));
            changed = true;
        }
        if (demo) {
            for (int i = 1; i <= 3; i++) {
                String teacherId = "demo-teacher-" + i;
                String number = "990000000" + i;
                if (findOrNull("users", teacherId) == null) {
                    list("users").add(newAccount(teacherId, "演示教师" + i, "teacher", "", "学生"));
                    changed = true;
                }
                if (findOrNull("users", number) == null) {
                    list("users").add(newAccount(number, "演示学生" + i, "student", number, "学生"));
                    changed = true;
                }
            }
        }
        if (changed) save();
    }

    private ObjectNode newAccount(String id, String name, String role, String number, String password) {
        ObjectNode account = o("id", id, "name", name, "role", role, "number", number, "enabled", true);
        if (!password.isEmpty()) account.put("passwordHash", Passwords.hash(password));
        return account;
    }

    static void validatePassword(String password) {
        require(password.length() >= 8 && password.length() <= 128, "密码应为 8–128 个字符");
    }

    private void resetPassword(ObjectNode account, String password) {
        validatePassword(password);
        account.put("passwordHash", Passwords.hash(password));
        account.put("sessionVersion", account.path("sessionVersion").asLong() + 1);
    }

    public synchronized void register(ObjectNode body) throws IOException {
        if (!mysql) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String number = str(body, "number").strip(), name = str(body, "name").strip(), password = str(body, "password");
        require(number.matches("[0-9]{10}"), "请输入 10 位学号");
        require(!name.isBlank() && name.length() <= 60, "请输入有效姓名");
        validatePassword(password);
        require(findOrNull("users", number) == null && list("users").valueStream().noneMatch(u -> str(u, "number").equals(number)),
            "该学号已存在，请登录或联系管理员开通");
        ObjectNode before = state.deepCopy();
        try {
            list("users").add(newAccount(id(), name, "student", number, password));
            save();
        } catch (RuntimeException | IOException e) { state = before; throw e; }
    }
    public synchronized void teacherAccount(String uid){only(user(uid),"teacher");}
    public synchronized ObjectNode classInvitation(String token){return ClassEnrollment.invitation(this,token);}
    public synchronized ObjectNode enrollmentPreview(String uid,String cid,JsonNode rows){
        ObjectNode u = user(uid);
        permit(u, "roster");
        return ClassEnrollment.preview(this,u,cid,rows);
    }

    public synchronized ObjectNode discussion(String uid, String pid) {
        ObjectNode p = project(user(uid), pid);
        ArrayNode files = filtered((ArrayNode) p.path("files"), f -> true);
        for (JsonNode f : files) ((ObjectNode) f).remove("seedText");
        return o("messages", p.path("messages").deepCopy(), "files", files, "status", str(p,"status"));
    }

    private ObjectNode calculateReview(JsonNode phase, JsonNode grading, ObjectNode teacher) {
        String name = str(teacher, "name");
        if (!mysql) return Grading.calculate(phase, grading, name);
        JsonNode entries = grading.path("reviewers");
        require(entries.isArray() && entries.size() == 1 && entries.get(0).isObject(), "请填写一位任课教师的评分");
        ObjectNode sanitized = (ObjectNode) grading.deepCopy();
        ((ObjectNode) sanitized.path("reviewers").get(0)).put("name",name).put("weight",100);
        return Grading.calculate(phase, sanitized, name);
    }

    public synchronized ArrayNode courseCatalog(String uid) {
        only(user(uid), "teacher");
        return list("courses").deepCopy();
    }

    public synchronized ObjectNode view(String uid) {
        ObjectNode u = user(uid);
        ObjectNode result = state.deepCopy();
        ObjectNode self = safeAccount(u);
        try {
            Path avatar = avatarPath(uid);
            if (Files.isRegularFile(avatar)) self.put("avatarVersion", Files.getLastModifiedTime(avatar).toMillis());
        } catch (IOException ignored) {
            // An unavailable avatar must not prevent loading the workspace.
        }
        result.set("user", self);
        ((ObjectNode) result.get("user")).remove("sessionVersion");
        if (mysql) {
            ArrayNode users = a();
            for (JsonNode account : list("users")) {
                ObjectNode safe = safeAccount((ObjectNode) account);
                safe.remove("sessionVersion");
                users.add(safe);
            }
            result.set("users", users);
        }
        result.set("tasks", filtered(list("tasks"), t -> canTask(u, t)));
        // 成员保留对父任务的访问权：教学班选课关系变化后仍可查看项目所属任务
        ArrayNode visibleTasks = (ArrayNode) result.get("tasks");
        for (JsonNode p : list("projects")) {
            if (!canProject(u, p)) continue;
            JsonNode t = findOrNull("tasks", str(p, "taskId"));
            if (t == null) continue;
            boolean exists = false;
            for (JsonNode v : visibleTasks) if (str(v, "id").equals(str(t, "id"))) exists = true;
            if (!exists) visibleTasks.add(t.deepCopy());
        }
        result.set("projects", filtered(list("projects"), p -> canProject(u, p)));
        result.set("notifications", filtered(list("notifications"), n -> str(n, "userId").equals(uid)));
        result.set("history", filtered(list("history"), n -> str(n, "userId").equals(uid)));
        if (role(u).equals("teacher")) {
            ArrayNode ownClasses = filtered(list("teachingClasses"), c -> contains(c.path("teacherIds"), uid));
            result.set("teachingClasses", ownClasses);
            Set<String> courses = new HashSet<>();
            for (JsonNode c : ownClasses) courses.add(str(c, "courseId"));
            result.set("courses", filtered(list("courses"), c -> courses.contains(str(c, "id"))));
        }
        if (mysql && role(u).equals("student")) {
            Set<String> visible = new HashSet<>();
            for (JsonNode task : visibleTasks) visible.add(str(task, "classId"));
            for (JsonNode enrollment : list("enrollments")) {
                if (str(enrollment, "studentId").equals(uid)) visible.add(str(enrollment, "classId"));
            }
            ArrayNode classes = filtered(list("teachingClasses"), c -> visible.contains(str(c, "id")));
            Set<String> courses = new HashSet<>();
            for (JsonNode c : classes) courses.add(str(c, "courseId"));
            result.set("teachingClasses", classes);
            result.set("courses", filtered(list("courses"), c -> courses.contains(str(c, "id"))));
        }
        if (mysql && !role(u).equals("admin")) {
            Set<String> visibleClasses = new HashSet<>();
            for (JsonNode c : result.path("teachingClasses")) visibleClasses.add(str(c, "id"));
            result.set("enrollments", filtered(list("enrollments"), e -> visibleClasses.contains(str(e, "classId"))));
            for (JsonNode account : result.path("users")) {
                if (!str(account, "id").equals(uid)) ((ObjectNode) account).remove(List.of("hasPassword", "permissions", "bio", "interests", "techStack"));
            }
        }
        if (!role(u).equals("admin")) {
            Set<String> roster=new HashSet<>();
            if(role(u).equals("teacher"))for(JsonNode e:list("enrollments")){
                ObjectNode tc=findOrNull("teachingClasses",str(e,"classId"));
                if(tc!=null && contains(tc.path("teacherIds"),uid))roster.add(str(e,"studentId"));
            }
            for (JsonNode n : result.path("users"))
                if (!str(n, "id").equals(uid) && !roster.contains(str(n,"id"))) ((ObjectNode) n).remove(List.of("number","contact"));
        }
        for(JsonNode tc:result.path("teachingClasses"))((ObjectNode)tc).remove(List.of("courseScores","inviteToken"));
        result.set("courseGrades",CourseGrading.grades(this,u));
        for (JsonNode p : result.path("projects"))
            for (JsonNode f : p.path("files")) ((ObjectNode) f).remove("seedText");
        if(role(u).equals("student"))result.set("learningAnalytics",LearningAnalytics.forStudent(this,uid));
        return result;
    }

    // —— 通知与动态 ——
    void activity(ObjectNode p, JsonNode u, String text) {
        ((ArrayNode) p.get("activity")).insert(0, o("text", text, "userId", str(u, "id"), "time", now()));
    }
    void notice(String uid, String title, String body, String path) {
        list("notifications").insert(0, o("id", id(), "userId", uid, "title", title, "text", body, "path", path, "read", false, "time", now()));
    }
    void notifyMembers(ObjectNode p, String title, String body) {
        for (JsonNode member : p.path("memberIds")) notice(member.asText(), title, body, "/student/projects/" + str(p, "id"));
    }
    void notifyTeachers(ObjectNode p, String title) {
        ObjectNode t = find("tasks", str(p, "taskId"));
        ObjectNode tc = findOrNull("teachingClasses", str(t, "classId"));
        if (tc == null) return;
        Set<String> ids = new HashSet<>();
        for (JsonNode u : tc.path("teacherIds")) ids.add(u.asText());
        for (String uid : ids) notice(uid, title, str(p, "name"), "/teacher/projects/" + str(p, "id"));
    }

    // —— 业务校验 ——
    ObjectNode nested(ObjectNode p, String key, String nid) {
        for (JsonNode n : p.path(key)) if (str(n, "id").equals(nid)) return (ObjectNode) n;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目记录不存在");
    }
    /** 成员校验：负责人保留、人数范围、去重、学生身份、跨班范围、同任务不重复组队（已归档/关闭项目豁免）。 */
    boolean individual(JsonNode task) {
        return str(task, "projectType").equals("individual") || (str(task, "projectType").isBlank() && task.path("min").asInt() == 1 && task.path("max").asInt() == 1);
    }
    void validateMembers(JsonNode task, ArrayNode members, String lead, String except) {
        if (individual(task)) require(members.size() == 1 && contains(members, lead), "个人项目只能由本人独立提交");
        require(contains(members, lead), "负责人必须保留在团队中");
        require(members.size() >= task.path("min").asInt() && members.size() <= task.path("max").asInt(), "团队人数不符合课程任务要求");
        Set<String> unique = new HashSet<>();
        for (JsonNode m : members) {
            String uid = m.asText();
            require(unique.add(uid), "成员不能重复");
            ObjectNode u = user(uid);
            only(u, "student");
            require(task.path("crossClass").asBoolean() || enrolled(uid, str(task, "classId")), "该任务不允许选择其他教学班成员");
            for (JsonNode p : list("projects")) {
                boolean sameTask = str(p, "taskId").equals(str(task, "id"));
                boolean inOtherTeam = contains(p.path("memberIds"), uid) && !str(p, "id").equals(except);
                boolean finished = List.of("已归档", "已关闭").contains(str(p, "status"));
                if (sameTask && inOtherTeam && !finished) {
                    require(!uid.equals(lead), "你已参加该任务的其他项目");
                    require(false, "成员 " + str(u, "name") + " 已参加该任务的其他项目");
                }
            }
        }
    }
    ArrayNode contributions(ArrayNode members) {
        ArrayNode cs = a();
        int count = members.size();
        for (int i = 0; i < count; i++)
            cs.add(o("userId", members.get(i).asText(),
                "role", i == 0 ? "项目负责人" : "开发成员",
                "description", "",
                "percent", i == 0 ? 100 - (count - 1) * (100 / count) : 100 / count,
                "evidence", "", "comment", ""));
        return cs;
    }
    void submissionFiles(ObjectNode p, JsonNode ids, boolean required) {
        require(ids.isArray(), "附件列表无效");
        if (required) require(!ids.isEmpty(), "请按阶段要求提交附件");
        for (JsonNode fid : ids) nested(p, "files", fid.asText());
    }
    String submissionMode(JsonNode phase) { return phase.path("submissionMode").asText("shared"); }
    JsonNode latestSubmission(ObjectNode p, JsonNode phase, String memberId) {
        JsonNode latest = null;
        for (JsonNode sub : p.path("submissions"))
            if (str(sub, "milestoneId").equals(str(phase, "id"))
                && sub.path("submissionMode").asText("shared").equals(submissionMode(phase))
                && (!submissionMode(phase).equals("individual") || str(sub, "submitterId").equals(memberId))) latest = sub;
        return latest;
    }
    void validateDeliverableRules(JsonNode phase) {
        require(List.of("shared", "individual").contains(submissionMode(phase)), "提交方式无效");
        require(!phase.path("isFinal").asBoolean() || submissionMode(phase).equals("shared"), "项目验收采用团队共享提交");
        if (!phase.has("deliverables")) return;
        JsonNode rules = phase.path("deliverables");
        require(rules.isArray() && rules.size() <= 20, "提交清单最多 20 项");
        Set<String> ids = new HashSet<>();
        for (JsonNode r : rules) {
            text(r, "id", "title");
            require(ids.add(str(r, "id")), "提交清单标识重复");
            require(r.path("extensions").isArray() && !r.path("extensions").isEmpty(), "请设置允许的文件扩展名");
            for (JsonNode ext : r.path("extensions")) require(ext.isTextual() && ext.asText().matches("\\.[a-z0-9]{1,12}"), "扩展名需为 .pptx、.zip 等小写格式");
            require(r.path("minFiles").isIntegralNumber() && r.path("maxFiles").isIntegralNumber()
                && r.path("minFiles").asInt() >= 1 && r.path("maxFiles").asInt() >= r.path("minFiles").asInt()
                && r.path("maxFiles").asInt() <= 20 && r.path("required").isBoolean(), "清单文件数量需为 1–20，且上限不能小于下限");
        }
    }
    void checkDeliverables(ObjectNode p, JsonNode phase, JsonNode submission) {
        JsonNode rules = phase.path("deliverables");
        if (rules.isMissingNode() || rules.isEmpty()) return;
        JsonNode selected = submission.path("deliverables");
        require(selected.isObject(), "请逐项选择提交清单中的文件");
        Set<String> validIds = new HashSet<>();
        for (JsonNode r : rules) {
            String rid = str(r, "id"), title = str(r, "title"); validIds.add(rid);
            JsonNode files = selected.path(rid);
            int count = files.isMissingNode() ? 0 : files.size();
            require(files.isMissingNode() || files.isArray(), title + "：文件列表无效");
            require((!r.path("required").asBoolean() && count == 0) || count >= r.path("minFiles").asInt(), title + "：文件数量不足");
            require(count <= r.path("maxFiles").asInt(), title + "：文件数量超出上限");
            Set<String> unique = new HashSet<>();
            for (JsonNode fid : files) {
                require(unique.add(fid.asText()), title + "：不能重复选择同一文件");
                require(contains(submission.path("fileIds"), fid.asText()) || contains(submission.path("imageIds"), fid.asText()), title + "：文件未包含在本次提交中");
                String filename = str(nested(p, "files", fid.asText()), "name").toLowerCase(Locale.ROOT);
                boolean matches = false;
                for (JsonNode ext : r.path("extensions")) if (filename.endsWith(ext.asText())) matches = true;
                require(matches, title + "：文件格式不符合要求");
            }
        }
        selected.fieldNames().forEachRemaining(rid -> require(validIds.contains(rid), "提交清单已变更，请刷新后重试"));
    }
    void checkFinal(ObjectNode p, ObjectNode f, boolean grading) {
        text(f, "summary", "description");
        boolean scheduled = TaskSchedule.scheduled(find("tasks", str(p, "taskId")));
        boolean required = true;
        JsonNode finalPhase = null;
        for (JsonNode m : phases(p))
            if (m.path("isFinal").asBoolean()) { required = m.path("requireMaterials").asBoolean(true); finalPhase = m; }
        submissionFiles(p, f.path("fileIds"), scheduled ? !grading : required);
        submissionFiles(p, f.path("imageIds"), false);
        // 最终附件必须包含代码 / 源文件压缩包（截图等展示材料走成果截图，不能充当源文件）
        if (finalPhase != null && !grading) checkDeliverables(p, finalPhase, f);
        if (!scheduled && required && (finalPhase == null || finalPhase.path("deliverables").isEmpty())) {
            boolean hasSource = false;
            for (JsonNode fid : f.path("fileIds")) {
                ObjectNode file = nested(p, "files", fid.asText());
                if (List.of("代码", "压缩包").contains(str(file, "category"))) hasSource = true;
            }
            require(hasSource, "最终附件必须包含代码或压缩包文件");
        }
        for (JsonNode fid : f.path("imageIds"))
            require(str(nested(p, "files", fid.asText()), "type").startsWith("image/"), "成果截图必须是图片文件");
        String link = str(f, "link");
        require(link.isBlank() || link.matches("https?://[^\\s]+"), "外部链接必须使用 http 或 https");
        JsonNode cs = f.path("contributions");
        require(cs.isArray() && cs.size() == p.path("memberIds").size(), "每位成员都需要贡献记录");
        Set<String> ids = new HashSet<>();
        double sum = 0;
        for (JsonNode c : cs) {
            require(ids.add(str(c, "userId")) && contains(p.path("memberIds"), str(c, "userId")), "贡献成员名单有误");
            text(c, "role", "description", "evidence");
            double percent = c.path("percent").asDouble(-1);
            require(percent >= 0 && percent <= 100, "贡献比例需在 0–100 之间");
            sum += percent;
            if (grading) {
                require(c.path("score").isNumber() && c.path("score").asDouble() >= 0 && c.path("score").asDouble() <= 100, "请为每位成员填写 0–100 的个人成绩");
                text(c, "comment");
            }
        }
        require(Math.abs(sum - 100) < 0.001, "贡献比例总和必须为 100%");
        if (grading) {
            require(f.path("projectScore").isNumber() && f.path("projectScore").asDouble() >= 0 && f.path("projectScore").asDouble() <= 100, "请填写 0–100 的项目总评");
            text(f, "comment");
            if (!str(f, "expertName").isBlank()) {
                text(f, "expertOrg", "expertComment");
                require(f.path("expertScore").isNumber() && f.path("expertScore").asDouble() >= 0 && f.path("expertScore").asDouble() <= 100, "专家评分应为 0–100");
            }
        }
    }

    // —— 业务动作（事务式：失败回滚内存状态） ——
    public synchronized ObjectNode action(String uid, ObjectNode b) throws IOException {
        ObjectNode account = user(uid);
        String op = str(b, "op");
        if (op.equals("previewCourses") || op.equals("previewRoster")) {
            only(account, "admin");
            return op.equals("previewCourses") ? CourseImport.process(this, b.path("rows"), false)
                : CourseImport.roster(this, b.path("rows"), false);
        }
        if (op.equals("importPreview")) return mutate(account, b);
        ObjectNode before = state.deepCopy();
        try {
            ObjectNode result = mutate(account, b);
            save();
            return result;
        } catch (RuntimeException | IOException e) {
            state = before;
            throw e;
        }
    }

    void permit(JsonNode account, String permission) {
        if (mysql && role(account).equals("teacher") && account.path("permissions").isArray()
            && !contains(account.path("permissions"), permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有该操作的功能权限");
        }
    }

    ObjectNode mutate(ObjectNode u, ObjectNode b) throws IOException {
        String op = str(b, "op"), uid = str(u, "id"), pid = str(b, "projectId");
        String permission = switch (op) {
            case "saveTask", "saveProjectPhases", "addItem" -> "tasks";
            case "reviewProject", "batchReviewProjects", "reopen" -> "projectReview";
            case "reviewPhase", "reviewFinal" -> "assessment";
            case "saveCourseGrades", "saveCourseDetails" -> "courseGrades";
            case "editFile" -> "projectFiles";
            case "message" -> "discussion";
            case "importClassStudents", "addClassStudents", "addClassStudent", "removeClassStudent",
                "resetClassStudentPassword", "createClassInvite" -> "roster";
            case "share" -> "sharing";
            default -> "";
        };
        if (!permission.isEmpty()) permit(u, permission);
        ObjectNode result = o("ok", true);
        switch (op) {
        case "importCourses" -> {
            only(u, "admin");
            result = CourseImport.process(this, b.path("rows"), true);
        }
        case "importRoster" -> {
            only(u, "admin");
            result = CourseImport.roster(this, b.path("rows"), true);
        }
        case "setTeacherPermissions" -> {
            only(u, "admin");
            ObjectNode teacher = find("users", str(b,"id"));
            only(teacher,"teacher");
            JsonNode selected = b.path("permissions");
            require(selected.isArray(), "请提供教师权限列表");
            Set<String> permissions = new LinkedHashSet<>();
            for (JsonNode item : selected) {
                require(TEACHER_PERMISSIONS.contains(item.asText()), "无效的教师权限");
                permissions.add(item.asText());
            }
            teacher.set("permissions", M.valueToTree(permissions));
        }
        case "updateProfile" -> {
            require(b.path("bio").isTextual(), "请输入有效的个性签名");
            String bio = str(b, "bio").strip();
            require(bio.length() <= 160, "个性签名最多 160 个字符");
            u.put("bio", bio);
            u.set("interests", profileTags(b.path("interests")));
            u.set("techStack", profileTags(b.path("techStack")));
        }
        case "changePassword" -> {
            require(mysql, "当前存储模式不支持修改密码");
            require(Passwords.matches(str(b, "currentPassword"), str(u, "passwordHash")), "当前密码不正确");
            String next = str(b, "newPassword");
            validatePassword(next);
            require(!next.equals(str(b, "currentPassword")), "新密码不能与当前密码相同");
            long version = u.path("sessionVersion").asLong() + 1;
            u.put("passwordHash", Passwords.hash(next)).put("sessionVersion", version);
            result.put("sessionVersion", version);
        }
        case "setAccountPassword" -> {
            only(u, "admin");
            ObjectNode target = find("users", str(b,"id"));
            require(!role(target).equals("admin"), "管理员账号凭据由本地配置管理");
            String password = str(b,"password");
            validatePassword(password);
            target.put("passwordHash", Passwords.hash(password));
            target.put("sessionVersion", target.path("sessionVersion").asLong() + 1);
        }
        case "saveCourseDetails" -> {
            ObjectNode tc = find("teachingClasses", str(b, "classId"));
            require(role(u).equals("admin") || (role(u).equals("teacher") && contains(tc.path("teacherIds"), uid)), "无课程编辑权限");
            ObjectNode rule=CourseGrading.normalize(b.path("gradingRule"));
            tc.set("gradingRule",rule);tc.put("courseDescription",str(b,"courseDescription"));
            Set<String> manual=new HashSet<>();for(JsonNode d:rule.path("dimensions"))if(str(d,"source").equals("manual"))manual.add(str(d,"id"));
            for(JsonNode scores:tc.path("courseScores"))if(scores.isObject()){
                List<String> remove=new ArrayList<>();scores.fieldNames().forEachRemaining(key->{if(!manual.contains(key))remove.add(key);});((ObjectNode)scores).remove(remove);
            }
        }
        case "saveCourseGrades" -> CourseGrading.save(this,u,b);
        case "createClassInvite" -> {
            ObjectNode tc=ClassEnrollment.managedClass(this,u,str(b,"classId"));
            if(str(tc,"inviteToken").isBlank())tc.put("inviteToken",id());
            result.put("token",str(tc,"inviteToken"));
        }
        case "joinClass" -> {
            only(u,"student");ObjectNode tc=ClassEnrollment.invitedClass(this,str(b,"token"));
            if (mysql) require(enrolled(uid,str(tc,"id")), "请先由管理员导入该教学班的选课名单");
            if(!enrolled(uid,str(tc,"id")))list("enrollments").add(o("studentId",uid,"classId",str(tc,"id")));
            result.put("classId",str(tc,"id"));
        }
        case "importClassStudents" -> result=ClassEnrollment.apply(this,u,str(b,"classId"),b.path("rows"));
        case "editFile" -> {
            ObjectNode p = project(u, pid); editable(u, p);
            ObjectNode f = nested(p, "files", str(b, "fileId"));
            require(role(u).equals("teacher") || str(f, "userId").equals(uid), "只能编辑自己上传的资料");
            text(b, "name");
            require(List.of("文档", "图片", "代码", "压缩包", "其他").contains(str(b, "category")), "文件类别无效");
            f.put("name", str(b, "name")); f.put("category", str(b, "category")); activity(p, u, "编辑资料：" + str(f, "name"));
        }
        case "createTeachingClass" -> {
            only(u, "teacher");
            if (mysql) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "教学班由管理员从教务表格导入");
            text(b, "name", "courseId", "academicYear", "semester");
            String name = str(b, "name").trim();
            require(name.length() <= 100, "班级名称不能超过 100 字");
            find("courses", str(b, "courseId"));
            ObjectNode c = o("id", id(), "name", name, "courseId", str(b, "courseId"), "teacherIds", a(uid),
                "academicYear", str(b, "academicYear"), "semester", str(b, "semester"));
            Migrations.validateTerm(c);
            for (JsonNode old : list("teachingClasses"))
                require(!(str(old, "name").equals(name) && str(old, "courseId").equals(str(c, "courseId"))
                        && str(old, "academicYear").equals(str(c, "academicYear")) && str(old, "semester").equals(str(c, "semester"))),
                    "该学年学期已存在同名教学班");
            require(b.path("studentIds").isArray(), "选课学生名单无效");
            Set<String> students = new HashSet<>();
            for (JsonNode sid : b.path("studentIds")) {
                only(user(sid.asText()), "student");
                require(students.add(sid.asText()), "选课学生重复");
            }
            c.set("gradingRule", o("attendance",20,"homework",20,"project",60));
            c.set("resources", a());
            list("teachingClasses").add(c);
            for (String sid : students) list("enrollments").add(o("studentId", sid, "classId", str(c, "id")));
            result.put("id", str(c, "id"));
        }
        case "addClassStudents" -> {
            only(u, "teacher");
            ObjectNode tc = find("teachingClasses", str(b, "classId"));
            require(contains(tc.path("teacherIds"), uid), "只能管理自己的教学班学生");
            require(b.path("studentIds").isArray() && !b.path("studentIds").isEmpty(), "请选择学生");
            for (JsonNode selected : b.path("studentIds")) {
                String studentId = selected.asText();
                only(user(studentId), "student");
                if (!enrolled(studentId, str(tc, "id"))) list("enrollments").add(o("studentId", studentId, "classId", str(tc, "id")));
            }
        }
        case "addClassStudent" -> {
            String cid = str(b, "classId");
            ClassEnrollment.managedClass(this, u, cid);
            String number = str(b, "number").strip(), name = str(b, "name").strip();
            require(number.matches("[0-9]{10}"), "请输入 10 位学号");
            require(!name.isBlank() && name.length() <= 60, "请输入有效姓名");
            ObjectNode student = (ObjectNode) list("users").valueStream()
                .filter(account -> number.equals(str(account, "number"))).findFirst().orElse(null);
            if (student == null) {
                String adminClassId = str(b, "administrativeClassId"), contact = str(b, "contact").strip();
                require(adminClassId.isBlank() || findOrNull("classes", adminClassId) != null, "行政班不存在");
                require(contact.length() <= 100, "联系方式最多 100 个字符");
                String password = str(b, "password");
                if (!password.isEmpty()) validatePassword(password);
                student = newAccount(id(), name, "student", number, password);
                if (!adminClassId.isBlank()) student.put("classId", adminClassId);
                if (!contact.isBlank()) student.put("contact", contact);
                list("users").add(student);
            } else {
                only(student, "student");
                require(student.path("enabled").asBoolean() && name.equals(str(student, "name")), "姓名与学号不匹配或账号已停用");
            }
            String studentId = str(student, "id");
            require(!enrolled(studentId, cid), "学生已在本班");
            list("enrollments").add(o("studentId", studentId, "classId", cid));
        }
        case "removeClassStudent", "resetClassStudentPassword" -> {
            String cid = str(b, "classId"), sid = str(b, "studentId");
            ClassEnrollment.managedClass(this, u, cid);
            require(enrolled(sid, cid), "只能操作本班学生");
            ObjectNode student = find("users", sid);
            only(student, "student");
            if (op.equals("resetClassStudentPassword")) {
                require(mysql, "当前存储模式不支持修改密码");
                resetPassword(student, str(b, "password"));
            } else {
                ArrayNode enrollments = list("enrollments");
                for (int i = enrollments.size() - 1; i >= 0; i--) {
                    JsonNode e = enrollments.get(i);
                    if (str(e, "classId").equals(cid) && str(e, "studentId").equals(sid)) enrollments.remove(i);
                }
            }
        }
        case "saveTask" -> {
            only(u, "teacher");
            ObjectNode t = ((ObjectNode) b.path("task")).deepCopy();
            text(t, "name", "classId", "courseId");
            if (!str(t, "status").equals("草稿")) text(t, "description");
            ObjectNode tc = find("teachingClasses", str(t, "classId"));
            require(contains(tc.path("teacherIds"), uid), "只能向自己的教学班发布任务");
            require(str(tc, "courseId").equals(str(t, "courseId")), "课程与教学班不匹配");
            String kind=t.path("taskKind").asText("training");
            require(List.of("training","exercise").contains(kind),"课程任务形式无效");
            ObjectNode previous = str(t, "id").isBlank() ? null : find("tasks", str(t, "id"));
            boolean scheduled = TaskSchedule.scheduled(t);
            require(!mysql || previous != null || scheduled, "发布前请设置开始时间和结束时间");
            require(previous == null || !TaskSchedule.scheduled(previous) || scheduled, "不能移除已设置的开始时间");
            if (scheduled) {
                TaskSchedule.validate(t);
                t.put("teamDue", str(t, "due"));
                boolean existingPersonalProject = previous != null && !exercise(previous) && individual(previous)
                    && list("projects").valueStream().anyMatch(p -> str(p, "taskId").equals(str(t, "id")));
                if (!exercise(t) && !existingPersonalProject) {
                    t.put("projectType", "group");
                    require(t.path("min").asInt() >= 2, "小组项目至少需要两名成员");
                }
            }
            if (exercise(t)) t.put("projectType", "individual");
            if (!t.hasNonNull("projectType")) t.put("projectType", individual(t) ? "individual" : "group");
            require(List.of("individual", "group").contains(str(t, "projectType")), "请选择个人项目或小组项目");
            if (individual(t)) { t.put("min", 1); t.put("max", 1); t.put("crossClass", false); }
            require(t.path("min").asInt() >= 1 && t.path("max").asInt() >= t.path("min").asInt() && t.path("max").asInt() <= 20,
                "组队人数需为 1–20 且上限不小于下限");
            require(t.path("milestones").isArray(), "任务列表格式无效");
            ensureFinal(t);
            int finals = validatePhases(t.path("milestones"));
            if (exercise(t)) {
                require(t.path("milestones").size() == 1 && finals == 0
                    && t.path("milestones").get(0).path("requiresSubmission").asBoolean(true), "班级任务须保留一项独立作答");
                ObjectNode phase = (ObjectNode) t.path("milestones").get(0);
                if (scheduled) phase.put("due", str(t, "due")).put("requireMaterials", true);
                else t.put("due", str(phase, "due")).put("teamDue", str(phase, "due"));
            } else {
                text(t, "teamDue", "due");
                require(str(t, "teamDue").compareTo(str(t, "due")) <= 0, "组队截止不能晚于申请截止");
                ObjectNode finalPhase = (ObjectNode) t.path("milestones").get(t.path("milestones").size() - 1);
                require(finals == 1 && finalPhase.path("isFinal").asBoolean(), "课程项目必须有且仅有一项最终成果验收，并置于最后");
                finalPhase.put("requiresSubmission", true).put("submissionMode", "shared");
                if (scheduled) {
                    finalPhase.put("due", str(t, "due")).put("requireMaterials", true);
                    for (JsonNode phase : t.path("milestones")) if (!phase.path("isFinal").asBoolean()) TaskSchedule.validatePhase(t, phase);
                }
            }
            require(List.of("已发布", "草稿", "已关闭").contains(str(t, "status")), "任务状态无效");
            boolean fresh = str(t, "id").isBlank();
            if (fresh) {
                t.put("id", id());
                t.put("teacherId", uid);
                t.put("created", now());
                list("tasks").add(t);
            } else {
                ObjectNode old = previous;
                require(related(u, old) && !str(old, "status").equals("已关闭"), "已关闭任务不可编辑或无任课权限");
                require(str(old, "classId").equals(str(t, "classId")) && str(old, "courseId").equals(str(t, "courseId")),
                    "任务必须保留在原教学班级及关联课程中，请在目标班级新建任务");
                if (!exercise(old) && !exercise(t)) {
                    JsonNode originalFinal = old.path("milestones").valueStream().filter(m -> m.path("isFinal").asBoolean()).findFirst().orElse(null);
                    JsonNode finalPhase = t.path("milestones").get(t.path("milestones").size() - 1);
                    require(originalFinal == null || str(originalFinal, "id").equals(str(finalPhase, "id")), "最终成果验收只能修改，不能移除或替换");
                }
                for (JsonNode p : list("projects")) {
                    if (!str(p, "taskId").equals(str(t, "id"))) continue;
                    require(individual(old) == individual(t), "已有项目时不能修改项目类型，请新建实训任务");
                    require(old.path("taskKind").asText("training").equals(kind),"已有项目时不能变更课程任务形式");
                    require(p.path("milestones").valueStream().noneMatch(local -> t.path("milestones").valueStream()
                        .anyMatch(shared -> str(shared, "id").equals(str(local, "id")))), "阶段标识与项目子任务冲突");
                    if (exercise(t)) require(str(old.path("milestones").get(0), "id").equals(str(t.path("milestones").get(0), "id")), "已有作答时不能替换作答阶段");
                    if (scheduled) for (JsonNode phase : p.path("milestones")) TaskSchedule.validatePhase(t, phase);
                }
                t.put("created", old.path("created").asText(""));
                t.put("teacherId", str(old, "teacherId"));
                old.removeAll();
                old.setAll(t);
            }
            if (str(t, "status").equals("已发布")) {
                for (JsonNode e : list("enrollments"))
                    if (str(e, "classId").equals(str(t, "classId")))
                        notice(str(e, "studentId"), fresh ? "新课程任务已发布" : "任务及阶段要求已更新",
                            str(t, "name"), "/student/tasks/" + str(t, "id"));
                for (JsonNode p : list("projects"))
                    if (str(p, "taskId").equals(str(t, "id"))) {
                        notifyMembers((ObjectNode) p, "任务及阶段要求已更新", str(t, "name"));
                        activity((ObjectNode) p, u, "教师调整课程任务与阶段要求");
                    }
            }
            result.put("id", str(t, "id"));
        }
        case "startClassTask", "createProject" -> {
            only(u, "student");
            ObjectNode t = find("tasks", str(b, "taskId"));
            boolean classTask = exercise(t);
            require(!op.equals("startClassTask") || classTask, "只能直接开始班级任务");
            if (classTask) {
                for (JsonNode p : list("projects")) {
                    if (str(p, "taskId").equals(str(t, "id")) && contains(p.path("memberIds"), uid)) return result.put("id", str(p, "id"));
                }
            }
            require(canTask(u, t) && str(t, "status").equals("已发布"), "任务未发布、已关闭或不在可参与范围内");
            require(TaskSchedule.started(t), "任务尚未开始");
            String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            String due = classTask ? str(t.path("milestones").path(0), "due") : str(t, "due");
            require(TaskSchedule.beforeDeadline(due) && (classTask || TaskSchedule.scheduled(t) || TaskSchedule.beforeDeadline(str(t, "teamDue"))),
                classTask ? "班级任务已截止" : "课程项目已截止");
            ObjectNode request = classTask ? o("name", str(t, "name"), "description", str(t, "description"),
                "memberIds", a(uid)) : b;
            text(request, "name", "description");
            require(request.path("memberIds").isArray(), "成员名单无效");
            ArrayNode members = (ArrayNode) request.get("memberIds");
            validateMembers(t, members, uid, "");
            String projectId = id();
            ObjectNode p = o("id", projectId, "taskId", str(t, "id"), "name", str(request, "name"), "description", str(request, "description"),
                "tags", str(request, "tags"), "leaderId", uid, "memberIds", members.deepCopy(),
                "projectType", individual(t) ? "individual" : "group", "status", classTask ? "进行中" : "待审核", "created", today, "share", false, "shareId", id(), "reviewComment", "",
                "items", a(), "files", a(), "messages", a(), "submissions", a(), "contributions", contributions(members),
                "finalHistory", a(), "activity", a());
            list("projects").add(p);
            activity(p, u, classTask ? "开始班级任务" : "提交项目申请，成员名单已锁定");
            if (!classTask) notifyTeachers(p, "新的项目申请");
            result.put("id", projectId);
        }
        case "editProject" -> {
            ObjectNode p = project(u, pid);
            leader(u, p);
            require(str(p, "status").equals("待修改"), "仅可修改教师退回的项目申请");
            text(b, "name", "description");
            for (String k : List.of("name", "description", "tags")) p.put(k, str(b, k));
            p.put("status", "待审核");
            notifyTeachers(p, "项目申请重新提交");
            activity(p, u, "更新项目申请信息");
        }
        case "batchReviewProjects" -> {
            only(u, "teacher");
            require(b.path("projectIds").isArray() && !b.path("projectIds").isEmpty(), "请选择待审核项目");
            Set<String> ids = new LinkedHashSet<>();
            for (JsonNode value : b.path("projectIds")) ids.add(value.asText());
            for (String selectedId : ids)
                mutate(u, o("op", "reviewProject", "projectId", selectedId, "pass", true, "comment", "批量审核通过"));
            result.put("count", ids.size());
        }
        case "reviewProject" -> {
            ObjectNode p = project(u, pid);
            teacher(u, p);
            require(str(p, "status").equals("待审核"), "项目当前不在申请审核中");
            text(b, "comment");
            p.put("status", b.path("pass").asBoolean() ? "进行中" : "待修改");
            p.put("reviewComment", str(b, "comment"));
            p.put("reviewerId", uid);
            p.put("reviewTime", now());
            activity(p, u, "项目申请" + (b.path("pass").asBoolean() ? "审核通过" : "退回修改") + "：" + str(b, "comment"));
            notifyMembers(p, "项目申请审核结果", str(b, "comment"));
        }
        case "saveProjectPhases" -> {
            ObjectNode p = project(u, pid);
            teacher(u, p);
            require(List.of("进行中", "结项待修改").contains(str(p, "status")), "只能给已通过申请且可提交成果的项目安排子任务");
            ObjectNode t = find("tasks", str(p, "taskId"));
            require(!exercise(t), "班级任务没有项目子任务");
            int revision = p.path("milestoneRevision").asInt();
            require(revision == b.path("revision").asInt(0), "子任务已更新，请刷新后重试");
            JsonNode local = b.path("milestones").deepCopy();
            require(validatePhases(local) == 0, "最终成果验收由课程项目统一设置");
            for (JsonNode phase : local) {
                require(t.path("milestones").valueStream().noneMatch(m -> str(m, "id").equals(str(phase, "id"))), "阶段标识与课程项目冲突");
                if (TaskSchedule.scheduled(t)) TaskSchedule.validatePhase(t, phase);
            }
            for (JsonNode phase : p.path("milestones")) {
                String phaseId = str(phase, "id");
                if (p.path("submissions").valueStream().anyMatch(sub -> str(sub, "milestoneId").equals(phaseId))) {
                    JsonNode updated = local.valueStream().filter(m -> str(m, "id").equals(phaseId)).findFirst().orElse(null);
                    require(updated != null && submissionMode(updated).equals(submissionMode(phase))
                        && updated.path("requiresSubmission").asBoolean(true), "已有提交的子任务不能删除或更改提交方式");
                }
            }
            p.set("milestones", local);
            p.put("milestoneRevision", revision + 1);
            activity(p, u, "更新项目子任务与阶段要求");
            notifyMembers(p, "项目子任务已更新", str(p, "name"));
        }
        case "addItem" -> {
            only(u, "teacher");
            ObjectNode p = project(u, pid);
            require(List.of("进行中", "结项待修改").contains(str(p, "status"))
                && !exercise(find("tasks", str(p, "taskId"))), "只能给进行中的课程项目布置工作项");
            text(b, "title", "due");
            String owner = str(b, "ownerId");
            require(contains(p.path("memberIds"), owner), "任务负责人必须是项目成员");
            ((ArrayNode) p.get("items")).add(o("id", id(), "title", str(b, "title"), "ownerId", owner,
                "status", "待开始", "due", str(b, "due"), "source", "教师任务"));
            activity(p, u, "创建任务：" + str(b, "title"));
        }
        case "itemStatus" -> {
            ObjectNode p = project(u, pid);
            editable(u, p);
            ObjectNode item = nested(p, "items", str(b, "itemId"));
            only(u, "student");
            require(str(item, "ownerId").equals(uid), "只能更新自己负责任务的状态");
            List<String> flow = List.of("待开始", "进行中", "待验收", "已完成");
            require(flow.contains(str(b, "status")), "任务状态无效");
            require(flow.indexOf(str(b, "status")) >= flow.indexOf(str(item, "status")), "任务状态只能向前流转");
            item.put("status", str(b, "status"));
            activity(p, u, "任务「" + str(item, "title") + "」更新为" + str(b, "status"));
        }
        case "message" -> {
            ObjectNode p = project(u, pid);
            editable(u, p);
            require(str(b, "text").length() <= 10000 && str(b, "code").length() <= 50000, "消息或代码过长");
            ArrayNode attachments = a(); Set<String> uniqueFiles = new LinkedHashSet<>();
            if (!str(b, "fileId").isBlank()) uniqueFiles.add(str(b, "fileId"));
            if (b.has("fileIds")) {
                require(b.path("fileIds").isArray(), "附件列表无效");
                for (JsonNode f : b.path("fileIds")) uniqueFiles.add(f.asText());
            }
            require(uniqueFiles.size() <= 10, "每条消息最多附带 10 个文件");
            for (String fid : uniqueFiles) { nested(p, "files", fid); attachments.add(fid); }
            require(!str(b, "text").isBlank() || !str(b, "code").isBlank() || !attachments.isEmpty(), "请输入消息、代码或添加附件");
            if (!str(b, "replyToId").isBlank()) nested(p, "messages", str(b, "replyToId"));
            require(str(b, "codeName").length() <= 180 && str(b, "codeLanguage").length() <= 40, "代码名称或语言过长");
            ((ArrayNode) p.get("messages")).add(o("id", id(), "userId", uid, "text", str(b, "text"),
                "code", str(b, "code"), "codeName", str(b, "codeName"), "codeLanguage", str(b, "codeLanguage"),
                "fileId", attachments.isEmpty() ? "" : attachments.get(0).asText(), "fileIds", attachments,
                "replyToId", str(b, "replyToId"), "time", now()));
            Set<String> mentioned = new HashSet<>();
            JsonNode selected = b.path("mentionIds");
            require(selected.isMissingNode() || selected.isArray() && selected.size() <= 100, "提及成员列表无效");
            for (JsonNode person : list("users")) {
                String recipient = str(person, "id");
                if (recipient.equals(uid) || role(person).equals("admin") || !person.path("enabled").asBoolean()) continue;
                if (selected.isArray() && !contains(selected, recipient)) continue;
                if (canProject(person, p) && str(b, "text").contains("@" + str(person, "name"))) mentioned.add(recipient);
            }
            for (String recipient : mentioned)
                notice(recipient, str(u, "name") + " 在项目讨论中提及你", str(b, "text"),
                    "/" + role(find("users", recipient)) + "/discussions/" + pid);
            activity(p, u, "发布项目讨论");
        }
        case "submitPhase" -> {
            ObjectNode p = project(u, pid);
            editable(u, p);
            only(u, "student");
            ObjectNode task = find("tasks", str(p, "taskId"));
            require(TaskSchedule.started(task), "任务尚未开始");
            if (exercise(task)) require(enrolled(uid, str(task, "classId")), "学生已不在该教学班");
            text(b, "description", "milestoneId");
            JsonNode phase = null;
            for (JsonNode m : phases(p))
                if (str(m, "id").equals(str(b, "milestoneId"))) phase = m;
            require(phase != null, "阶段不存在");
            if (!submissionMode(phase).equals("individual")) leader(u, p);
            require(phase.path("requiresSubmission").asBoolean(true), "该阶段无需提交");
            require(!phase.path("isFinal").asBoolean(), "最终验收请通过最终成果入口提交贡献比例");
            require(List.of("进行中", "结项待修改").contains(str(p, "status")), "审核期间不能修改阶段提交");
            submissionFiles(p, b.path("fileIds"), TaskSchedule.scheduled(task) || phase.path("requireMaterials").asBoolean(true));
            checkDeliverables(p, phase, b);
            JsonNode latest = latestSubmission(p, phase, uid);
            require(latest == null || !str(latest, "status").equals("已通过"), "当前阶段已通过");
            require(TaskSchedule.beforeDeadline(str(phase, "due"))
                || !TaskSchedule.scheduled(task) && latest != null && str(latest, "status").equals("退回修改"),
                "任务已截止，无法提交");
            require(latest == null || str(latest, "id").equals(str(b, "baseSubmissionId")), "提交版本已变化，请刷新后核对最新材料");
            if (latest != null && str(latest, "status").equals("待验收")) ((ObjectNode) latest).put("status", "已更新");
            ((ArrayNode) p.get("submissions")).add(o("id", id(), "milestoneId", str(b, "milestoneId"),
                "description", str(b, "description"), "fileIds", b.path("fileIds").deepCopy(),
                "submissionMode", submissionMode(phase), "deliverables", b.path("deliverables").isObject() ? b.path("deliverables").deepCopy() : o(),
                "status", "待验收", "comment", "", "time", now(), "submitterId", uid));
            activity(p, u, "提交阶段成果");
            notifyTeachers(p, "阶段成果待验收");
        }
        case "reviewPhase" -> {
            ObjectNode p = project(u, pid);
            teacher(u, p);
            editable(u, p);
            // 与 submitPhase 的状态门对称：结项待审核期间不再验收/退回阶段
            require(List.of("进行中", "结项待修改").contains(str(p, "status")), "结项审核期间不能验收阶段成果");
            ObjectNode sub = nested(p, "submissions", str(b, "submissionId"));
            require(str(sub, "status").equals("待验收"), "该提交已验收");
            JsonNode phase = null;
            for (JsonNode m : phases(p)) if (str(m, "id").equals(str(sub, "milestoneId"))) phase = m;
            require(phase != null, "该任务已删除，历史提交保留但不再批改");
            JsonNode current = latestSubmission(p, phase, str(sub, "submitterId"));
            require(current != null && str(current, "id").equals(str(sub, "id")), "当前提交已过期，请查看最新版本");
            text(b, "comment");
            if(!b.has("grading"))require(b.path("score").isNumber() && b.path("score").asDouble() >= 0 && b.path("score").asDouble() <= 100, "分数需为 0–100");
            sub.put("status", b.path("pass").asBoolean() ? "已通过" : "退回修改");
            if(b.has("grading")) {
                ObjectNode calculated=calculateReview(phase,b.path("grading"),u);
                sub.set("score",calculated.get("score")); sub.set("grading",calculated);
            } else {
                require(phase.path("rubric").isMissingNode() || phase.path("rubric").isEmpty(), "请按评分表逐项评分");
                ObjectNode calculated=Grading.calculate(phase,o("reviewers",a(o("name",str(u,"name"),"weight",100,"score",b.get("score")))),str(u,"name"));
                sub.set("score",calculated.get("score"));sub.set("grading",calculated);
            }
            sub.put("comment", str(b, "comment"));
            sub.put("reviewerId", uid);
            sub.put("reviewTime", now());
            if (b.path("pass").asBoolean() && exercise(find("tasks", str(p, "taskId")))) p.put("status", "已归档");
            activity(p, u, "阶段验收：" + str(sub, "status"));
            notifyMembers(p, "阶段成果" + str(sub, "status"), str(b, "comment"));
        }
        case "saveContribution" -> {
            ObjectNode p = project(u, pid);
            editable(u, p);
            only(u, "student");
            require(List.of("进行中", "结项待修改").contains(str(p, "status")), "结项审核中无法修改贡献");
            text(b, "role", "description", "evidence");
            for (JsonNode c : p.path("contributions"))
                if (str(c, "userId").equals(uid))
                    for (String k : List.of("role", "description", "evidence")) ((ObjectNode) c).put(k, str(b, k));
            activity(p, u, "更新个人职责与贡献");
        }
        case "submitFinal" -> {
            ObjectNode p = project(u, pid);
            editable(u, p);
            only(u, "student");
            leader(u, p);
            require(List.of("进行中", "结项待修改", "结项待审核").contains(str(p, "status")), "当前无法重复提交结项");
            ObjectNode task = find("tasks", str(p, "taskId"));
            require(!exercise(task), "班级任务通过作答提交");
            require(TaskSchedule.started(task), "课程项目尚未开始");
            for (JsonNode m : phases(p)) {
                if (m.path("isFinal").asBoolean() || !m.path("requiresSubmission").asBoolean(true)) continue;
                for (JsonNode member : p.path("memberIds")) {
                    JsonNode latest = latestSubmission(p, m, member.asText());
                    require(latest != null && str(latest, "status").equals("已通过"), "请先通过全部成员的阶段验收");
                }
            }
            boolean finalPublished = false;
            for (JsonNode m : phases(p)) if (m.path("isFinal").asBoolean()) {
                finalPublished = true;
                require(TaskSchedule.beforeDeadline(str(m, "due"))
                    || !TaskSchedule.scheduled(task) && str(p, "status").equals("结项待修改"), "任务已截止，无法提交");
            }
            require(finalPublished, "教师尚未发布项目验收任务");
            ObjectNode f = ((ObjectNode) b.path("final")).deepCopy();
            checkFinal(p, f, false);
            f.put("submitterId", uid);
            f.put("submitTime", now());
            f.put("approved", false);
            f.put("comment", "");
            f.remove(List.of("projectScore", "expertScore", "reviewerId", "reviewTime", "grading"));
            if (mysql) f.remove(List.of("expertName", "expertOrg", "expertComment"));
            for (JsonNode c : f.path("contributions")) ((ObjectNode) c).remove(List.of("score","comment"));
            if (p.hasNonNull("final")) ((ArrayNode) p.get("finalHistory")).add(p.get("final").deepCopy());
            p.set("final", f);
            p.set("contributions", f.path("contributions").deepCopy());
            p.put("status", "结项待审核");
            p.put("share", false);
            activity(p, u, "提交结项材料与成员贡献");
            notifyTeachers(p, "结项材料待审核");
        }
        case "reviewFinal" -> {
            ObjectNode p = project(u, pid);
            teacher(u, p);
            require(str(p, "status").equals("结项待审核"), "当前不在结项审核中");
            ObjectNode f = (ObjectNode) p.path("final");
            if (!str(b.path("final"), "submitTime").isBlank()) require(str(b.path("final"), "submitTime").equals(str(f, "submitTime")), "提交版本已变化，请刷新后查看最新成果再批改");
            if (b.path("pass").asBoolean()) {
                ObjectNode grading = ((ObjectNode) b.path("final")).deepCopy();
                ObjectNode merged = f.deepCopy();
                for (String k : List.of("contributions", "projectScore", "comment", "expertName", "expertOrg", "expertScore", "expertComment"))
                    if (grading.has(k)) merged.set(k, grading.get(k));
                if (mysql) merged.remove(List.of("expertName","expertOrg","expertScore","expertComment"));
                JsonNode phase = null;
                for (JsonNode m : phases(p)) if (m.path("isFinal").asBoolean()) phase=m;
                require(phase!=null,"项目验收任务已删除");
                if(b.has("grading")) {
                    ObjectNode calculated=calculateReview(phase,b.path("grading"),u);
                    merged.set("projectScore",calculated.get("score")); merged.set("grading",calculated);
                } else {
                    require(phase.path("rubric").isMissingNode() || phase.path("rubric").isEmpty(), "请按评分表逐项评分");
                    ObjectNode calculated=Grading.calculate(phase,o("reviewers",a(o("name",str(u,"name"),"weight",100,"score",merged.path("projectScore")))),str(u,"name"));
                    merged.set("projectScore",calculated.get("score"));merged.set("grading",calculated);
                }
                checkFinal(p, merged, true);
                merged.put("approved", true);
                merged.put("reviewerId", uid);
                merged.put("reviewTime", now());
                p.set("final", merged);
                p.set("contributions", merged.path("contributions").deepCopy());
                p.put("status", "已归档");
                activity(p, u, "确认个人成绩与贡献，结项通过并自动归档");
                for (JsonNode c : merged.path("contributions"))
                    notice(str(c, "userId"), "结项通过，个人成绩已确认",
                        str(p, "name") + "：" + c.path("score").asText() + " 分", "/student/projects/" + pid);
            } else {
                text(b, "comment");
                p.put("status", "结项待修改");
                f.put("comment", str(b, "comment"));
                f.put("approved", false);
                activity(p, u, "结项退回：" + str(b, "comment"));
                notifyMembers(p, "结项材料退回修改", str(b, "comment"));
            }
        }
        case "reopen" -> {
            ObjectNode p = project(u, pid);
            teacher(u, p);
            require(str(p, "status").equals("已归档"), "只有归档项目可以重新开放");
            p.put("status", "进行中");
            // 重新开放后项目恢复编辑，公开分享随之关闭（旧结项档案保留作历史成绩）
            p.put("share", false);
            activity(p, u, "移出归档，重新开放项目");
            notifyMembers(p, "项目重新开放", "教师已将项目移出归档");
        }
        case "share" -> {
            ObjectNode p = project(u, pid);
            require(role(u).equals("teacher") || str(p, "leaderId").equals(uid), "只有负责人或关联教师可以管理分享");
            if (b.path("enabled").asBoolean()) {
                require(p.path("final").path("approved").asBoolean(), "仅可分享已审核通过的最终成果");
                require(str(p, "status").equals("已归档"), "仅已归档项目的成果可以分享，重新开放后请先完成结项");
            }
            p.put("share", b.path("enabled").asBoolean());
            activity(p, u, b.path("enabled").asBoolean() ? "启用成果分享" : "关闭成果分享");
        }
        case "readNotices" -> {
            for (JsonNode n : list("notifications"))
                if (str(n, "userId").equals(uid) && (str(b, "id").isBlank() || str(n, "id").equals(str(b, "id"))))
                    ((ObjectNode) n).put("read", true);
        }
        case "historySave" -> {
            only(u, "student");
            ObjectNode h = ((ObjectNode) b.path("record")).deepCopy();
            text(h, "name", "year", "date", "role", "description", "contribution", "summary");
            h.put("userId", uid);
            if (str(h, "id").isBlank()) {
                h.put("id", id());
                list("history").add(h);
            } else {
                ObjectNode old = find("history", str(h, "id"));
                require(str(old, "userId").equals(uid), "只能编辑本人的补录记录");
                old.removeAll();
                old.setAll(h);
            }
            result.put("id", str(h, "id"));
        }
        case "historyDelete" -> {
            only(u, "student");
            ObjectNode h = find("history", str(b, "id"));
            require(str(h, "userId").equals(uid), "只能删除本人的历史补录");
            ArrayNode hs = list("history");
            for (int i = 0; i < hs.size(); i++)
                if (hs.get(i) == h) { hs.remove(i); break; }
        }
        case "toggleUser" -> {
            only(u, "admin");
            ObjectNode person = find("users", str(b, "id"));
            require(!str(person, "id").equals(uid), "不能停用当前管理员");
            person.put("enabled", b.path("enabled").asBoolean());
            person.put("sessionVersion", person.path("sessionVersion").asLong() + 1);
        }
        case "importPreview", "importConfirm" -> {
            only(u, "admin");
            ArrayNode checked = validateImport(str(b, "kind"), b.path("rows"));
            result.set("rows", checked);
            if (op.equals("importConfirm")) {
                for (JsonNode row : checked)
                    require(row.path("error").asText().isBlank(), "导入包含错误行，请修正后重新上传");
                String key = str(b, "kind");
                for (JsonNode row : checked) {
                    ObjectNode data = (ObjectNode) row.path("data").deepCopy();
                    list(key).add(data);
                }
                result.put("count", checked.size());
            }
        }
        case "reset" -> {
            only(u, "admin");
            if (mysql) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MySQL 模式不提供演示数据重置");
            state = Seed.create();
        }
        default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的业务操作");
        }
        return result;
    }

    ArrayNode validateImport(String kind, JsonNode rows) {
        require(List.of("users", "courses", "classes", "teachingClasses", "enrollments").contains(kind), "导入类型不支持");
        require(rows.isArray() && !rows.isEmpty() && rows.size() <= 1000, "每次导入 1–1000 行");
        ArrayNode checked = a();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (JsonNode r : rows) {
            ObjectNode data = ((ObjectNode) r).deepCopy();
            String error = "";
            try {
                if (kind.equals("enrollments")) {
                    text(r, "studentId", "classId");
                    only(user(str(r, "studentId")), "student");
                    find("teachingClasses", str(r, "classId"));
                    String key = str(r, "studentId") + ":" + str(r, "classId");
                    require(seen.add(key) && !enrolled(str(r, "studentId"), str(r, "classId")), "选课关系重复");
                } else {
                    text(r, "id", "name");
                    require(str(r, "id").matches("[A-Za-z0-9_-]{1,60}"), "标识只能包含字母、数字、下划线和短横线");
                    require(seen.add(str(r, "id")), "文件内标识重复");
                    for (JsonNode old : list(kind)) require(!str(old, "id").equals(str(r, "id")), "关键标识已存在，请使用新标识");
                    if (kind.equals("users")) {
                        require(List.of("student", "teacher").contains(str(r, "role")), "角色须为 student 或 teacher");
                        if (str(r, "role").equals("student")) {
                            text(r, "number");
                            if (!mysql || !str(r,"classId").isBlank()) find("classes", str(r, "classId"));
                            if (mysql) require(str(r,"number").matches("[0-9]{10}"),"学号应为 10 位数字");
                            for (JsonNode old : list("users")) require(!str(old, "number").equals(str(r, "number")), "学号重复");
                            require(seen.add("number:" + str(r, "number")), "文件内学号重复");
                        }
                        data.put("enabled", true);
                        if (mysql) {
                            data.retain("id","name","role","number","classId","enabled");
                            if (str(data,"role").equals("teacher")) data.remove("number");
                        }
                    }
                    if (kind.equals("teachingClasses")) {
                        // 旧版模板兼容：学年学期留空时按当前学期补齐，再校验格式
                        if (str(data, "academicYear").isBlank()) data.put("academicYear", Migrations.currentAcademicYear());
                        if (str(data, "semester").isBlank()) data.put("semester", Migrations.currentSemester());
                        Migrations.validateTerm(data);
                        text(r, "courseId");
                        find("courses", str(r, "courseId"));
                        require(r.path("teacherIds").isArray() && !r.path("teacherIds").isEmpty(), "至少指定一位教师");
                        for (JsonNode tid : r.path("teacherIds")) only(user(tid.asText()), "teacher");
                    }
                }
            } catch (ResponseStatusException e) {
                error = e.getReason();
            } catch (RuntimeException e) {
                error = "该行数据格式有误";
            }
            checked.add(o("row", ++index, "data", data, "error", error));
        }
        return checked;
    }

    // —— 文件 ——
    public synchronized ObjectNode upload(String uid, String pid, String category, MultipartFile file) throws IOException {
        ObjectNode u = user(uid), p = project(u, pid);
        permit(u, "projectFiles");
        editable(u, p);
        require(file.getSize() > 0 && file.getSize() <= 20 * 1024 * 1024, "文件大小应为 1 字节至 20 MB");
        require(List.of("文档", "图片", "代码", "压缩包", "其他").contains(category), "文件类别无效");
        String fid = id();
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("未命名文件").replaceAll("[\\\\/\\r\\n]", "_");
        Path target = repository.file(fid, uid);
        ObjectNode meta = o("id", fid, "name", filename, "category", category, "size", file.getSize(),
            "type", Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"),
            "userId", uid, "time", now());
        ObjectNode before = state.deepCopy();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            ((ArrayNode) p.get("files")).add(meta);
            activity(p, u, "上传资料：" + filename);
            save();
            return meta;
        } catch (IOException | RuntimeException e) {
            state = before;
            Files.deleteIfExists(target);
            throw e;
        }
    }

    public synchronized ObjectNode portfolio(String uid) {
        only(user(uid), "student");
        ObjectNode v = view(uid);
        return o("projects", filtered((ArrayNode) v.get("projects"), p -> !exercise(findOrNull("tasks", str(p, "taskId")))),
            "history", v.get("history"));
    }

    ObjectNode accessibleClass(String uid, String cid) {
        ObjectNode u = user(uid), tc = find("teachingClasses", cid);
        require(role(u).equals("admin") || (role(u).equals("teacher") ? contains(tc.path("teacherIds"), uid) : enrolled(uid, cid)), "无教学班访问权限");
        return tc;
    }
    public synchronized ObjectNode classFile(String uid, String cid, String fid) {
        ObjectNode tc = accessibleClass(uid, cid);
        if (str(tc.path("syllabus"), "id").equals(fid)) return ((ObjectNode) tc.get("syllabus")).deepCopy();
        return nested(tc, "resources", fid).deepCopy();
    }
    public synchronized ObjectNode classUpload(String uid, String cid, String category, MultipartFile file) throws IOException {
        ObjectNode tc = accessibleClass(uid, cid), u = user(uid);
        only(u, "teacher");
        permit(u, "courseFiles");
        require(List.of("syllabus", "reference").contains(category), "资料类型无效");
        require(file.getSize() > 0 && file.getSize() <= 20 * 1024 * 1024, "文件大小应为 1 字节至 20 MB");
        String fid = id(); Path target = repository.file(fid, uid);
        ObjectNode meta = o("id", fid, "name", Optional.ofNullable(file.getOriginalFilename()).orElse("未命名"), "category", category,
            "size", file.getSize(), "type", Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"), "userId", uid, "time", now());
        ObjectNode before = state.deepCopy();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            if (category.equals("syllabus")) tc.set("syllabus", meta);
            else { if (!tc.has("resources")) tc.set("resources", a()); ((ArrayNode)tc.get("resources")).add(meta); }
            for (JsonNode e : list("enrollments")) if (str(e, "classId").equals(cid))
                notice(str(e, "studentId"), category.equals("syllabus") ? "课程大纲已更新" : "新增课程参考资料", str(meta, "name"), "/student/classes/" + cid + "/resources");
            save(); return meta;
        } catch (IOException | RuntimeException e) { state = before; Files.deleteIfExists(target); throw e; }
    }

    public synchronized ObjectNode fileMeta(String uid, String pid, String fid) {
        return nested(project(user(uid), pid), "files", fid).deepCopy();
    }

    public synchronized byte[] fileBytes(ObjectNode f) throws IOException {
        if (f.has("seedText")) return str(f, "seedText").getBytes(StandardCharsets.UTF_8);
        return Files.readAllBytes(repository.file(str(f, "id"), str(f,"userId")));
    }

    // —— 公开分享（字段白名单，不输出学号、内部资料、消息或未审核材料） ——
    public synchronized ObjectNode publicProject(String shareId) {
        ObjectNode p = null;
        for (JsonNode item : list("projects")) if (str(item, "shareId").equals(shareId)) p = (ObjectNode) item;
        require(p != null && p.path("share").asBoolean() && p.path("final").path("approved").asBoolean(), "该项目暂未公开");
        ObjectNode t = find("tasks", str(p, "taskId"));
        ArrayNode members = a();
        for (JsonNode c : p.path("contributions"))
            members.add(o("name", str(find("users", str(c, "userId")), "name"), "role", str(c, "role")));
        ObjectNode f = (ObjectNode) p.path("final");
        ArrayNode images = a();
        for (JsonNode fid : f.path("imageIds")) {
            ObjectNode file = nested(p, "files", fid.asText());
            images.add(o("id", str(file, "id"), "name", str(file, "name")));
        }
        return o("name", str(p, "name"), "description", str(p, "description"),
            "tags", str(p, "tags"), "created", str(p, "created"), "status", str(p, "status"),
            "course", str(find("courses", str(t, "courseId")), "name"),
            "teachingClass", str(find("teachingClasses", str(t, "classId")), "name"),
            "members", members, "summary", str(f, "summary"), "result", str(f, "description"),
            "link", str(f, "link"), "comment", str(f, "comment"), "images", images);
    }

    public synchronized ObjectNode publicImage(String shareId, String fid) {
        ObjectNode pub = publicProject(shareId);
        boolean allowed = false;
        for (JsonNode image : pub.path("images")) if (str(image, "id").equals(fid)) allowed = true;
        require(allowed, "该文件不是公开成果图片");
        for (JsonNode p : list("projects"))
            if (str(p, "shareId").equals(shareId)) return nested((ObjectNode) p, "files", fid).deepCopy();
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
    }
}
