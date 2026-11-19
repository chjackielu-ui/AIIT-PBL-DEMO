package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import static cn.axg.platform.Seed.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassEnrollmentGradingTest {
 @Test void scanLinkLoadsTheApplicationDirectly()throws Exception{
  org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new SpaController()).build()
   .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/join/test-token"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
 }
 @TempDir Path data;
 ObjectNode row(String seq,String name,String number,String cls){return o("sequence",seq,"name",name,"number",number,"className",cls,"contact","13800000000");}
 ObjectNode dimension(String id,String name,int weight){return o("id",id,"name",name,"weight",weight,"source","manual");}
 void rule(PlatformService s,ArrayNode dims)throws Exception{s.action("t1",o("op","saveCourseDetails","classId","tc1","courseDescription","规则说明","gradingRule",o("dimensions",dims)));}
 JsonNode grades(PlatformService s,String uid){for(JsonNode g:s.view(uid).path("courseGrades"))if(g.path("classId").asText().equals("tc1"))return g;throw new AssertionError();}
 @Test void importCreatesStudentsPreservesTextSkipsDuplicatesAndRollsBackInvalidBatch()throws Exception{
  PlatformService s=new PlatformService(data.toString());int users=s.list("users").size(),enrollments=s.list("enrollments").size();
  ArrayNode rows=a(row("1","新学生","001234567890","软件2601"));
  assertEquals(0,s.enrollmentPreview("t1","tc1",rows).path("errors").asInt());assertEquals(users,s.list("users").size());
  assertThrows(ResponseStatusException.class,()->s.enrollmentPreview("t2","tc1",rows));
  ArrayNode bad=a(rows.get(0),row("2","错误姓名",s.user("s1").path("number").asText(),"软件2401"));
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","importClassStudents","classId","tc1","rows",bad)));
  assertEquals(users,s.list("users").size());assertEquals(enrollments,s.list("enrollments").size());
  assertEquals(1,s.action("t1",o("op","importClassStudents","classId","tc1","rows",rows)).path("added").asInt());
  assertEquals(1,s.action("t1",o("op","importClassStudents","classId","tc1","rows",rows)).path("skipped").asInt());
  PlatformService restored=new PlatformService(data.toString());JsonNode student=restored.list("users").get(users);
  assertEquals("001234567890",student.path("number").asText());assertEquals("13800000000",student.path("contact").asText());assertTrue(restored.enrolled(student.path("id").asText(),"tc1"));
  assertFalse(restored.view("s1").path("users").get(users).has("contact"));assertTrue(restored.view("t1").path("users").get(users).has("number"));
 }
 @Test void invitationJoinsOnlySelectedClassAndIsIdempotent()throws Exception{
  PlatformService s=new PlatformService(data.toString());
  assertThrows(ResponseStatusException.class,()->s.action("t2",o("op","createClassInvite","classId","tc1")));
  String token=s.action("t1",o("op","createClassInvite","classId","tc1")).path("token").asText();
  assertEquals("tc1",s.classInvitation(token).path("classId").asText());assertFalse(s.classInvitation(token).has("users"));
  assertFalse(s.view("s1").path("teachingClasses").get(0).has("inviteToken"));
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","joinClass","token",token)));
  s.action("s6",o("op","joinClass","token",token));int count=s.list("enrollments").size();s.action("s6",o("op","joinClass","token",token));
  assertEquals(count,s.list("enrollments").size());assertTrue(s.enrolled("s6","tc1"));assertTrue(s.canTask(s.user("s6"),s.find("tasks","ct1")));
  assertThrows(ResponseStatusException.class,()->s.action("s7",o("op","joinClass","token","invalid")));
 }
 @Test void customDimensionsRecalculateTotalsAndOnlyExposeOwnGrades()throws Exception{
  PlatformService s=new PlatformService(data.toString());ArrayNode dims=a(dimension("attendance","点名",50),dimension("homework","作业",50));rule(s,dims);
  assertTrue(grades(s,"s1").path("rows").get(0).path("total").isNull());
  s.action("t1",o("op","saveCourseGrades","classId","tc1","dimensions",dims,"scores",o("s1",o("attendance",80,"homework",100),"s2",o("attendance",50,"homework",60))));
  assertEquals(90,grades(s,"s1").path("rows").get(0).path("total").asDouble());assertEquals(1,grades(s,"s1").path("rows").size());
  ArrayNode expanded=a(dimension("attendance","点名",20),dimension("homework","作业",30),dimension("participation","课堂表现",50));rule(s,expanded);
  assertTrue(grades(s,"s1").path("rows").get(0).path("total").isNull());
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","saveCourseGrades","classId","tc1","dimensions",dims,"scores",o("s1",o("attendance",90)))));
  s.action("t1",o("op","saveCourseGrades","classId","tc1","dimensions",expanded,"scores",o("s1",o("participation",60))));
  assertEquals(76,grades(s,"s1").path("rows").get(0).path("total").asDouble());
  ArrayNode remaining=a(dimension("attendance","点名",100));rule(s,remaining);
  assertEquals(80,grades(s,"s1").path("rows").get(0).path("total").asDouble());assertFalse(s.find("teachingClasses","tc1").path("courseScores").path("s1").has("participation"));
  assertThrows(ResponseStatusException.class,()->rule(s,a(dimension("x","重复",50),dimension("y","重复",50))));
  assertThrows(ResponseStatusException.class,()->s.action("s1",o("op","saveCourseGrades","classId","tc1","dimensions",remaining,"scores",o("s1",o("attendance",100)))));
  assertEquals(80,grades(new PlatformService(data.toString()),"s1").path("rows").get(0).path("total").asDouble());
 }
 @Test void practicalProgressUsesIndividualScoresAndCannotBeManuallyOverwritten()throws Exception{
  PlatformService s=new PlatformService(data.toString());ObjectNode task=s.find("tasks","ct1"),p=s.find("projects","p1");
  task.set("milestones",a(o("id","shared","scoreWeight",1),o("id","individual","submissionMode","individual","scoreWeight",1),o("id","final","isFinal",true,"scoreWeight",2)));
  p.set("submissions",a(o("milestoneId","shared","status","已通过","score",80),o("milestoneId","individual","submissionMode","individual","submitterId","s1","status","已通过","score",100),o("milestoneId","individual","submissionMode","individual","submitterId","s2","status","已通过","score",40)));
  p.set("final",o("approved",true,"contributions",a(o("userId","s1","score",70),o("userId","s2","score",50))));
  assertEquals(80,CourseGrading.projectScore(s,task,p,"s1"));assertEquals(55,CourseGrading.projectScore(s,task,p,"s2"));
  ArrayNode dims=a(o("id","project","name","实训进度","source","project","weight",100));rule(s,dims);
  assertThrows(ResponseStatusException.class,()->s.action("t1",o("op","saveCourseGrades","classId","tc1","dimensions",dims,"scores",o("s1",o("project",100)))));
 }
}
