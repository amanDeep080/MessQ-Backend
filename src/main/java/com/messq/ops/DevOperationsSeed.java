package com.messq.ops;
import com.messq.auth.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.messq.ops.Store.*;
@Component @Profile({"dev","demo-data"}) @Order(1)
public class DevOperationsSeed implements CommandLineRunner {
 final Store s;final DiningService dining;
 public DevOperationsSeed(Store s,DiningService dining){this.s=s;this.dining=dining;}
 @Override @Transactional public void run(String...args){
  long m;if(s.count("SELECT COUNT(*) FROM messes")==0){m=s.insert("messes",data("name","Hostel A Mess","seats",120));s.insert("messes",data("name","Hostel B Mess","seats",100));}else m=s.count("SELECT MIN(id) FROM messes");
  for(var a:s.rows("SELECT id,role FROM accounts WHERE email IN ('student@messq.local','staff@messq.local','admin@messq.local')"))if(s.count("SELECT COUNT(*) FROM profiles WHERE user_id=?",num(a,"id"))==0){s.db.update("INSERT INTO profiles(user_id,mess_id,hostel,room,plan_active,permissions) VALUES (?,?,?,?,?,?)",num(a,"id"),m,"Hostel A","101",true,"QR_SCAN,QUEUE_MANAGE,ORDER_MANAGE,INVENTORY_MANAGE,MENU_MANAGE,COMPLAINT_MANAGE");}
  if(s.count("SELECT COUNT(*) FROM menu_items WHERE mess_id=? AND meal_date=?",m,dining.today())==0){
   for(String name:List.of("Dal tadka","Steamed rice","Roti","Mixed sabzi","Curd"))s.insert("menu_items",data("mess_id",m,"name",name,"meal","DINNER","meal_date",dining.today(),"allergens",name.equals("Roti")?"wheat":name.equals("Curd")?"milk":""));
   for(String name:List.of("Paneer roll","Vegetable sandwich","Cold coffee"))s.insert("menu_items",data("mess_id",m,"name",name,"meal","CANTEEN","meal_date",dining.today(),"price_cents",name.equals("Cold coffee")?6500:8000,"allergens",name.equals("Cold coffee")?"milk":"wheat,milk"));
   for(int i=1;i<=3;i++)s.insert("meal_slots",data("mess_id",m,"meal","DINNER","meal_date",dining.today(),"starts_at",dining.now().plusMinutes(i*10),"ends_at",dining.now().plusMinutes(i*10+20),"capacity",20));
  }
  if(s.count("SELECT COUNT(*) FROM tables_state WHERE mess_id=?",m)==0)for(int i=1;i<=12;i++)s.insert("tables_state",data("mess_id",m,"label","Table "+i,"seats",4));
  if(s.count("SELECT COUNT(*) FROM inventory WHERE mess_id=?",m)==0){s.insert("inventory",data("mess_id",m,"name","Rice","unit","kg","quantity",120,"minimum",25,"supplier","Campus supplier"));s.insert("inventory",data("mess_id",m,"name","Milk","unit","litre","quantity",8,"minimum",10,"expires_on",dining.today().plusDays(1),"supplier","Dairy supplier"));}
  if(s.count("SELECT COUNT(*) FROM announcements WHERE mess_id=?",m)==0)s.insert("announcements",data("mess_id",m,"title","Welcome to MessQ","body","These starter menu and inventory records are development seed data. Edit them for your campus before use."));
 }
}
