package com.messq.auth;

import com.messq.ops.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import static com.messq.ops.Store.*;

/** Explicit first-install provisioning. Existing admins/passwords are never modified. */
@Component
@Order(-1)
@ConditionalOnProperty(name="MESSQ_BOOTSTRAP_ADMIN",havingValue="true")
public class BootstrapAdmin implements CommandLineRunner {
 private final Store store;
 private final AccountRepository accounts;
 private final PasswordEncoder passwords;
 private final String email,password;
 public BootstrapAdmin(Store store,AccountRepository accounts,PasswordEncoder passwords,
  @Value("${MESSQ_ADMIN_EMAIL:}") String email,@Value("${MESSQ_ADMIN_PASSWORD:}") String password){
  this.store=store;this.accounts=accounts;this.passwords=passwords;this.email=email;this.password=password;
 }
 @Override @Transactional public void run(String... args){
  if(store.count("SELECT COUNT(*) FROM accounts WHERE role='ADMIN'")>0)return;
  String normalized=AuthService.normalize(email);
  if(!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)
   throw new IllegalStateException("First install requires MESSQ_ADMIN_EMAIL and MESSQ_ADMIN_PASSWORD (12+ characters, at most 72 UTF-8 bytes)");
  if(accounts.existsByEmail(normalized))throw new IllegalStateException("Bootstrap email already belongs to an account; use a new admin email");
  Account admin=accounts.saveAndFlush(new Account("Mess administrator",normalized,passwords.encode(password),Role.ADMIN));
  long mess=store.insert("messes",data("name","College Mess","seats",100));
  store.db.update("INSERT INTO profiles(user_id,mess_id) VALUES (?,?)",admin.getId(),mess);
 }
}
