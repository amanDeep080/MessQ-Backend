package com.messq.ops;
import com.messq.auth.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.event.*;
import java.util.concurrent.ConcurrentHashMap;
@Configuration @EnableWebSocket
public class LiveUpdates extends TextWebSocketHandler implements WebSocketConfigurer {
 final ConcurrentHashMap<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
 final AuthService auth;final Access access;
 public LiveUpdates(AuthService auth,Access access){this.auth=auth;this.access=access;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){registry.addHandler(this,"/api/live");}
 @Override public void afterConnectionEstablished(WebSocketSession session)throws Exception{
  if(!(session.getPrincipal() instanceof JwtAuthenticationToken token)){session.close(CloseStatus.NOT_ACCEPTABLE);return;}
  auth.current(token.getToken());sessions.put(session.getId(),session);session.sendMessage(new TextMessage("{\"event\":\"connected\"}"));
 }
 @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){sessions.remove(session.getId());}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void changed(Operations.Changed event){
  for(WebSocketSession session:sessions.values())try{
   var token=(JwtAuthenticationToken)session.getPrincipal();if(token==null||token.getToken().getExpiresAt()==null||token.getToken().getExpiresAt().isBefore(java.time.Instant.now())){session.close();continue;}
   var user=auth.current(token.getToken());if(access.mess(user)==event.mess()&&session.isOpen())synchronized(session){session.sendMessage(new TextMessage("{\"event\":\"refresh\"}"));}
  }catch(Exception e){sessions.remove(session.getId());try{session.close();}catch(Exception ignored){}}
 }
}
