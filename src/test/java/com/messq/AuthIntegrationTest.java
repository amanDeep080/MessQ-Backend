package com.messq;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messq.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:messqtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
 "app.jwt-secret=test-only-secret-with-at-least-32-bytes-never-deploy"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired AccountRepository accounts;
 @Autowired PasswordEncoder passwords;
 private final String password = "Test-Only-Password-924";
 private String register(String email) throws Exception {
  ObjectNode body=json.createObjectNode().put("name","Test student").put("email",email).put("password",password);
  String response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("STUDENT")).andReturn().getResponse().getContentAsString();
  return json.readTree(response).get("accessToken").asText();
 }
 private String login(String email,String role,int expected) throws Exception {
  ObjectNode body=json.createObjectNode().put("email",email).put("password",password).put("role",role);
  return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
 }
 @Test void studentCannotEscalateRoleOrReadAdminData() throws Exception {
  String email=UUID.randomUUID()+"@example.com";
  String token=register(email);
  mvc.perform(get("/api/dashboard").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  mvc.perform(get("/api/staff/service").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  login(email,"ADMIN",403);
 }
 @Test void passwordsAreHashedAndDuplicateEmailsAreRejected() throws Exception {
  String email=UUID.randomUUID()+"@example.com";register(email);
  Account a=accounts.findByEmail(email).orElseThrow();assertNotEquals(password,a.getPasswordHash());assertTrue(passwords.matches(password,a.getPasswordHash()));
  String body=json.createObjectNode().put("name","Someone").put("email",email.toUpperCase()).put("password",password).toString();
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
 }
 @Test void authenticationRequiredAndTamperedTokenRejected() throws Exception {
  mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/me").header("Authorization","Bearer tampered.token.value")).andExpect(status().isUnauthorized());
 }
 @Test void publicSignupRejectsRoleField() throws Exception {
  String body=json.createObjectNode().put("name","Attacker").put("email","attacker@example.com").put("password",password).put("role","ADMIN").toString();
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
 }
 @Test void adminCanListAccountsWithoutExposingHashes() throws Exception {
  String email=UUID.randomUUID()+"@example.com";
  accounts.save(new Account("Admin",email,passwords.encode(password),Role.ADMIN));
  String token=json.readTree(login(email,"ADMIN",200)).get("accessToken").asText();
  mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
 }
 @Test void deactivatedAccountCannotUseExistingToken() throws Exception {
  String email=UUID.randomUUID()+"@example.com";String token=register(email);
  Account account=accounts.findByEmail(email).orElseThrow();account.deactivate();accounts.saveAndFlush(account);
  mvc.perform(get("/api/dashboard").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
 }
 @Test void wrongPasswordIsUnauthorized() throws Exception {
  String email=UUID.randomUUID()+"@example.com";register(email);
  String body=json.createObjectNode().put("email",email).put("password","WrongPassword-424").put("role","STUDENT").toString();
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
 }

 @Test void workspaceRoutesEnforceAuthenticationAndRole() throws Exception {
  mvc.perform(get("/api/workspace")).andExpect(status().isUnauthorized());
  String token=register(UUID.randomUUID()+"@example.com");
  mvc.perform(get("/api/workspace").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
  mvc.perform(get("/api/workspace/pages/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
  String body=json.createObjectNode().put("requestId",UUID.randomUUID().toString()).set("values",json.createObjectNode().put("diet","JAIN").put("allergens","milk")).toString();
  mvc.perform(post("/api/workspace/actions/profile.save").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/workspace/pages/profile").header("Authorization","Bearer "+token)).andExpect(status().isOk());
 }
}
