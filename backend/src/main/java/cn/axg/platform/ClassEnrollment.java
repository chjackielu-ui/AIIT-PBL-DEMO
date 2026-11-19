package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static cn.axg.platform.Seed.*;
import static cn.axg.platform.PlatformService.*;

final class ClassEnrollment {
 private ClassEnrollment() {}
 static ObjectNode managedClass(PlatformService s,JsonNode u,String cid) {
  s.only(u,"teacher");ObjectNode tc=s.find("teachingClasses",cid);
  require(s.contains(tc.path("teacherIds"),str(u,"id")),"只能管理自己的教学班学生");return tc;
 }
 static ObjectNode invitedClass(PlatformService s,String token) {
  for(JsonNode tc:s.list("teachingClasses"))if(!token.isBlank() && str(tc,"inviteToken").equals(token))return (ObjectNode)tc;
  throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"入班链接不存在或已失效，请向教师获取二维码");
 }
 static ObjectNode invitation(PlatformService s,String token) {
  ObjectNode tc=invitedClass(s,token);
  return o("classId",str(tc,"id"),"name",str(tc,"name"),"courseName",str(s.find("courses",str(tc,"courseId")),"name"),"academicYear",str(tc,"academicYear"),"semester",str(tc,"semester"));
 }
 static ObjectNode preview(PlatformService s,JsonNode u,String cid,JsonNode source) {
  managedClass(s,u,cid);require(source.isArray() && !source.isEmpty() && source.size()<=2000,"请上传含 1–2000 名学生的表格");
  ArrayNode rows=a();Set<String> numbers=new HashSet<>(),sequences=new HashSet<>();int line=1,errors=0;
  for(JsonNode raw:source){
   line++;String number=str(raw,"number").trim(),name=str(raw,"name").trim(),className=str(raw,"className").trim(),sequence=str(raw,"sequence").trim(),contact=str(raw,"contact").trim();
   ObjectNode row=o("row",line,"sequence",sequence,"name",name,"number",number,"className",className,"contact",contact,"studentId","","status","新建账号并入班","error","");
   try {
    require(sequence.matches("[1-9][0-9]{0,6}") && sequences.add(sequence),"序号需为不重复的正整数");
    require(!name.isBlank() && name.length()<=100 && !className.isBlank() && className.length()<=100,"请填写姓名和班级（均不超过 100 字）");
    require(!number.isBlank() && number.length()<=50 && numbers.add(number),"学号不能为空、超过 50 字或在表格内重复");
    if(s.mysql())require(number.matches("[0-9]{10}"),"学号应为 10 位数字");
    require(contact.length()<=100,"联系方式不能超过 100 字");
    ObjectNode existing=null;
    for(JsonNode candidate:s.list("users"))if(str(candidate,"number").equals(number)){
     require(existing==null,"系统存在重复学号，请联系管理员处理");existing=(ObjectNode)candidate;
    }
    if(existing!=null){
     require(str(existing,"role").equals("student") && existing.path("enabled").asBoolean(),"该学号的学生账号不可用");
     require(str(existing,"name").equals(name),"学号对应的姓名与系统不一致，请核对");
     ObjectNode adminClass=s.findOrNull("classes",str(existing,"classId"));
     require(adminClass==null || str(adminClass,"name").equals(className),"学号对应的班级与系统不一致，请核对");
     row.put("studentId",str(existing,"id"));row.put("status",s.enrolled(str(existing,"id"),cid)?"已在本班，跳过":"加入本班");
    }
   }catch(org.springframework.web.server.ResponseStatusException e){row.put("error",e.getReason());errors++;}
   rows.add(row);
  }
  return o("rows",rows,"errors",errors);
 }
 static ObjectNode apply(PlatformService s,JsonNode u,String cid,JsonNode source) {
  ObjectNode check=preview(s,u,cid,source);require(check.path("errors").asInt()==0,"表格中有错误，请先核对预览后重新上传");
  int added=0,created=0,skipped=0;
  for(JsonNode row:check.path("rows")){
   String sid=str(row,"studentId");
   if(sid.isBlank()){
    String classId="";for(JsonNode c:s.list("classes"))if(str(c,"name").equals(str(row,"className")))classId=str(c,"id");
    if(!s.mysql() && classId.isBlank()){classId=id();s.list("classes").add(o("id",classId,"name",str(row,"className")));}
    sid=id();s.list("users").add(o("id",sid,"name",str(row,"name"),"number",str(row,"number"),"classId",classId,"contact",str(row,"contact"),"role","student","enabled",true));created++;
   }
   if(s.enrolled(sid,cid)){skipped++;continue;}
   if(!str(row,"contact").isBlank())s.find("users",sid).put("contact",str(row,"contact"));
   s.list("enrollments").add(o("studentId",sid,"classId",cid));added++;
  }
  return o("ok",true,"added",added,"created",created,"skipped",skipped);
 }
}
