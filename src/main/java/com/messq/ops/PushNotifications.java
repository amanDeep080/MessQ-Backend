package com.messq.ops;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.FileInputStream;
import java.util.logging.Logger;
import static com.messq.ops.Store.*;
@Component
public class PushNotifications {
 final Store s;final FirebaseMessaging messaging;final Logger log=Logger.getLogger(PushNotifications.class.getName());
 public PushNotifications(Store s,@Value("${FIREBASE_CREDENTIALS_PATH:}")String path)throws Exception{
  this.s=s;if(path.isBlank()){messaging=null;return;}
  try(var stream=new FileInputStream(path)){var app=FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(stream)).build(),"messq-server");messaging=FirebaseMessaging.getInstance(app);}
 }
 @Scheduled(fixedDelay=20000,initialDelay=20000)
 public void send(){if(messaging==null)return;for(var n:s.rows("SELECT n.* FROM notifications n JOIN accounts a ON a.id=n.user_id WHERE n.pushed=FALSE AND a.active=TRUE ORDER BY n.id LIMIT 50")){
  boolean retry=false;for(var token:s.rows("SELECT token FROM device_tokens WHERE user_id=?",num(n,"user_id")))try{messaging.send(Message.builder().setToken(str(token,"token")).setNotification(Notification.builder().setTitle("MessQ update").setBody("You have a new campus dining update. Open MessQ to view it.").build()).putData("event","refresh").build());}catch(FirebaseMessagingException e){if(e.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED)s.db.update("DELETE FROM device_tokens WHERE user_id=? AND token=?",num(n,"user_id"),str(token,"token"));else retry=true;}
  if(!retry)s.db.update("UPDATE notifications SET pushed=TRUE WHERE id=?",num(n,"id"));
 }}
}
