package cn.axg.platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import static cn.axg.platform.Seed.*;
import static org.junit.jupiter.api.Assertions.*;
class ProjectWorkflowTest {
 @Test void teacherCanRemoveSubmittedTasksAndPublishNewTasksWithoutLosingHistory() throws Exception {
  PlatformService s=service();ObjectNode t=task(s,"individual");String tid=t.path("id").asText();
  ObjectNode phase=(ObjectNode)t.path("milestones").get(0);phase.put("requireMaterials",false);
  String mid=phase.path("id").asText(),pid=create(s,t,"s1",a("s1"));
  s.action("t1",o("op","reviewProject","projectId",pid,"pass",true,"comment","approved"));
  s.action("s1",o("op","submitPhase","projectId",pid,"milestoneId",mid,"description","history","fileIds",a()));
  ObjectNode update=t.deepCopy();JsonNode finalPhase=t.path("milestones").get(t.path("milestones").size()-1);
  update.set("milestones",a(finalPhase));
  assertThrows(ResponseStatusException.class,()->s.action("t2",o("op","saveTask","task",update)));
  s.action("t1",o("op","saveTask","task",update));
  assertEquals(1,s.find("tasks",tid).path("milestones").size());
  assertEquals(1,s.find("projects",pid).path("submissions").size());
  assertThrows(ResponseStatusException.class,()->s.action("s1",o("op","submitPhase","projectId",pid,"milestoneId",mid,"description","deleted","fileIds",a())));
  ((ArrayNode)update.get("milestones")).insert(0,o("id","","name","new stage","description","new task","due","2099-12-01","criteria","complete","scoringRule","teacher 70%, expert 30%"));
  s.action("t1",o("op","saveTask","task",update));
  ObjectNode reloaded=new PlatformService(data.toString()).view("s1");
  assertTrue(reloaded.path("tasks").toString().contains("new stage"));
  assertFalse(s.find("tasks",tid).path("milestones").get(0).path("id").asText().isBlank());
  assertEquals("teacher 70%, expert 30%",s.find("tasks",tid).path("milestones").get(0).path("scoringRule").asText());
 }
 @TempDir Path data;
 PlatformService service() throws Exception{return new PlatformService(data.toString());}
 ObjectNode task(PlatformService s,String type) throws Exception {
  ObjectNode t=s.find("tasks","ct1").deepCopy();t.put("id","");t.put("projectType",type);t.put("name",type+" task");t.put("teamDue","2099-01-01");t.put("due","2099-01-02");t.put("min",2);t.put("max",5);t.put("status","已发布");
  String id=s.action("t1",o("op","saveTask","task",t)).path("id").asText();return s.find("tasks",id);
 }
 String create(PlatformService s,ObjectNode t,String uid,ArrayNode members) throws Exception{return s.action(uid,o("op","createProject","taskId",t.path("id").asText(),"name",uid+" project","description","description","goal","goal","memberIds",members)).path("id").asText();}
 @Test void coldStartPersonalProjectsAreIndependentAndPersistent() throws Exception {
  PlatformService s=service();s.list("users").add(o("id","cold","name","New Student","role","student","enabled",true));
  ObjectNode t=task(s,"individual");assertFalse(s.canTask(s.user("cold"),t));
  assertThrows(ResponseStatusException.class,()->create(s,t,"cold",a("cold")));
  s.action("t1",o("op","addClassStudents","classId","tc1","studentIds",a("cold")));
  assertTrue(s.canTask(s.user("cold"),t));assertEquals(1,t.path("min").asInt());
  assertThrows(ResponseStatusException.class,()->create(s,t,"cold",a("cold","s1")));
  String first=create(s,t,"cold",a("cold")),second=create(s,t,"s1",a("s1"));
  assertEquals("待审核",s.find("projects",first).path("status").asText());assertNotEquals(first,second);
  assertThrows(ResponseStatusException.class,()->create(s,t,"s1",a("s1")));
  ObjectNode publish=o("op","saveProjectPhases","projectId",first,"milestones",
      a(o("id","local-phase","name","Requirements","description","project-specific","due","2099-12-01","criteria","complete","requireMaterials",false)));
  assertThrows(ResponseStatusException.class,()->s.action("t1",publish));
  s.action("t1",o("op","batchReviewProjects","projectIds",a(first,second)));
  s.action("t1",publish);
  assertThrows(ResponseStatusException.class,()->s.action("s1",o("op","submitPhase","projectId",second,
      "milestoneId","local-phase","description","wrong project","fileIds",a())));
  s.action("cold",o("op","submitPhase","projectId",first,"milestoneId","local-phase","description","personal result","fileIds",a()));
  assertEquals(0,s.find("projects",second).path("submissions").size());
  assertEquals(1,new PlatformService(data.toString()).find("projects",first).path("memberIds").size());
 }
 @Test void groupUsesOneProjectAndBulkApprovalIsAtomicAndAuthorized() throws Exception {
  PlatformService s=service();ObjectNode t=task(s,"group");String first=create(s,t,"s1",a("s1","s2")),second=create(s,t,"s4",a("s4","s5"));
  assertEquals("待审核",s.find("projects",first).path("status").asText());
  assertThrows(ResponseStatusException.class,()->create(s,t,"s2",a("s2","s3")));
  assertThrows(ResponseStatusException.class,()->s.action("t2",o("op","batchReviewProjects","projectIds",a(first,second))));
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","batchReviewProjects","projectIds",a(first,"missing"))));
  assertEquals("待审核",s.find("projects",first).path("status").asText());
  s.action("t1",o("op","batchReviewProjects","projectIds",a(first,second)));
  assertEquals("进行中",s.find("projects",second).path("status").asText());
  ObjectNode phase=(ObjectNode)s.find("tasks",t.path("id").asText()).path("milestones").get(0);phase.put("requireMaterials",false);
  s.action("s1",o("op","submitPhase","projectId",first,"milestoneId",phase.path("id").asText(),"description","team result","fileIds",a()));
  assertEquals(1,s.find("projects",first).path("submissions").size());
  assertThrows(ResponseStatusException.class,()->s.action("s2",o("op","submitPhase","projectId",first,"milestoneId",phase.path("id").asText(),"description","duplicate","fileIds",a())));
 }
}
