package com.messq.ops;
import com.messq.auth.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static com.messq.ops.Store.*;
@Service
public class Operations {
 final Store s;final Access access;final DiningService dining;final CommerceService commerce;final ManagementService management;final InsightService insights;final ObjectMapper json;final ApplicationEventPublisher events;
 public record Changed(long mess){}
 public Operations(Store s,Access access,DiningService dining,CommerceService commerce,ManagementService management,InsightService insights,ObjectMapper json,ApplicationEventPublisher events){this.s=s;this.access=access;this.dining=dining;this.commerce=commerce;this.management=management;this.insights=insights;this.json=json;this.events=events;}
 @Transactional
 public Ui.Result execute(Account a,String op,Ui.Command command){
  if(command==null||command.values()==null||command.requestId()==null||!command.requestId().matches("[A-Za-z0-9-]{8,80}")||command.values().size()>25||command.values().values().stream().anyMatch(Objects::isNull))throw error(400,"A request ID and valid values are required");
  long mess=num(access.profile(a),"mess_id");
  if(mess==0&&!Set.of("profile.save","mess.create","user.assign").contains(op))throw error(409,"Your admin must assign your mess first");
  if(mess!=0)s.one("SELECT id FROM messes WHERE id=? FOR UPDATE",mess); // Capacity changes within a mess serialize across server instances.
  var current=s.one("SELECT id,active,role FROM accounts WHERE id=? FOR UPDATE",a.getId());
  if(!bool(current,"active")||!a.getRole().name().equals(str(current,"role")))throw error(401,"Your session is no longer valid");
  if(num(access.profile(a),"mess_id")!=mess)throw error(409,"Your mess assignment changed. Refresh and try again.");
  String fingerprint;try{fingerprint=hash(op+json.writeValueAsString(new TreeMap<>(command.values())));}catch(Exception e){throw error(400,"Invalid request");}
  var receipts=s.rows("SELECT * FROM command_receipts WHERE user_id=? AND request_id=?",a.getId(),command.requestId());
  if(!receipts.isEmpty()){if(!fingerprint.equals(str(receipts.get(0),"payload_hash")))throw error(409,"Request ID was reused for a different operation");try{return json.readValue(str(receipts.get(0),"response_json"),Ui.Result.class);}catch(Exception e){throw new IllegalStateException(e);}}
  Map<String,String> p=command.values();Ui.Result result=switch(op){
   case "queue.join"->dining.join(a,mess,p);case "queue.leave"->dining.leave(a,mess,p);case "queue.next"->dining.callNext(a,mess,p);
   case "booking.create"->dining.book(a,mess,p);case "booking.cancel"->dining.cancelBooking(a,mess,p);case "pass.issue"->dining.pass(a,mess,p);case "pass.redeem"->dining.redeem(a,mess,p);
   case "skip.create"->dining.skip(a,mess,p);case "skip.undo"->dining.undoSkip(a,mess,p);
   case "wallet.topup"->commerce.topup(a,mess,p,command.requestId());case "order.checkout"->commerce.checkout(a,mess,p);case "order.state"->commerce.status(a,mess,p);
   default->management.apply(op,a,mess,p);
  };
  try{s.db.update("INSERT INTO command_receipts(user_id,request_id,payload_hash,response_json) VALUES (?,?,?,?)",a.getId(),command.requestId(),fingerprint,json.writeValueAsString(result));}catch(java.io.IOException e){throw new IllegalStateException(e);}
  s.insert("audit_logs",data("actor_id",a.getId(),"mess_id",mess==0?null:mess,"action",op,"detail","Request "+command.requestId())); // Do not log submitted passwords, QR tokens, or AI text.
  if(mess!=0)events.publishEvent(new Changed(mess));return result;
 }
}
