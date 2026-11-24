package cn.axg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static cn.axg.platform.PlatformService.*;
import static cn.axg.platform.Seed.*;

final class CourseLibrary {
    static ObjectNode create(String role) {
        ObjectNode lib=o("files",a(),"folders",a());
        if (role.equals("teacher")) {
            for(String name:List.of("教学资料","实践案例","作业与评价")) {
                String parent=id();
                lib.withArray("folders").add(o("id",parent,"name",name,"parentId",""));
                for(String child:switch(name) {
                    case "教学资料" -> List.of("课程大纲","章节讲义");
                    case "实践案例" -> List.of("参考项目","实验指导");
                    default -> List.of("提交模板","评分标准");
                }) lib.withArray("folders").add(o("id",id(),"name",child,"parentId",parent));
            }
        }
        return lib;
    }

    static void taskReferences(PlatformService s,ObjectNode u,ObjectNode task) {
        ObjectNode old=str(task,"id").isBlank()?null:s.find("tasks",str(task,"id"));
        // Rebuild metadata from trusted records on every write.
        ArrayNode refs=a();
        JsonNode requested=task.remove("references");
        if(requested==null && old!=null) requested=old.path("references");
        if(requested!=null) for(JsonNode entry:requested) {
            String fid=str(entry,"id");
            JsonNode saved=old==null?null:old.path("references").valueStream().filter(f->str(f,"id").equals(fid)).findFirst().orElse(null);
            ObjectNode file=saved==null?s.libraryFile(str(u,"id"),fid):(ObjectNode)saved.deepCopy();
            file.put("downloadable",entry.path("downloadable").asBoolean(file.path("downloadable").asBoolean(true)));
            refs.add(file);
        }
        task.set("references",refs);
    }

    static ObjectNode change(PlatformService s,ObjectNode u,ObjectNode b) {
        String uid=str(u,"id"), op=str(b,"op");
        ObjectNode lib=s.library(uid);
        if(op.equals("personalFolder")) {
            require(Set.of("createFolder","renameFolder","deleteFolder","moveFile").contains(str(b,"action")),"目录操作无效");
            return ProjectFolders.change(s,lib,u,b.deepCopy().put("op",str(b,"action")));
        }
        if(op.equals("personalEdit")) {
            ObjectNode file=s.nested(lib,"files",str(b,"fileId"));
            String name=str(b,"name").strip();
            require(!name.isEmpty() && name.length()<=255,"请输入有效文件名");
            if(!file.has("sourceExtension")) file.put("sourceExtension",DocumentPreview.extension(str(file,"name")));
            String category = str(b,"category");
            boolean downloadable = b.path("downloadable").asBoolean(true);
            if (!str(file, "name").equals(name) || !str(file, "category").equals(category)
                || file.path("downloadable").asBoolean(true) != downloadable) {
                file.put("name",name).put("category",category).put("downloadable",downloadable);
                LibraryActivity.record(lib,u,"编辑资料",file,str(file,"folderId"));
            }
            return o("ok",true);
        }
        if(op.equals("distributeFiles")) {
            s.only(u,"teacher"); s.permit(u,"courseFiles");
            require(!b.path("classIds").isEmpty() && !b.path("fileIds").isEmpty(),"请选择资料和教学班");
            for(JsonNode cid:b.path("classIds")) {
                ObjectNode tc=s.accessibleClass(uid,cid.asText());
                for(JsonNode fid:b.path("fileIds")) {
                    ObjectNode file=s.libraryFile(uid,fid.asText());
                    List<String> path=new ArrayList<>();
                    String folder=str(file,"folderId");
                    while(!folder.isEmpty()) {
                        ObjectNode parent=s.nested(lib,"folders",folder);
                        path.add(0,str(parent,"name"));
                        folder=str(parent,"parentId");
                    }
                    file.put("directory",String.join(" / ",path));
                    ArrayNode files=tc.withArray("resources");
                    boolean exists=false;
                    for(JsonNode row:files) if(str(row,"id").equals(fid.asText())) {((ObjectNode)row).setAll(file);exists=true;break;}
                    if(!exists) files.add(file);
                }
            }
            return o("ok",true);
        }
        ObjectNode tc=s.accessibleClass(uid,str(b,"classId"));
        if(op.equals("classDownload")) {
            s.only(u,"teacher");s.permit(u,"courseFiles");
            ObjectNode f=str(tc.path("syllabus"),"id").equals(str(b,"fileId")) && tc.hasNonNull("syllabus")
                ? (ObjectNode)tc.get("syllabus") : s.nested(tc,"resources",str(b,"fileId"));
            f.put("downloadable",b.path("downloadable").asBoolean(true));
            return o("ok",true);
        }
        s.permit(u,"discussion");
        boolean topic=op.equals("publishTopic");
        require(topic || op.equals("replyTopic"),"无效的话题操作");
        if(topic) s.only(u,"teacher");
        ArrayNode files=a();
        require(b.path("fileIds").size()<=10,"最多添加十份附件");
        for(JsonNode fid:b.path("fileIds")) {
            String source=b.path("sourceProjects").path(fid.asText()).asText("");
            files.add(s.selectedLibraryFile(uid,fid.asText(),source));
        }
        String body=str(b,"body").strip();
        require(body.length()<=30000 && (topic || !body.isEmpty() || !files.isEmpty()),"请输入正文或选择附件，正文最多三万字");
        ObjectNode post=o("id",id(),"userId",uid,"body",body,"time",now(),"files",files);
        if(topic) {
            String title=str(b,"title").strip();
            require(!title.isEmpty() && title.length()<=120,"请填写话题标题（最多120字）");
            post.put("title",title).putArray("replies");
            tc.withArray("topics").insert(0,post);
        } else s.nested(tc,"topics",str(b,"topicId")).withArray("replies").add(post);
        return post;
    }
}
