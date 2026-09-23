package com.messq.ops;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import static com.messq.ops.Store.*;
@Component
public class Maintenance {
 final Store s;final DiningService dining;final InsightService insights;
 public Maintenance(Store s,DiningService dining,InsightService insights){this.s=s;this.dining=dining;this.insights=insights;}
 @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional
 public void expire(){
  s.db.update("UPDATE queue_entries SET status='EXPIRED' WHERE meal_date<? AND status IN ('WAITING','CALLED')",dining.today());
  s.db.update("UPDATE bookings SET status='NO_SHOW' WHERE status='BOOKED' AND slot_id IN (SELECT id FROM meal_slots WHERE ends_at<?)",dining.now());
  s.db.update("UPDATE bookings SET status='EXPIRED' WHERE status='WAITLIST' AND slot_id IN (SELECT id FROM meal_slots WHERE ends_at<?)",dining.now());
  s.db.update("DELETE FROM qr_passes WHERE expires_at<?",dining.now().minusDays(1));
  s.db.update("DELETE FROM command_receipts WHERE created_at<?",dining.now().minusDays(30));
 }
 @Scheduled(fixedDelay=3600000,initialDelay=10000) @Transactional
 public void forecast(){for(var mess:s.rows("SELECT id FROM messes")){long m=num(mess,"id");s.one("SELECT id FROM messes WHERE id=? FOR UPDATE",m);LocalDate day=dining.today().plusDays(1);for(String meal:List.of("BREAKFAST","LUNCH","DINNER")){var d=insights.demand(m,meal,day);if(s.count("SELECT COUNT(*) FROM forecast_history WHERE mess_id=? AND meal_date=? AND meal=?",m,day,meal)==0)s.insert("forecast_history",data("mess_id",m,"meal_date",day,"meal",meal,"predicted",d.get("expected"),"method",d.get("method")));}
  for(var past:s.rows("SELECT * FROM forecast_history WHERE mess_id=? AND meal_date<? AND actual IS NULL",m,dining.today()))s.db.update("UPDATE forecast_history SET actual=? WHERE id=?",s.count("SELECT COUNT(*) FROM attendance WHERE mess_id=? AND meal_date=? AND meal=?",m,past.get("meal_date"),past.get("meal")),num(past,"id"));}}
}
