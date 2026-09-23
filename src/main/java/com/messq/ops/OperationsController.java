package com.messq.ops;
import com.messq.auth.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.messq.ops.Store.*;
@RestController @RequestMapping("/api/workspace")
public class OperationsController {
 final AuthService auth;final Pages pages;final Operations ops;final InsightService ai;final Access access;final Store s;
 public OperationsController(AuthService auth,Pages pages,Operations ops,InsightService ai,Access access,Store s){this.auth=auth;this.pages=pages;this.ops=ops;this.ai=ai;this.access=access;this.s=s;}
 @GetMapping public Ui.Workspace workspace(@AuthenticationPrincipal Jwt jwt){return pages.workspace(auth.current(jwt));}
 @GetMapping("/pages/{module}") public Ui.Page page(@AuthenticationPrincipal Jwt jwt,@PathVariable String module){return pages.page(auth.current(jwt),module);}
 @PostMapping("/actions/{operation}") public Ui.Result action(@AuthenticationPrincipal Jwt jwt,@PathVariable String operation,@RequestBody Ui.Command command){
  var account=auth.current(jwt);if("ai.ask".equals(operation)){if(command==null||command.values()==null)throw error(400,"Invalid request");return ai.ask(account,access.mess(account),command.values());}return ops.execute(account,operation,command);
 }
 @PostMapping("/device") public Map<String,String> device(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,String> body){var a=auth.current(jwt);String token=text(body,"token",1000);s.db.update("DELETE FROM device_tokens WHERE token=? AND user_id<>?",token,a.getId());if(s.count("SELECT COUNT(*) FROM device_tokens WHERE user_id=? AND token=?",a.getId(),token)==0)s.db.update("INSERT INTO device_tokens(user_id,token) VALUES (?,?)",a.getId(),token);return Map.of("status","registered");}
 @DeleteMapping("/device") public Map<String,String> removeDevice(@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,String> body){var a=auth.current(jwt);s.db.update("DELETE FROM device_tokens WHERE user_id=? AND token=?",a.getId(),text(body,"token",1000));return Map.of("status","removed");}
}
