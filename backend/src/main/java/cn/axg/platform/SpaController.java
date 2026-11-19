package cn.axg.platform;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class SpaController {
 @GetMapping({"/login","/join/**","/student/**","/teacher/**","/admin/**","/profile/**","/notifications","/share/**","/print/**","/{page:[^\\.]*}"})
 public String app(){return "forward:/index.html";}
}
