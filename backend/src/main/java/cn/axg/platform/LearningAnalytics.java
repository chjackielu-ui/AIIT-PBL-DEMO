package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static cn.axg.platform.Seed.*;
import static cn.axg.platform.PlatformService.str;
import static cn.axg.platform.PlatformService.projectPhases;

/** 用真实提交和教师评分计算，仅向学生返回本人指标和匿名班级平均。 */
final class LearningAnalytics {
 private LearningAnalytics() {}
 static double round(double n){return Math.round(n*100)/100.0;}
 static void ratio(ObjectNode out,String key,int count,int total){if(total>0)out.put(key,round(count*100.0/total));else out.putNull(key);}
 static ObjectNode metrics(PlatformService s,String cid,String uid) {
  int required=0,submitted=0,passed=0,timed=0,onTime=0,scored=0,uploads=0;double scores=0;
  for(JsonNode task:s.list("tasks")){
   if(!str(task,"classId").equals(cid) || str(task,"status").equals("草稿"))continue;
   ObjectNode project=null;
   for(JsonNode p:s.list("projects"))if(str(p,"taskId").equals(str(task,"id")) && s.contains(p.path("memberIds"),uid) && !str(p,"status").equals("已关闭"))project=(ObjectNode)p;
   if(project!=null)for(JsonNode file:project.path("files"))if(str(file,"userId").equals(uid))uploads++;
   for(JsonNode phase:projectPhases(task,project)){
    if(!phase.path("isFinal").asBoolean() && !phase.path("requiresSubmission").asBoolean(true))continue;
    required++;if(project==null)continue;
    JsonNode record;boolean accepted;String time;JsonNode score;
    if(phase.path("isFinal").asBoolean()){
     record=project.path("final");accepted=record.path("approved").asBoolean();time=str(record,"submitTime");score=null;
     for(JsonNode c:record.path("contributions"))if(str(c,"userId").equals(uid))score=c.path("score");
    }else{
     record=s.latestSubmission(project,phase,uid);accepted=record!=null && str(record,"status").equals("已通过");time=record==null?"":str(record,"time");score=record==null?null:record.path("score");
    }
    if(record==null || record.isMissingNode() || record.isNull())continue;
    submitted++;
    if(accepted){passed++;if(score!=null && score.isNumber()){scores+=score.asDouble();scored++;}}
    Boolean timely = TaskSchedule.onTime(time, str(phase, "due"));
    if (timely != null) { timed++; if (timely) onTime++; }
   }
  }
  ObjectNode out=o("required",required,"submitted",submitted,"passed",passed,"scored",scored,"timed",timed,"onTime",onTime,"uploads",uploads);
  ratio(out,"submissionRate",submitted,required);ratio(out,"completionRate",passed,required);ratio(out,"onTimeRate",onTime,timed);
  if(scored>0)out.put("averageScore",round(scores/scored));else out.putNull("averageScore");
  return out;
 }
 static ArrayNode forStudent(PlatformService s,String uid) {
  ArrayNode rows=a();
  for(JsonNode tc:s.list("teachingClasses")){
   String cid=str(tc,"id");if(!s.enrolled(uid,cid))continue;
   Set<String> ids=new LinkedHashSet<>();
   for(JsonNode e:s.list("enrollments"))if(str(e,"classId").equals(cid)){
    ObjectNode u=s.findOrNull("users",str(e,"studentId"));if(u!=null && u.path("enabled").asBoolean() && str(u,"role").equals("student"))ids.add(str(u,"id"));
   }
   List<ObjectNode> all=ids.stream().map(id->metrics(s,cid,id)).toList();ObjectNode averages=o(),samples=o();
   for(String metric:List.of("submissionRate","completionRate","onTimeRate","averageScore","uploads")){
    double sum=0;int count=0;for(ObjectNode m:all)if(m.path(metric).isNumber()){sum+=m.path(metric).asDouble();count++;}
    if(count>0)averages.put(metric,round(sum/count));else averages.putNull(metric);samples.put(metric,count);
   }
   rows.add(o("classId",cid,"studentCount",ids.size(),"mine",metrics(s,cid,uid),"classAverage",averages,"samples",samples));
  }
  return rows;
 }
}
