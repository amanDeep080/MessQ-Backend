package com.messq.ops;
import com.messq.auth.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import static com.messq.ops.Store.*;
@Service
public class DiningService {
 final Store s;final Access access;final Clock clock;
 public DiningService(Store s,Access access,Clock clock){this.s=s;this.access=access;this.clock=clock;}
 LocalDate today(){return LocalDate.now(clock);}
 OffsetDateTime now(){return OffsetDateTime.now(clock);}
 public String meal(Map<String,String> p){return choice(p,"meal","BREAKFAST","LUNCH","SNACKS","DINNER");}
 public Ui.Result join(Account a,long m,Map<String,String> p){
  access.eligible(a);access.open(m);String meal=meal(p);LocalDate day=today();
  if(s.count("SELECT COUNT(*) FROM attendance WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=?",a.getId(),m,day,meal)>0)throw error(409,"Meal already claimed");
  if(s.count("SELECT COUNT(*) FROM skip_meals WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=?",a.getId(),m,day,meal)>0)throw error(409,"Undo your skip declaration first");
  var old=s.rows("SELECT * FROM queue_entries WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=?",a.getId(),m,day,meal);
  if(!old.isEmpty()&&List.of("WAITING","CALLED").contains(str(old.get(0),"status")))throw error(409,"You are already in this queue");
  if(s.count("SELECT COUNT(*) FROM queue_entries WHERE mess_id=? AND meal_date=? AND meal=? AND status IN ('WAITING','CALLED')",m,day,meal)>=1000)throw error(409,"Queue is full");
  long id;
  if(old.isEmpty())id=s.insert("queue_entries",data("user_id",a.getId(),"mess_id",m,"meal_date",day,"meal",meal));
  else{id=num(old.get(0),"id");s.db.update("UPDATE queue_entries SET status='WAITING',joined_at=?,served_at=NULL WHERE id=?",now(),id);}
  return new Ui.Result("Joined queue. Token Q"+id);
 }
 public Ui.Result leave(Account a,long m,Map<String,String> p){var q=s.one("SELECT * FROM queue_entries WHERE id=?",id(p,"id"));access.owner(a,q);access.scope(m,q);if(!List.of("WAITING","CALLED").contains(str(q,"status")))throw error(409,"Queue token is no longer active");s.db.update("UPDATE queue_entries SET status='CANCELLED' WHERE id=?",num(q,"id"));return new Ui.Result("Queue entry cancelled");}
 public Ui.Result callNext(Account a,long m,Map<String,String> p){access.staff(a,"QUEUE_MANAGE");String meal=meal(p);var rows=s.rows("SELECT * FROM queue_entries WHERE mess_id=? AND meal_date=? AND meal=? AND status='WAITING' ORDER BY joined_at,id LIMIT 1",m,today(),meal);if(rows.isEmpty())throw error(409,"No students waiting");var q=rows.get(0);s.db.update("UPDATE queue_entries SET status='CALLED' WHERE id=?",num(q,"id"));notifyUser(num(q,"user_id"),"Your token Q"+num(q,"id")+" is called. Please proceed to the mess.");return new Ui.Result("Called Q"+num(q,"id"));}
 public Ui.Result book(Account a,long m,Map<String,String> p){
  access.eligible(a);access.open(m);var slot=s.one("SELECT * FROM meal_slots WHERE id=?",id(p,"id"));access.scope(m,slot);
  if(!time(slot.get("starts_at")).isAfter(now()))throw error(409,"This slot has already started");
  if(s.count("SELECT COUNT(*) FROM skip_meals WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=?",a.getId(),m,slot.get("meal_date"),slot.get("meal"))>0)throw error(409,"Undo your meal skip first");
  if(s.count("SELECT COUNT(*) FROM bookings b JOIN meal_slots s ON b.slot_id=s.id WHERE b.user_id=? AND s.mess_id=? AND s.meal_date=? AND s.meal=? AND b.status IN ('BOOKED','WAITLIST','CLAIMED')",a.getId(),m,slot.get("meal_date"),slot.get("meal"))>0)throw error(409,"You already have a booking for this meal; cancel it first");
  boolean full=s.count("SELECT COUNT(*) FROM bookings WHERE slot_id=? AND status IN ('BOOKED','CLAIMED')",num(slot,"id"))>=num(slot,"capacity");
  if(full&&!"true".equals(p.get("waitlist")))throw error(409,"Slot is full. Choose the waitlist action.");
  String status=full?"WAITLIST":"BOOKED";
  var old=s.rows("SELECT id FROM bookings WHERE user_id=? AND slot_id=?",a.getId(),num(slot,"id"));
  if(old.isEmpty())s.insert("bookings",data("user_id",a.getId(),"slot_id",num(slot,"id"),"status",status));else s.db.update("UPDATE bookings SET status=? WHERE id=?",status,num(old.get(0),"id"));
  return new Ui.Result(full?"Added to slot waitlist":"Meal slot booked");
 }
 public Ui.Result cancelBooking(Account a,long m,Map<String,String> p){
  var b=s.one("SELECT b.*,s.mess_id,s.starts_at FROM bookings b JOIN meal_slots s ON s.id=b.slot_id WHERE b.id=?",id(p,"id"));access.owner(a,b);access.scope(m,b);
  if(!List.of("BOOKED","WAITLIST").contains(str(b,"status"))||!time(b.get("starts_at")).isAfter(now()))throw error(409,"This booking cannot be cancelled");
  s.db.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",num(b,"id"));
  if("BOOKED".equals(str(b,"status"))){var wait=s.rows("SELECT b.* FROM bookings b JOIN profiles p ON p.user_id=b.user_id JOIN accounts a ON a.id=b.user_id WHERE b.slot_id=? AND b.status='WAITLIST' AND p.plan_active=TRUE AND a.active=TRUE AND p.mess_id=? ORDER BY b.id LIMIT 1",num(b,"slot_id"),m);if(!wait.isEmpty()){s.db.update("UPDATE bookings SET status='BOOKED' WHERE id=?",num(wait.get(0),"id"));notifyUser(num(wait.get(0),"user_id"),"A place opened up: your waitlisted meal slot is now booked.");}}
  return new Ui.Result("Booking cancelled");
 }
 public Ui.Result pass(Account a,long m,Map<String,String> p){
  access.eligible(a);String meal;LocalDate day;Long booking=null;OffsetDateTime expiry=now().plusMinutes(10);
  if(p.containsKey("id")&&!p.get("id").isBlank()){
   var b=s.one("SELECT b.*,s.mess_id,s.meal_date,s.meal,s.starts_at,s.ends_at FROM bookings b JOIN meal_slots s ON s.id=b.slot_id WHERE b.id=?",id(p,"id"));access.owner(a,b);access.scope(m,b);
   if(!"BOOKED".equals(str(b,"status")))throw error(409,"A confirmed unused booking is required");
   if(now().isBefore(time(b.get("starts_at")).minusMinutes(15))||now().isAfter(time(b.get("ends_at"))))throw error(409,"Passes are available from 15 minutes before your slot until it ends");
   meal=str(b,"meal");day=LocalDate.parse(str(b,"meal_date"));booking=num(b,"id");if(time(b.get("ends_at")).isBefore(expiry))expiry=time(b.get("ends_at"));
  }else{meal=meal(p);day=today();if(s.count("SELECT COUNT(*) FROM queue_entries WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=? AND status='CALLED'",a.getId(),m,day,meal)==0)throw error(409,"Wait until your queue token is called");}
  byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  s.insert("qr_passes",data("token_hash",hash(token),"user_id",a.getId(),"mess_id",m,"meal_date",day,"meal",meal,"booking_id",booking,"expires_at",expiry));
  return new Ui.Result("Show this pass to staff. Expires in at most 10 minutes.",token,null);
 }
 public Ui.Result redeem(Account a,long m,Map<String,String> p){
  access.staff(a,"QR_SCAN");String raw=text(p,"token",200);var q=s.one("SELECT * FROM qr_passes WHERE token_hash=?",hash(raw));access.scope(m,q);
  if(bool(q,"redeemed")||!time(q.get("expires_at")).isAfter(now()))throw error(409,"Pass was already used or has expired");
  var owner=s.one("SELECT p.*,a.active FROM profiles p JOIN accounts a ON a.id=p.user_id WHERE p.user_id=?",num(q,"user_id"));
  if(!bool(owner,"active")||!bool(owner,"plan_active")||num(owner,"mess_id")!=m)throw error(403,"Student is no longer eligible");
  if(q.get("booking_id")!=null){var b=s.one("SELECT status FROM bookings WHERE id=?",num(q,"booking_id"));if(!"BOOKED".equals(str(b,"status")))throw error(409,"Booking is not active");}
  if(s.count("SELECT COUNT(*) FROM attendance WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=?",num(q,"user_id"),m,q.get("meal_date"),q.get("meal"))>0)throw error(409,"Meal already claimed");
  s.insert("attendance",data("user_id",num(q,"user_id"),"mess_id",m,"meal_date",q.get("meal_date"),"meal",q.get("meal")));
  s.db.update("UPDATE qr_passes SET redeemed=TRUE WHERE id=?",num(q,"id"));
  if(q.get("booking_id")!=null)s.db.update("UPDATE bookings SET status='CLAIMED' WHERE id=?",num(q,"booking_id"));
  s.db.update("UPDATE queue_entries SET status='SERVED',served_at=? WHERE user_id=? AND mess_id=? AND meal_date=? AND meal=? AND status IN ('WAITING','CALLED')",now(),num(q,"user_id"),m,q.get("meal_date"),q.get("meal"));
  notifyUser(num(q,"user_id"),"Meal checked in. Enjoy your food!");return new Ui.Result("Valid meal claim. Welcome!");
 }
 public Ui.Result skip(Account a,long m,Map<String,String> p){access.eligible(a);LocalDate day=date(p,"date");String meal=meal(p);LocalTime cutoff=switch(meal){case "BREAKFAST"->LocalTime.of(7,0);case "LUNCH"->LocalTime.of(12,0);case "SNACKS"->LocalTime.of(16,0);default->LocalTime.of(18,30);};if(!day.atTime(cutoff).atZone(clock.getZone()).toInstant().isAfter(clock.instant()))throw error(409,"Skip cutoff has passed");
  if(s.count("SELECT COUNT(*) FROM bookings b JOIN meal_slots ms ON ms.id=b.slot_id WHERE b.user_id=? AND ms.mess_id=? AND ms.meal_date=? AND ms.meal=? AND b.status IN ('BOOKED','WAITLIST','CLAIMED')",a.getId(),m,day,meal)>0)throw error(409,"Cancel your booking before skipping");
  s.insert("skip_meals",data("user_id",a.getId(),"mess_id",m,"meal_date",day,"meal",meal));
  String ref="skip:"+m+":"+day+":"+meal;if(s.count("SELECT COUNT(*) FROM points_ledger WHERE user_id=? AND reference=?",a.getId(),ref)==0)s.insert("points_ledger",data("user_id",a.getId(),"points",5,"reason","Planned a skipped meal","reference",ref));
  return new Ui.Result("Meal skipped. Thank you for planning ahead.");}
 public Ui.Result undoSkip(Account a,long m,Map<String,String> p){var r=s.one("SELECT * FROM skip_meals WHERE id=?",id(p,"id"));access.owner(a,r);access.scope(m,r);s.db.update("DELETE FROM skip_meals WHERE id=?",num(r,"id"));s.db.update("DELETE FROM points_ledger WHERE user_id=? AND reference=?",a.getId(),"skip:"+m+":"+r.get("meal_date")+":"+r.get("meal"));return new Ui.Result("Skip declaration removed");}
 public void notifyUser(long user,String text){s.insert("notifications",data("user_id",user,"message",text));}
}
