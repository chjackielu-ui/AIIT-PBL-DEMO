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
 public ApiController(PlatformService service){this.service=service;}
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
 @PostMapping("/classes/{cid}/files") ObjectNode classUpload(@PathVariable String cid,@RequestParam String category,@RequestParam MultipartFile file,HttpSession session) throws IOException {return service.classUpload(uid(session),cid,category,file);}
 @GetMapping("/classes/{cid}/files/{fid}") ResponseEntity<byte[]> classFile(@PathVariable String cid,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return bytes(service.classFile(uid(session),cid,fid),download);}
 @GetMapping("/state") ObjectNode state(HttpSession session){return service.view(uid(session));}
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
 @PostMapping("/projects/{pid}/files") ObjectNode upload(@PathVariable String pid,@RequestParam String category,@RequestParam MultipartFile file,HttpSession session) throws IOException {return service.upload(uid(session),pid,category,file);}
 ResponseEntity<byte[]> bytes(ObjectNode f,boolean download) throws IOException {String type=f.path("type").asText("application/octet-stream");try{MediaType.parseMediaType(type);}catch(Exception e){type="application/octet-stream";}return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'; style-src 'unsafe-inline'").header("Cache-Control","no-store").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.builder(download?"attachment":"inline").filename(f.path("name").asText(),StandardCharsets.UTF_8).build().toString()).body(service.fileBytes(f));}
 @GetMapping("/projects/{pid}/files/{fid}") ResponseEntity<byte[]> file(@PathVariable String pid,@PathVariable String fid,@RequestParam(defaultValue="false") boolean download,HttpSession session) throws IOException {return bytes(service.fileMeta(uid(session),pid,fid),download);}
 @GetMapping("/public/{shareId}") ObjectNode pub(@PathVariable String shareId){return service.publicProject(shareId);}
 @GetMapping("/public/{shareId}/images/{fid}") ResponseEntity<byte[]> image(@PathVariable String shareId,@PathVariable String fid) throws IOException {return bytes(service.publicImage(shareId,fid),false);}
}
