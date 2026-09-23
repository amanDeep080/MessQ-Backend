package com.messq.auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
@Component @Profile({"dev","demo-data"}) @org.springframework.core.annotation.Order(0)
public class DevSeed implements CommandLineRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final String password;
    public DevSeed(AccountRepository accounts, PasswordEncoder encoder, @Value("${app.demo-password:${MESSQ_DEMO_PASSWORD:}}") String password) { this.accounts = accounts; this.encoder = encoder; this.password = password; }
    @Override public void run(String... args) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalStateException("Set MESSQ_DEMO_PASSWORD to 12–72 characters (at most 72 UTF-8 bytes) for the dev profile.");
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase(java.util.Locale.ROOT) + "@messq.local";
            if (!accounts.existsByEmail(email)) accounts.save(new Account(role == Role.STUDENT ? "Maansi" : role.name(), email, encoder.encode(password), role));
        }
    }
}
