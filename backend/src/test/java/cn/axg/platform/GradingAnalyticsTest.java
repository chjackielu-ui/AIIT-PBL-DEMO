package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import static cn.axg.platform.Seed.*;
import static org.junit.jupiter.api.Assertions.*;

class GradingAnalyticsTest {
 @TempDir Path data;
 ObjectNode phase(){return o("rubric",a(o("id","a","dimension","答辩效果","title","问题回答","description","准确回答","maxPoints",25),o("id","b","dimension","项目内涵","title","创新度","description","设计依据","maxPoints",25)));}
 ObjectNode reviewers(){return o("reviewers",a(o("name","Teacher","weight",70,"points",o("a",20,"b",25)),o("name","Expert","weight",30,"points",o("a",15,"b",20))));}
 @Test void rubricNormalizesAndWeightsReviewersWithoutTrustingClientTotals() {
  ObjectNode phase=phase();Grading.validate(phase);ObjectNode input=reviewers();input.put("score",100);
  ObjectNode result=Grading.calculate(phase,input,"Recorder");assertEquals(84,result.path("score").asDouble());
  assertEquals(90,result.path("reviewers").get(0).path("score").asDouble());
  ((ObjectNode)phase.path("rubric").get(0)).put("maxPoints",100);
  assertEquals(25,result.path("rubric").get(0).path("maxPoints").asInt(),"Rule snapshot must remain independent");
  assertEquals("Recorder",result.path("recordedBy").asText());
 }
 @Test void rubricRejectsMissingOutOfRangeAndUnbalancedScores() {
  ObjectNode missing=reviewers();((ObjectNode)missing.path("reviewers").get(0).path("points")).remove("a");
  assertThrows(ResponseStatusException.class,()->Grading.calculate(phase(),missing,"Teacher"));
  ObjectNode invalid=reviewers();((ObjectNode)invalid.path("reviewers").get(0).path("points")).put("a",26);
  assertThrows(ResponseStatusException.class,()->Grading.calculate(phase(),invalid,"Teacher"));
  ObjectNode weights=reviewers();((ObjectNode)weights.path("reviewers").get(0)).put("weight",60);
  assertThrows(ResponseStatusException.class,()->Grading.calculate(phase(),weights,"Teacher"));
  ObjectNode changed=reviewers();changed.set("rubric",a());
  assertThrows(ResponseStatusException.class,()->Grading.calculate(phase(),changed,"Teacher"));
 }
 @Test void analyticsUsesAssignedTasksRealEventsAndPrivateClassAggregates() throws Exception {
  PlatformService s=new PlatformService(data.toString());s.list("projects").removeAll();s.list("tasks").removeAll();s.list("enrollments").removeAll();
  s.list("enrollments").add(o("classId","tc1","studentId","s1")).add(o("classId","tc1","studentId","s2"));
  s.list("tasks").add(o("id","test","classId","tc1","status","已发布","milestones",a(o("id","stage","due","2026-10-10","submissionMode","individual"),o("id","empty","due","2026-10-10"))));
  s.list("projects").add(o("id","project","taskId","test","status","进行中","memberIds",a("s1","s2"),"files",a(o("userId","s1")),"submissions",a(
   o("id","old","milestoneId","stage","submissionMode","individual","submitterId","s1","status","已更新","time","2026-10-08T12:00:00"),
   o("id","current","milestoneId","stage","submissionMode","individual","submitterId","s1","status","已通过","score",80,"time","2026-10-12T12:00:00"))));
  JsonNode mine=LearningAnalytics.metrics(s,"tc1","s1");assertEquals(2,mine.path("required").asInt());assertEquals(50,mine.path("submissionRate").asDouble());assertEquals(50,mine.path("completionRate").asDouble());assertEquals(0,mine.path("onTimeRate").asDouble());assertEquals(80,mine.path("averageScore").asDouble());
  assertTrue(LearningAnalytics.metrics(s,"tc1","s2").path("averageScore").isNull());
  JsonNode row=LearningAnalytics.forStudent(s,"s1").get(0);assertEquals(25,row.path("classAverage").path("completionRate").asDouble());assertEquals(1,row.path("samples").path("averageScore").asInt());assertEquals(2,row.path("studentCount").asInt());
  assertFalse(row.toString().contains("s2"));assertTrue(LearningAnalytics.forStudent(s,"s3").isEmpty());
 }
}
