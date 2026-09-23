package com.messq.ops;
import com.messq.auth.*;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.messq.ops.Store.*;
@Component
public class Access {
 final Store s;
 public Access(Store s){this.s=s;}
 public Map<String,Object> profile(Account a){return s.rows("SELECT * FROM profiles WHERE user_id=?",a.getId()).stream().findFirst().orElse(data("user_id",a.getId(),"mess_id",null,"plan_active",false,"permissions","","diet","VEGETARIAN","allergens",""));}
 public long mess(Account a){long id=num(profile(a),"mess_id");if(id==0)throw error(409,"Ask your admin to assign your account to a mess and meal plan");return id;}
 public void student(Account a){if(a.getRole()!=Role.STUDENT)throw error(403,"Student account required");}
 public void admin(Account a){if(a.getRole()!=Role.ADMIN)throw error(403,"Admin account required");}
 public void staff(Account a,String permission){if(a.getRole()==Role.ADMIN)return;if(a.getRole()!=Role.STAFF||!Arrays.asList(str(profile(a),"permissions").split(",")).contains(permission))throw error(403,"Missing staff permission: "+permission);}
 public void eligible(Account a){student(a);if(!bool(profile(a),"plan_active"))throw error(403,"An active meal plan is required");}
 public void open(long mess){if(!"OPEN".equals(str(s.one("SELECT * FROM messes WHERE id=?",mess),"status")))throw error(409,"This mess is not accepting new requests");}
 public void owner(Account a,Map<String,Object> record){if(num(record,"user_id")!=a.getId())throw error(403,"This record belongs to another account");}
 public void scope(long mess,Map<String,Object> record){if(num(record,"mess_id")!=mess)throw error(403,"This record belongs to another mess");}
}
