package cn.axg.platform;
import com.fasterxml.jackson.databind.node.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import java.net.*;
import java.util.*;

@RestController @RequestMapping("/api")
public class ApiController {
 private final PlatformService service;
 private final DocumentPreview preview;
 public ApiController(PlatformService service,DocumentPreview preview){this.service=service;this.preview=preview;}
 String uid(HttpSession session){
  Object id=session.getAttribute("uid");
  if(id==null || !service.sessionValid(id.toString(),session.getAttribute("version"))) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录");
  }
  return id.toString();
 }
 @GetMapping("/health") ObjectNode health(){return Seed.o("status","UP","framework","Spring Boot","storage",service.mysql()?"mysql":"test-json");}
 @GetMapping("/accounts") ArrayNode accounts(){return service.accounts();}
 @PostMapping("/login") ObjectNode login(@RequestBody ObjectNode body,HttpServletRequest request){
  ObjectNode u=service.login(body.path("id").asText().strip(),body.path("role").asText(),body.path("password").asText());
  HttpSession existing=request.getSession(false);
  if(existing!=null)existing.invalidate();
  HttpSession session=request.getSession();
  session.setAttribute("uid",u.path("id").asText());
  session.setAttribute("version",u.path("sessionVersion").asLong());
  return Seed.o("ok",true);
 }
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) void register(@RequestBody ObjectNode body) throws IOException {service.register(body);}
 @PostMapping("/logout") ObjectNode logout(HttpSession session){session.invalidate();return Seed.o("ok",true);}
 @GetMapping("/course-catalog") ArrayNode courseCatalog(HttpSession session){return service.courseCatalog(uid(session));}
 @GetMapping("/class-invites/{token}") ObjectNode classInvite(@PathVariable String token){return service.classInvitation(token);}
 @PostMapping("/classes/{cid}/students/preview") ObjectNode enrollmentPreview(@PathVariable String cid,@RequestBody ObjectNode body,HttpSession session){return service.enrollmentPreview(uid(session),cid,body.path("rows"));}
 @GetMapping("/class-access-addresses") ArrayNode classAddresses(HttpSession session,HttpServletRequest request) throws SocketException {
  service.teacherAccount(uid(session));ArrayNode addresses=Seed.a();
  for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces()))if(network.isUp() && !network.isLoopback() && !network.isVirtual())
   for(InetAddress address:Collections.list(network.getInetAddresses()))if(address instanceof Inet4Address && address.isSiteLocalAddress())addresses.add(request.getScheme()+"://"+address.getHostAddress()+":"+request.getLocalPort());
  return addresses;
 }
 @GetMapping("/portfolio") ObjectNode portfolio(HttpSession session){return service.portfolio(uid(session));}
 @GetMapping("/projects/{pid}/discussion") ObjectNode discussion(@PathVariable String pid,HttpSession session){return service.discussion(uid(session),pid);}
 @PostMapping("/classes/{cid}/files") ObjectNode classUpload(@PathVariable String cid,@RequestParam String category,@RequestParam(defaultValue="true") boolean downloadable,@RequestParam MultipartFile file,HttpSession session) throws IOException {return service.classUpload(uid(session),cid,category,downloadable,file);}
 @GetMapping("/classes/{cid}/files/{fid}") ResponseEntity<byte[]> classFile(@PathVariable String cid,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return reference(service.classFile(uid(session),cid,fid),download,session);}
 @PostMapping("/library/files") ObjectNode personalUpload(@RequestParam MultipartFile file,@RequestParam(defaultValue="") String folderId,@RequestParam(defaultValue="文档") String category,@RequestParam(defaultValue="true") boolean downloadable,HttpSession session) throws IOException {
  return service.personalUpload(uid(session),folderId,category,downloadable,file);
 }
 @GetMapping("/library/files/{fid}") ResponseEntity<byte[]> personal(@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return bytes(service.libraryFile(uid(session),fid),download);}
 @GetMapping("/library/files/{fid}/preview") ResponseEntity<byte[]> personalPreview(@PathVariable String fid,HttpSession session) throws IOException {return preview(service.libraryFile(uid(session),fid));}
 @GetMapping("/library/activity") ResponseEntity<ObjectNode> libraryActivity(@RequestParam(defaultValue="") String projectId,@RequestParam(defaultValue="0") int offset,HttpSession session) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.libraryActivity(uid(session),projectId,offset));
 }
 @GetMapping("/tasks/{tid}/files/{fid}") ResponseEntity<byte[]> taskReference(@PathVariable String tid,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return reference(service.taskFile(uid(session),tid,fid),download,session);}
 @GetMapping("/tasks/{tid}/files/{fid}/preview") ResponseEntity<byte[]> taskPreview(@PathVariable String tid,@PathVariable String fid,HttpSession session) throws IOException {return preview(service.taskFile(uid(session),tid,fid));}
 @GetMapping("/classes/{cid}/topics/{tid}/{post}/files/{fid}") ResponseEntity<byte[]> topicFile(@PathVariable String cid,@PathVariable String tid,@PathVariable String post,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return reference(service.topicFile(uid(session),cid,tid,post,fid),download,session);}
 @GetMapping("/classes/{cid}/topics/{tid}/{post}/files/{fid}/preview") ResponseEntity<byte[]> topicPreview(@PathVariable String cid,@PathVariable String tid,@PathVariable String post,@PathVariable String fid,HttpSession session) throws IOException {return preview(service.topicFile(uid(session),cid,tid,post,fid));}
 private ResponseEntity<byte[]> reference(ObjectNode f,boolean download,HttpSession session) throws IOException {
  if(!f.path("downloadable").asBoolean(true) && service.studentAccount(uid(session))) {
   String type=f.path("type").asText(), ext=DocumentPreview.extension(f.path("name").asText());
   if(download || !(type.startsWith("image/") || type.startsWith("text/") || Set.of("md","markdown","txt","csv","json").contains(ext)))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"该资料仅供在线查看");
  }
  return bytes(f,download);
 }
 @GetMapping("/state") ObjectNode state(HttpSession session){return service.view(uid(session));}
 @GetMapping("/notifications") ArrayNode notifications(HttpSession session) throws IOException {return service.notifications(uid(session));}
 @PostMapping("/actions") ObjectNode action(@RequestBody ObjectNode body,HttpSession session) throws IOException {
  ObjectNode result=service.action(uid(session),body);
  if (body.path("op").asText().equals("changePassword")) {
   session.setAttribute("version",result.remove("sessionVersion").asLong());
  }
  return result;
 }
 @GetMapping("/profile/avatar") ResponseEntity<byte[]> avatar(HttpSession session) throws IOException {
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
      .header("X-Content-Type-Options","nosniff").body(service.avatar(uid(session)));
 }
 @PostMapping("/profile/avatar") ObjectNode uploadAvatar(@RequestParam MultipartFile file,HttpSession session) throws IOException {
  return Seed.o("avatarVersion",service.saveAvatar(uid(session),file));
 }
 @DeleteMapping("/profile/avatar") @ResponseStatus(HttpStatus.NO_CONTENT)
 void deleteAvatar(HttpSession session) throws IOException {service.removeAvatar(uid(session));}
 @PostMapping("/admin/course-import/preview") ObjectNode previewCourses(@RequestBody ObjectNode body,HttpSession session) throws IOException {
  return service.action(uid(session),Seed.o("op","previewCourses","rows",body.path("rows")));
 }
 @PostMapping("/admin/course-import") ObjectNode importCourses(@RequestBody ObjectNode body,HttpSession session) throws IOException {
  return service.action(uid(session),Seed.o("op","importCourses","rows",body.path("rows")));
 }
 @PostMapping("/admin/roster-import/preview") ObjectNode previewRoster(@RequestBody ObjectNode body,HttpSession session) throws IOException {
  return service.action(uid(session),Seed.o("op","previewRoster","rows",body.path("rows")));
 }
 @PostMapping("/admin/roster-import") ObjectNode importRoster(@RequestBody ObjectNode body,HttpSession session) throws IOException {
  return service.action(uid(session),Seed.o("op","importRoster","rows",body.path("rows")));
 }
 @PostMapping("/projects/{pid}/files") ObjectNode upload(@PathVariable String pid,@RequestParam String category,@RequestParam(defaultValue="") String folderId,@RequestParam MultipartFile file,HttpSession session) throws IOException {return service.upload(uid(session),pid,category,folderId,file);}
 ResponseEntity<byte[]> bytes(ObjectNode f,boolean download) throws IOException {String type=f.path("type").asText("application/octet-stream");try{MediaType.parseMediaType(type);}catch(Exception e){type="application/octet-stream";}return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'; style-src 'unsafe-inline'").header("Cache-Control","no-store").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.builder(download?"attachment":"inline").filename(f.path("name").asText(),StandardCharsets.UTF_8).build().toString()).body(service.fileBytes(f));}
 @GetMapping("/projects/{pid}/files/{fid}") ResponseEntity<byte[]> file(@PathVariable String pid,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return bytes(service.fileMeta(uid(session),pid,fid),download);}
 @GetMapping("/projects/{pid}/files/{fid}/preview") ResponseEntity<byte[]> projectPreview(@PathVariable String pid,@PathVariable String fid,HttpSession session) throws IOException {
  return preview(service.fileMeta(uid(session),pid,fid));
 }
 @GetMapping("/classes/{cid}/files/{fid}/preview") ResponseEntity<byte[]> classPreview(@PathVariable String cid,@PathVariable String fid,HttpSession session) throws IOException {
  return preview(service.classFile(uid(session),cid,fid));
 }
 private ResponseEntity<byte[]> preview(ObjectNode file) throws IOException {
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
   .header("X-Content-Type-Options","nosniff")
   .header("Content-Security-Policy","sandbox; default-src 'none'")
   .body(preview.pdf(file,service.fileBytes(file)));
 }
 @GetMapping("/public/{shareId}") ObjectNode pub(@PathVariable String shareId){return service.publicProject(shareId);}
 @GetMapping("/public/{shareId}/images/{fid}") ResponseEntity<byte[]> image(@PathVariable String shareId,@PathVariable String fid) throws IOException {return bytes(service.publicImage(shareId,fid),false);}
}
