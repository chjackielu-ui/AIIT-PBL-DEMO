package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import static cn.axg.platform.Seed.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionRulesTest {
 @TempDir Path data;
 PlatformService service() throws Exception { return new PlatformService(data.toString()); }
 String prepare(PlatformService s,String mode) throws Exception {
  ObjectNode task=s.find("tasks","ct1").deepCopy();task.put("id","");task.put("name","Submission rules");task.put("teamDue","2099-01-01");task.put("due","2099-02-01");task.put("projectType","group");task.put("min",2);task.put("max",5);
  task.set("milestones",a(o("id","stage","name","Stage","description","Instructions","due","2099-03-01","criteria","Complete","requireMaterials",true,"submissionMode",mode,
   "deliverables",a(o("id","slides","title","PPT","extensions",a(".ppt",".pptx"),"minFiles",1,"maxFiles",2,"required",true))),
   o("id","final","name","Acceptance","description","Final instructions","criteria","Complete","due","2099-04-01","isFinal",true,"requireMaterials",false)));
  String tid=s.action("t1",o("op","saveTask","task",task)).path("id").asText();
  String pid=s.action("s1",o("op","createProject","taskId",tid,"name","Team","description","Description","goal","Goal","memberIds",a("s1","s2"))).path("id").asText();
  s.action("t1",o("op","reviewProject","projectId",pid,"pass",true,"comment","Approved"));
  ((ArrayNode)s.find("projects",pid).get("files")).add(o("id","ppt","name","DEMO.PPTX","type","application/octet-stream","category","文档"));
  ((ArrayNode)s.find("projects",pid).get("files")).add(o("id","pdf","name","fake.pptx.pdf","type","application/pdf","category","文档"));
  return pid;
 }
 ObjectNode submission(String pid,String file,String base) {return o("op","submitPhase","projectId",pid,"milestoneId","stage","description","Result","fileIds",a(file),"deliverables",o("slides",a(file)),"baseSubmissionId",base);}
 JsonNode latest(PlatformService s,String pid) {JsonNode subs=s.find("projects",pid).path("submissions");return subs.get(subs.size()-1);}
 void grade(PlatformService s,String pid,String sid,int score) throws Exception {s.action("t1",o("op","reviewPhase","projectId",pid,"submissionId",sid,"pass",true,"score",score,"comment","Passed"));}

 @Test void sharedSubmissionRequiresLeaderAndRejectsStaleUpdatesAndReviews() throws Exception {
  PlatformService s=service();String pid=prepare(s,"shared");
  assertThrows(ResponseStatusException.class,()->s.action("s3",submission(pid,"ppt","")));
  assertThrows(ResponseStatusException.class,()->s.action("t1",submission(pid,"ppt","")));
  assertThrows(ResponseStatusException.class,()->s.action("s2",submission(pid,"ppt","")));
  s.action("s1",submission(pid,"ppt",""));String first=latest(s,pid).path("id").asText();
  assertEquals("s1",latest(s,pid).path("submitterId").asText());
  assertThrows(ResponseStatusException.class,()->s.action("s1",submission(pid,"ppt","")));
  s.action("s1",submission(pid,"ppt",first));String second=latest(s,pid).path("id").asText();
  assertEquals("已更新",s.find("projects",pid).path("submissions").get(0).path("status").asText());
  assertThrows(ResponseStatusException.class,()->grade(s,pid,first,60));
  grade(s,pid,second,92);
  assertThrows(ResponseStatusException.class,()->s.action("s2",submission(pid,"ppt",second)));
  assertEquals(92,new PlatformService(data.toString()).find("projects",pid).path("submissions").get(1).path("score").asInt());
 }

 @Test void checklistRejectsWrongFormatMissingCountsForeignFilesAndInvalidConfiguration() throws Exception {
  PlatformService s=service();String pid=prepare(s,"shared");
  assertThrows(ResponseStatusException.class,()->s.action("s1",submission(pid,"pdf","")));
  assertThrows(ResponseStatusException.class,()->s.action("s1",submission(pid,"missing","")));
  ObjectNode missing=submission(pid,"ppt","");missing.set("deliverables",o());
  assertThrows(ResponseStatusException.class,()->s.action("s1",missing));
  ObjectNode duplicate=submission(pid,"ppt","");duplicate.set("deliverables",o("slides",a("ppt","ppt")));
  assertThrows(ResponseStatusException.class,()->s.action("s1",duplicate));
  String tid=s.find("projects",pid).path("taskId").asText();ObjectNode t=s.find("tasks",tid).deepCopy();
  ((ObjectNode)t.path("milestones").get(0).path("deliverables").get(0)).put("maxFiles",0);
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","saveTask","task",t)));
  assertEquals(0,s.find("projects",pid).path("submissions").size());
 }

 @Test void individualSubmissionKeepsIndependentGradesAndFinalWaitsForEveryMember() throws Exception {
  PlatformService s=service();String pid=prepare(s,"individual");
  s.action("s1",submission(pid,"ppt",""));String first=latest(s,pid).path("id").asText();
  s.action("s2",submission(pid,"ppt",""));String second=latest(s,pid).path("id").asText();
  grade(s,pid,second,95);
  ObjectNode f=o("summary","Summary","description","Final result","fileIds",a(),"imageIds",a(),"link","","contributions",a(
   o("userId","s1","role","Lead","description","Implementation","evidence","Slides","percent",50),
   o("userId","s2","role","Member","description","Testing","evidence","Slides","percent",50)));
  assertThrows(ResponseStatusException.class,()->s.action("s2",o("op","submitFinal","projectId",pid,"final",f)));
  grade(s,pid,first,80);
  assertThrows(ResponseStatusException.class,()->s.action("s2",o("op","submitFinal","projectId",pid,"final",f)));
  s.action("s1",o("op","submitFinal","projectId",pid,"final",f));
  ObjectNode oldVersion=s.find("projects",pid).path("final").deepCopy();
  f.put("description","Updated before grading");s.action("s1",o("op","submitFinal","projectId",pid,"final",f));
  ResponseStatusException stale=assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","reviewFinal","projectId",pid,"pass",true,"final",oldVersion)));
  assertTrue(stale.getReason().contains("提交版本已变化"));
  assertEquals(1,s.find("projects",pid).path("finalHistory").size());
  assertEquals("s1",s.find("projects",pid).path("final").path("submitterId").asText());
  ObjectNode finalGrade=s.find("projects",pid).path("final").deepCopy();finalGrade.put("projectScore",88);finalGrade.put("comment","Complete");
  for(JsonNode c:finalGrade.path("contributions")){((ObjectNode)c).put("score",90);((ObjectNode)c).put("comment","Confirmed");}
  s.action("t1",o("op","reviewFinal","projectId",pid,"pass",true,"final",finalGrade));
  assertEquals(88,s.find("projects",pid).path("final").path("grading").path("score").asInt());
  assertEquals(100,s.find("projects",pid).path("final").path("grading").path("reviewers").get(0).path("weight").asInt());
  JsonNode subs=new PlatformService(data.toString()).find("projects",pid).path("submissions");
  assertEquals(80,subs.get(0).path("score").asInt());assertEquals(95,subs.get(1).path("score").asInt());
 }
 @Test void deadlineLocksPendingSubmissionButTeacherReturnAllowsCorrection() throws Exception {
  PlatformService s=service();String pid=prepare(s,"shared");
  s.action("s1",submission(pid,"ppt",""));String first=latest(s,pid).path("id").asText();
  ObjectNode phase=(ObjectNode)s.find("tasks",s.find("projects",pid).path("taskId").asText()).path("milestones").get(0);
  phase.put("due","2000-01-01");
  assertThrows(ResponseStatusException.class,()->s.action("s1",submission(pid,"ppt",first)));
  s.action("t1",o("op","reviewPhase","projectId",pid,"submissionId",first,"pass",false,"score",50,"comment","Please correct"));
  s.action("s1",submission(pid,"ppt",first));
  assertEquals("待验收",latest(s,pid).path("status").asText());
 }
 @Test void teacherAssignsTasksButCannotChangeStudentWorkOrSubmitForStudents() throws Exception {
  PlatformService s=service();String pid=prepare(s,"shared");
  s.action("t1",o("op","addItem","projectId",pid,"title","Team task","due","2099-03-01","ownerId","s1"));
  String item=s.find("projects",pid).path("items").get(0).path("id").asText();
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","itemStatus","projectId",pid,"itemId",item,"status","已完成")));
  assertThrows(ResponseStatusException.class,()->s.action("t1",submission(pid,"ppt","")));
  s.action("s1",o("op","itemStatus","projectId",pid,"itemId",item,"status","已完成"));
  s.find("projects",pid).put("status","待修改");
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","editProject","projectId",pid,"name","Teacher change","description","Description","goal","Goal")));
  s.action("s1",o("op","editProject","projectId",pid,"name","Leader change","description","Description","goal","Goal"));
 }
 @Test void exerciseHasOneDirectSubmissionAndArchivesAfterTeacherApproval() throws Exception {
  PlatformService s=service();String group=prepare(s,"shared");String oldTid=s.find("projects",group).path("taskId").asText();
  ObjectNode t=s.find("tasks",oldTid).deepCopy();t.put("id","");t.put("taskKind","exercise");
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","saveTask","task",t)));
  ((ArrayNode)t.path("milestones")).remove(1);
  String tid=s.action("t1",o("op","saveTask","task",t)).path("id").asText();assertEquals("individual",s.find("tasks",tid).path("projectType").asText());
  String pid=s.action("s1",o("op","createProject","taskId",tid,"name","Exercise","description","Instructions","goal","Complete","memberIds",a("s1"))).path("id").asText();
  assertEquals("进行中",s.find("projects",pid).path("status").asText());
  ((ArrayNode)s.find("projects",pid).get("files")).add(o("id","ppt","name","result.pptx"));
  s.action("s1",submission(pid,"ppt",""));grade(s,pid,latest(s,pid).path("id").asText(),90);
  assertEquals("已归档",new PlatformService(data.toString()).find("projects",pid).path("status").asText());
 }
}
