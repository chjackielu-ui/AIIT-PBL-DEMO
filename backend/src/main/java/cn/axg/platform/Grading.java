package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static cn.axg.platform.Seed.*;
import static cn.axg.platform.PlatformService.require;
import static cn.axg.platform.PlatformService.str;

/** 评分规则由教师维护；所有结果在服务端重算，并保存当次规则快照。 */
final class Grading {
 private Grading() {}
 static void validate(JsonNode phase) {
  if(phase.has("scoreWeight"))require(phase.path("scoreWeight").isNumber() && finite(phase.path("scoreWeight").asDouble()) && phase.path("scoreWeight").asDouble()>0 && phase.path("scoreWeight").asDouble()<=100,"任务权重需大于 0 且不超过 100");
  if(!phase.has("rubric"))return;
  JsonNode rubric=phase.path("rubric");require(rubric.isArray() && rubric.size()<=30,"评分观测点最多 30 项");
  Set<String> ids=new HashSet<>();
  for(JsonNode point:rubric){
   require(!str(point,"id").isBlank() && ids.add(str(point,"id")),"评分观测点标识为空或重复");
   require(!str(point,"dimension").isBlank() && !str(point,"title").isBlank(),"请填写评分维度和观测点");
   require(point.path("maxPoints").isNumber() && finite(point.path("maxPoints").asDouble()) && point.path("maxPoints").asDouble()>0 && point.path("maxPoints").asDouble()<=100,"观测点满分需大于 0 且不超过 100");
  }
 }
 static boolean finite(double value){return Double.isFinite(value);}
 static double score(JsonNode node,double max,String message){require(node.isNumber() && finite(node.asDouble()) && node.asDouble()>=0 && node.asDouble()<=max,message);return node.asDouble();}
 static ObjectNode calculate(JsonNode phase,JsonNode grading,String teacherName) {
  JsonNode rubric=phase.path("rubric");boolean detailed=rubric.isArray() && !rubric.isEmpty();
  if(grading.has("rubric"))require(grading.path("rubric").equals(detailed?rubric:a()),"评分表已变化，请刷新后核对新规则");
  JsonNode reviewers=grading.path("reviewers");
  require(reviewers.isArray() && !reviewers.isEmpty() && reviewers.size()<=20,"请填写 1–20 位评分人员");
  double weight=0,total=0,max=0;ArrayNode results=a();
  if(detailed)for(JsonNode point:rubric)max+=point.path("maxPoints").asDouble();
  Set<String> names=new HashSet<>();
  for(JsonNode row:reviewers){
   String name=str(row,"name").trim();require(!name.isBlank() && name.length()<=100 && names.add(name),"评分人员不能为空或重复");
   double w=score(row.path("weight"),100,"评分人员权重需为 0–100");require(w>0,"评分人员权重需大于 0");weight+=w;
   double normalized;ObjectNode points=o();
   if(detailed){
    double sum=0;require(row.path("points").isObject(),"请逐项填写观测点评分");
    Set<String> ids=new HashSet<>();
    for(JsonNode point:rubric){String id=str(point,"id");ids.add(id);double n=score(row.path("points").path(id),point.path("maxPoints").asDouble(),str(point,"title")+"：评分超出范围或未填写");sum+=n;points.put(id,n);}
    row.path("points").fieldNames().forEachRemaining(id->require(ids.contains(id),"评分标准已变化，请刷新并重新评分"));
    normalized=sum/max*100;
   }else normalized=score(row.path("score"),100,"分数需为 0–100");
   total+=normalized*w/100;
   results.add(o("name",name,"weight",w,"score",Math.round(normalized*100)/100.0,"points",points,"comment",str(row,"comment")));
  }
  require(Math.abs(weight-100)<0.001,"评分人员权重合计必须为 100%");
  return o("score",Math.round(total*100)/100.0,"reviewers",results,"rubric",detailed?rubric.deepCopy():a(),"recordedBy",teacherName,
   "formula",detailed?"各人员得分 = 观测点得分之和 ÷ 观测点满分之和 × 100；任务成绩 = Σ（人员得分 × 权重%）":"任务成绩 = Σ（人员百分制分数 × 权重%）");
 }
}
