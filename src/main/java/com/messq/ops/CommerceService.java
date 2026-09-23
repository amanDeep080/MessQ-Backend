package com.messq.ops;
import com.messq.auth.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static com.messq.ops.Store.*;
@Service
public class CommerceService {
 final Store s;final Access access;final DiningService dining;final ObjectMapper json;final boolean mockPayments;
 public CommerceService(Store s,Access access,DiningService dining,ObjectMapper json,@Value("${app.mock-payments:false}")boolean mockPayments){this.s=s;this.access=access;this.dining=dining;this.json=json;this.mockPayments=mockPayments;}
 void wallet(long user){if(s.count("SELECT COUNT(*) FROM wallets WHERE user_id=?",user)==0)s.db.update("INSERT INTO wallets(user_id,balance_cents) VALUES (?,0)",user);s.one("SELECT * FROM wallets WHERE user_id=? FOR UPDATE",user);}
 public Ui.Result topup(Account a,long m,Map<String,String> p,String requestId){access.student(a);if(!mockPayments)throw error(409,"Mock payments are disabled. A real payment provider must verify top-ups server-side.");int rupees=integer(p,"rupees",1,1000);wallet(a.getId());long cents=rupees*100L;s.db.update("UPDATE wallets SET balance_cents=balance_cents+? WHERE user_id=?",cents,a.getId());s.insert("wallet_transactions",data("user_id",a.getId(),"amount_cents",cents,"kind","MOCK_TOPUP","reference","topup:"+requestId));return new Ui.Result("Development credit added: ₹"+rupees+". No real money was charged.");}
 public Ui.Result checkout(Account a,long m,Map<String,String> p){
  access.student(a);access.open(m);List<Map<String,Object>> lines=new ArrayList<>();
  try{var arr=json.readTree(text(p,"items",8000));if(!arr.isArray()||arr.isEmpty()||arr.size()>30)throw error(400,"Cart must contain 1–30 items");
   Set<Long> seen=new HashSet<>();for(var item:arr){long id=item.path("id").asLong();int qty=item.path("quantity").asInt();if(id<1||qty<1||qty>20||!seen.add(id))throw error(400,"Invalid cart item or quantity");
    var menu=s.one("SELECT * FROM menu_items WHERE id=?",id);access.scope(m,menu);if(!bool(menu,"available")||!"CANTEEN".equals(str(menu,"meal"))||!dining.today().equals(java.time.LocalDate.parse(str(menu,"meal_date"))))throw error(409,"Item is not available today");
    lines.add(data("menu_id",id,"name",str(menu,"name"),"quantity",qty,"unit_cents",num(menu,"price_cents")));
   }
  }catch(org.springframework.web.server.ResponseStatusException e){throw e;}catch(Exception e){throw error(400,"Invalid cart JSON");}
  long total=0;for(var line:lines)total=Math.addExact(total,num(line,"quantity")*num(line,"unit_cents"));
  Map<String,Object> coupon=null;String code=optional(p,"coupon",30).toUpperCase(Locale.ROOT);
  if(!code.isEmpty()){coupon=s.one("SELECT * FROM coupons WHERE mess_id=? AND code=?",m,code);if(java.time.LocalDate.parse(str(coupon,"expires_on")).isBefore(dining.today())||total<num(coupon,"minimum_cents")||s.count("SELECT COUNT(*) FROM coupon_usage WHERE coupon_id=?",num(coupon,"id"))>=num(coupon,"usage_limit")||s.count("SELECT COUNT(*) FROM coupon_usage WHERE coupon_id=? AND user_id=?",num(coupon,"id"),a.getId())>0)throw error(409,"Coupon is expired, ineligible or already used");total=total*(100-num(coupon,"discount_percent"))/100;}
  wallet(a.getId());if(s.db.update("UPDATE wallets SET balance_cents=balance_cents-? WHERE user_id=? AND balance_cents>=?",total,a.getId(),total)!=1)throw error(409,"Insufficient wallet balance");
  long order=s.insert("orders",data("user_id",a.getId(),"mess_id",m,"total_cents",total));
  for(var line:lines){line.put("order_id",order);s.insert("order_items",line);}
  s.insert("wallet_transactions",data("user_id",a.getId(),"amount_cents",-total,"kind","ORDER_PAYMENT","order_id",order,"reference","order:"+order));
  if(coupon!=null)s.insert("coupon_usage",data("coupon_id",num(coupon,"id"),"user_id",a.getId(),"order_id",order));
  dining.notifyUser(a.getId(),"Order MQ"+order+" placed.");return new Ui.Result("Order MQ"+order+" placed. Paid ₹"+String.format(Locale.ROOT,"%.2f",total/100.0));
 }
 public Ui.Result status(Account a,long m,Map<String,String> p){
  var order=s.one("SELECT * FROM orders WHERE id=?",id(p,"id"));access.scope(m,order);String next=choice(p,"status","ACCEPTED","PREPARING","READY","COLLECTED","CANCELLED");String old=str(order,"status");
  if(a.getRole()==Role.STUDENT){access.owner(a,order);if(!"CANCELLED".equals(next)||!"PLACED".equals(old))throw error(409,"Students can only cancel an unaccepted order");}
  else{access.staff(a,"ORDER_MANAGE");boolean valid=switch(old){case "PLACED"->Set.of("ACCEPTED","CANCELLED").contains(next);case "ACCEPTED"->Set.of("PREPARING","CANCELLED").contains(next);case "PREPARING"->"READY".equals(next);case "READY"->"COLLECTED".equals(next);default->false;};if(!valid)throw error(409,"Invalid order transition: "+old+" → "+next);}
  if("CANCELLED".equals(next)){wallet(num(order,"user_id"));s.db.update("UPDATE wallets SET balance_cents=balance_cents+? WHERE user_id=?",num(order,"total_cents"),num(order,"user_id"));s.insert("wallet_transactions",data("user_id",num(order,"user_id"),"amount_cents",num(order,"total_cents"),"kind","REFUND","order_id",num(order,"id"),"reference","refund:"+num(order,"id")));}
  s.db.update("UPDATE orders SET status=? WHERE id=?",next,num(order,"id"));dining.notifyUser(num(order,"user_id"),"Order MQ"+num(order,"id")+": "+next);return new Ui.Result("Order updated to "+next);
 }
}
