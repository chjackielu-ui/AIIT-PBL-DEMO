package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static cn.axg.platform.Seed.*;
import static cn.axg.platform.PlatformService.*;

final class CourseGrading {
 private CourseGrading() {}
 static ArrayNode dimensions(JsonNode rule) {
  if(rule.path("dimensions").isArray())return rule.path("dimensions").deepCopy();
  return a(o("id","attendance","name","点名","weight",rule.path("attendance").asDouble(20),"source","manual"),o("id","homework","name","作业","weight",rule.path("homework").asDouble(20),"source","manual"),o("id","project","name","实训进度","weight",rule.path("project").asDouble(60),"source","project"));
 }
 static ObjectNode normalize(JsonNode rule) {
  require(rule.isObject(),"评分规则格式无效");
  if(!rule.has("dimensions"))for(String key:List.of("attendance","homework","project"))require(rule.path(key).isNumber(),"请填写评分维度与权重");
  else require(rule.path("dimensions").isArray(),"评分维度格式无效");
  ArrayNode dims=dimensions(rule);require(!dims.isEmpty() && dims.size()<=20,"评分维度需为 1–20 项");
  Set<String> ids=new HashSet<>(),names=new HashSet<>();double weight=0;int auto=0;
  for(JsonNode d:dims){
   require(str(d,"id").matches("[A-Za-z0-9_-]{1,80}") && ids.add(str(d,"id")),"维度标识无效或重复");
   String name=str(d,"name").trim();require(!name.isBlank() && name.length()<=30 && names.add(name),"维度名称不能为空、重复或超过 30 字");
   ((ObjectNode)d).put("name",name);weight+=Grading.score(d.path("weight"),100,"维度权重需为 0–100");
   require(List.of("manual","project").contains(str(d,"source")),"维度评分方式无效");if(str(d,"source").equals("project"))auto++;
  }
  require(auto<=1,"实训进度自动汇总维度最多一项");require(Math.abs(weight-100)<0.001,"评分权重合计必须为 100%");
  return o("dimensions",dims);
 }
 static double projectScore(PlatformService s,JsonNode task,ObjectNode p,String uid) {
  double total=0,weight=0;
  for(JsonNode phase:projectPhases(task,p)){
   if(!phase.path("isFinal").asBoolean() && !phase.path("requiresSubmission").asBoolean(true))continue;
   double w=phase.path("scoreWeight").asDouble(1),score=0;weight+=w;
   if(phase.path("isFinal").asBoolean()){
    if(p.path("final").path("approved").asBoolean())for(JsonNode c:p.path("final").path("contributions"))if(str(c,"userId").equals(uid))score=c.path("score").asDouble();
   }else{JsonNode sub=s.latestSubmission(p,phase,uid);if(sub!=null && str(sub,"status").equals("已通过"))score=sub.path("score").asDouble();}
   total+=score*w;
  }
  return weight>0?total/weight:0;
 }
 static Double progressScore(PlatformService s,String cid,String uid) {
  double total=0;int count=0;boolean scored=false;
  for(JsonNode task:s.list("tasks"))if(str(task,"classId").equals(cid) && !str(task,"status").equals("草稿")){
   count++;
   for(JsonNode p:s.list("projects"))if(str(p,"taskId").equals(str(task,"id")) && s.contains(p.path("memberIds"),uid) && !List.of("待审核","待修改","已关闭").contains(str(p,"status"))){
    for (JsonNode phase : projectPhases(task,p)) {
     if (phase.path("isFinal").asBoolean()) {
      if (p.path("final").path("approved").asBoolean() && p.path("final").path("contributions").valueStream()
          .anyMatch(c -> str(c,"userId").equals(uid) && c.path("score").isNumber())) scored=true;
     } else if (phase.path("requiresSubmission").asBoolean(true)) {
      JsonNode submission=s.latestSubmission((ObjectNode)p,phase,uid);
      if (submission!=null && str(submission,"status").equals("已通过") && submission.path("score").isNumber()) scored=true;
     }
    }
    total+=projectScore(s,task,(ObjectNode)p,uid);
    break;
   }
  }
  if (s.mysql() && !scored) return null;
  return count>0?LearningAnalytics.round(total/count):0;
 }
 static ArrayNode grades(PlatformService s,JsonNode viewer) {
  ArrayNode classes=a();String role=str(viewer,"role"),uid=str(viewer,"id");
  for(JsonNode tc:s.list("teachingClasses")){
   String cid=str(tc,"id");if(role.equals("teacher") && !s.contains(tc.path("teacherIds"),uid) || role.equals("student") && !s.enrolled(uid,cid))continue;
   ArrayNode dims=dimensions(tc.path("gradingRule")),rows=a();Set<String> seen=new HashSet<>();
   for(JsonNode enrollment:s.list("enrollments"))if(str(enrollment,"classId").equals(cid)){
    String sid=str(enrollment,"studentId");if(!seen.add(sid) || role.equals("student") && !uid.equals(sid))continue;
    ObjectNode scores=o();double total=0;boolean complete=true;
    for(JsonNode d:dims){
     JsonNode saved=tc.path("courseScores").path(sid).path(str(d,"id"));Double score=null;
     if(str(d,"source").equals("project"))score=progressScore(s,cid,sid);else if(saved.isNumber())score=saved.asDouble();
     if(score!=null){scores.put(str(d,"id"),score);total+=score*d.path("weight").asDouble()/100;}else if(d.path("weight").asDouble()>0)complete=false;
    }
    ObjectNode row=o("studentId",sid,"scores",scores);if(complete)row.put("total",LearningAnalytics.round(total));else row.putNull("total");rows.add(row);
   }
   classes.add(o("classId",cid,"dimensions",dims,"rows",rows));
  }
  return classes;
 }
 static void save(PlatformService s,JsonNode u,ObjectNode body) {
  ObjectNode tc=ClassEnrollment.managedClass(s,u,str(body,"classId"));ArrayNode dims=dimensions(tc.path("gradingRule"));
  require(dims.equals(body.path("dimensions")),"评分维度已变化，请刷新后重新录入");JsonNode rows=body.path("scores");require(rows.isObject(),"成绩格式无效");
  ObjectNode saved=tc.path("courseScores").isObject()?(ObjectNode)tc.path("courseScores"):o();
  Iterator<Map.Entry<String,JsonNode>> people=rows.fields();
  while(people.hasNext()){
   var person=people.next();String sid=person.getKey();require(s.enrolled(sid,str(tc,"id")),"只能为本班学生录入成绩");require(person.getValue().isObject(),"成绩格式无效");
   ObjectNode scores=saved.path(sid).isObject()?((ObjectNode)saved.path(sid)).deepCopy():o();
   person.getValue().fields().forEachRemaining(entry->{
    JsonNode dimension=null;for(JsonNode d:dims)if(str(d,"id").equals(entry.getKey()))dimension=d;
    require(dimension!=null && str(dimension,"source").equals("manual"),"评分维度已移除或属于自动汇总项");
    if(entry.getValue().isNull())scores.remove(entry.getKey());else scores.put(entry.getKey(),Grading.score(entry.getValue(),100,"成绩需为 0–100 分"));
   });saved.set(sid,scores);
  }
  tc.set("courseScores",saved);
 }
}
