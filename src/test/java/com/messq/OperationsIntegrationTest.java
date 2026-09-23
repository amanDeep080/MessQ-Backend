package com.messq;
import com.messq.auth.*;
import com.messq.ops.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.messq.ops.Store.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:operations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.jwt-secret=test-only-secret-with-at-least-32-bytes-never-deploy","app.mock-payments=true"})
class OperationsIntegrationTest {
 @Autowired Store s;@Autowired Operations ops;@Autowired AccountRepository accounts;@Autowired PasswordEncoder encoder;@Autowired Pages pages;@Autowired Clock clock;@Autowired InsightService insights;
 Account student,second,admin,staff;long mess;
 @BeforeEach void prepare(){mess=s.insert("messes",data("name","Test "+UUID.randomUUID(),"seats",50));student=account(Role.STUDENT);second=account(Role.STUDENT);admin=account(Role.ADMIN);staff=account(Role.STAFF);}
 Account account(Role role){Account a=accounts.saveAndFlush(new Account("Test "+role,UUID.randomUUID()+"@example.com","unused-test-hash",role));s.db.update("INSERT INTO profiles(user_id,mess_id,plan_active,permissions) VALUES (?,?,TRUE,?)",a.getId(),mess,"QR_SCAN,QUEUE_MANAGE,ORDER_MANAGE,INVENTORY_MANAGE,MENU_MANAGE,COMPLAINT_MANAGE");return a;}
 Ui.Result act(Account a,String operation,Map<String,String> values){return ops.execute(a,operation,new Ui.Command(UUID.randomUUID().toString(),values));}
 long slot(int capacity){var now=OffsetDateTime.now(clock);return s.insert("meal_slots",data("mess_id",mess,"meal","DINNER","meal_date",LocalDate.now(clock),"starts_at",now.plusMinutes(2),"ends_at",now.plusMinutes(30),"capacity",capacity));}
 long food(){return s.insert("menu_items",data("mess_id",mess,"name","Paneer roll","meal","CANTEEN","meal_date",LocalDate.now(clock),"price_cents",8000));}
 @Test void finalSeatCannotBeDoubleBooked()throws Exception{
  long slot=slot(1);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{List<Future<Boolean>> results=new ArrayList<>();for(Account a:List.of(student,second))results.add(pool.submit(()->{start.await();try{act(a,"booking.create",Map.of("id",""+slot));return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}}));start.countDown();int successes=0;for(var r:results)if(r.get(10,TimeUnit.SECONDS))successes++;assertEquals(1,successes);assertEquals(1,s.count("SELECT COUNT(*) FROM bookings WHERE slot_id=? AND status='BOOKED'",slot));}finally{pool.shutdownNow();}
 }
 @Test void cancellationPromotesWaitlist(){long slot=slot(1);act(student,"booking.create",Map.of("id",""+slot));act(second,"booking.create",Map.of("id",""+slot,"waitlist","true"));long id=s.count("SELECT id FROM bookings WHERE user_id=? AND slot_id=?",student.getId(),slot);act(student,"booking.cancel",Map.of("id",""+id));assertEquals("BOOKED",str(s.one("SELECT status FROM bookings WHERE user_id=? AND slot_id=?",second.getId(),slot),"status"));}
 @Test void qrIsSingleUseAndCannotCrossMesses(){long slot=slot(2);act(student,"booking.create",Map.of("id",""+slot));long id=s.count("SELECT id FROM bookings WHERE user_id=? AND slot_id=?",student.getId(),slot);String token=act(student,"pass.issue",Map.of("id",""+id)).qr();
  long other=s.insert("messes",data("name","Other mess","seats",10));s.db.update("UPDATE profiles SET mess_id=? WHERE user_id=?",other,staff.getId());assertEquals(403,assertThrows(ResponseStatusException.class,()->act(staff,"pass.redeem",Map.of("token",token))).getStatusCode().value());s.db.update("UPDATE profiles SET mess_id=? WHERE user_id=?",mess,staff.getId());act(staff,"pass.redeem",Map.of("token",token));assertEquals(409,assertThrows(ResponseStatusException.class,()->act(staff,"pass.redeem",Map.of("token",token))).getStatusCode().value());assertEquals(1,s.count("SELECT COUNT(*) FROM attendance WHERE user_id=?",student.getId()));}
 @Test void expiredPassIsRejected(){long slot=slot(1);act(student,"booking.create",Map.of("id",""+slot));long id=s.count("SELECT id FROM bookings WHERE user_id=?",student.getId());String token=act(student,"pass.issue",Map.of("id",""+id)).qr();s.db.update("UPDATE qr_passes SET expires_at=? WHERE token_hash=?",OffsetDateTime.now(clock).minusMinutes(1),hash(token));assertThrows(ResponseStatusException.class,()->act(staff,"pass.redeem",Map.of("token",token)));}
 @Test void orderRetriesAndRefundsNeverDuplicateMoney(){long food=food();act(student,"wallet.topup",Map.of("rupees","100"));String key=UUID.randomUUID().toString();var cmd=new Ui.Command(key,Map.of("items","[{\"id\":"+food+",\"quantity\":1}]"));var first=ops.execute(student,"order.checkout",cmd);assertEquals(first,ops.execute(student,"order.checkout",cmd));assertEquals(2000,s.count("SELECT balance_cents FROM wallets WHERE user_id=?",student.getId()));assertEquals(1,s.count("SELECT COUNT(*) FROM orders WHERE user_id=?",student.getId()));long order=s.count("SELECT id FROM orders WHERE user_id=?",student.getId());act(student,"order.state",Map.of("id",""+order,"status","CANCELLED"));assertEquals(10000,s.count("SELECT balance_cents FROM wallets WHERE user_id=?",student.getId()));assertThrows(ResponseStatusException.class,()->act(student,"order.state",Map.of("id",""+order,"status","CANCELLED")));assertEquals(10000,s.count("SELECT balance_cents FROM wallets WHERE user_id=?",student.getId()));}
 @Test void orderStateAndOwnershipAreEnforced(){long food=food();act(student,"wallet.topup",Map.of("rupees","100"));act(student,"order.checkout",Map.of("items","[{\"id\":"+food+",\"quantity\":1}]"));long order=s.count("SELECT id FROM orders WHERE user_id=?",student.getId());assertThrows(ResponseStatusException.class,()->act(second,"order.state",Map.of("id",""+order,"status","CANCELLED")));assertThrows(ResponseStatusException.class,()->act(staff,"order.state",Map.of("id",""+order,"status","READY")));for(String state:List.of("ACCEPTED","PREPARING","READY","COLLECTED"))act(staff,"order.state",Map.of("id",""+order,"status",state));assertEquals("COLLECTED",str(s.one("SELECT status FROM orders WHERE id=?",order),"status"));}
 @Test void inventoryCannotGoNegativeAndRollbackKeepsLedgerClean(){long item=s.insert("inventory",data("mess_id",mess,"name","Rice","unit","kg","quantity",3,"minimum",1));assertThrows(ResponseStatusException.class,()->act(staff,"inventory.adjust",Map.of("id",""+item,"kind","CONSUME","quantity","4","reason","Cooking")));assertEquals(0,s.count("SELECT COUNT(*) FROM stock_transactions WHERE item_id=?",item));}
 @Test void staffPermissionsAndStudentAdminIsolation(){s.db.update("UPDATE profiles SET permissions='' WHERE user_id=?",staff.getId());assertEquals(403,assertThrows(ResponseStatusException.class,()->act(staff,"queue.next",Map.of("meal","DINNER"))).getStatusCode().value());assertEquals(403,assertThrows(ResponseStatusException.class,()->act(student,"mess.status",Map.of("status","CLOSED"))).getStatusCode().value());}
 @Test void allRolePagesRenderWithEmptyAndPopulatedData(){food();slot(2);for(Account a:List.of(student,staff,admin))for(Ui.Module module:pages.workspace(a).modules())assertNotNull(pages.page(a,module.key()).title(),module.key());}
 @Test void requestIdCannotBeReusedForDifferentPayload(){String key=UUID.randomUUID().toString();ops.execute(student,"wallet.topup",new Ui.Command(key,Map.of("rupees","100")));assertEquals(409,assertThrows(ResponseStatusException.class,()->ops.execute(student,"wallet.topup",new Ui.Command(key,Map.of("rupees","200")))).getStatusCode().value());}
 @Test void forecastColdStartIsLabelledAndBounded(){var d=insights.demand(mess,"DINNER",LocalDate.now(clock).plusDays(1));assertTrue(str(d,"method").contains("Cold-start"));assertTrue(num(d,"expected")<=2);}

