package com.messq.auth;

import com.messq.auth.AuthDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final long lifetime;
    private final String dummyHash;
    public AuthService(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder encoder, @Value("${app.token-lifetime-seconds}") long lifetime) {
        this.accounts = accounts; this.passwords = passwords; this.encoder = encoder; this.lifetime = lifetime;
        this.dummyHash = passwords.encode("not-an-account-password");
    }
    @Transactional(readOnly=true)
    public AuthResponse login(LoginRequest request) {
        checkPasswordLength(request.password());
        Account account = accounts.findByEmail(normalize(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches || !account.isActive()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        if (account.getRole() != request.role()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account cannot use the selected role");
        return issue(account);
    }
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        checkPasswordLength(request.password());
        String email = normalize(request.email());
        if (accounts.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        Account account = accounts.saveAndFlush(new Account(request.name().trim(), email, passwords.encode(request.password()), Role.STUDENT));
        return issue(account);
    }
    public Account current(Jwt jwt) {
        long id;
        try { id = Long.parseLong(jwt.getSubject()); } catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"); }
        Account account = accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
        if (!account.isActive() || !account.getRole().name().equals(jwt.getClaimAsString("role"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account access changed; sign in again");
        return account;
    }
    private AuthResponse issue(Account account) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("messq").subject(account.getId().toString())
            .audience(List.of("messq-android")).issuedAt(now).expiresAt(now.plusSeconds(lifetime))
            .claim("role", account.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, lifetime, UserDto.from(account));
    }
    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
    }
}