 @Test void deactivatedAccountCannotMutateWithPreviouslyLoadedAccount(){s.db.update("UPDATE accounts SET active=FALSE WHERE id=?",student.getId());assertEquals(401,assertThrows(ResponseStatusException.class,()->act(student,"wallet.topup",Map.of("rupees","100"))).getStatusCode().value());assertEquals(0,s.count("SELECT COUNT(*) FROM wallets WHERE user_id=?",student.getId()));}
 @Test void voteAndEventCapacityAreEnforced(){
  long poll=s.insert("polls",data("mess_id",mess,"question","Menu preference?","options_text","Rice|Roti","closes_at",OffsetDateTime.now(clock).plusDays(1)));
  act(student,"poll.vote",Map.of("id",""+poll,"choice","1"));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->act(student,"poll.vote",Map.of("id",""+poll,"choice","0")));
  assertEquals(1,s.count("SELECT COUNT(*) FROM votes WHERE poll_id=?",poll));
  long event=s.insert("events",data("mess_id",mess,"title","Campus dinner","starts_at",OffsetDateTime.now(clock).plusDays(1),"capacity",1));
  act(student,"event.book",Map.of("id",""+event));assertEquals(409,assertThrows(ResponseStatusException.class,()->act(second,"event.book",Map.of("id",""+event))).getStatusCode().value());
  act(student,"event.cancel",Map.of("id",""+event));act(second,"event.book",Map.of("id",""+event));
 }
 @Test void menuDietTagsPersistAndCrowdColdStartDoesNotInventTimes(){
  act(staff,"menu.save",Map.of("name","Jain thali","meal","LUNCH","date",LocalDate.now(clock).toString(),"price","0","diet","JAIN","calories","500","protein","20"));
  assertEquals("JAIN",str(s.one("SELECT dietary_tag FROM menu_items WHERE mess_id=?",mess),"dietary_tag"));
  assertEquals("More history needed",str(insights.crowd(mess).get(0),"label"));
 }
}
