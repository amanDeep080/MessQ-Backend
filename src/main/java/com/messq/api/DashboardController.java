package com.messq.api;
import com.messq.auth.*;
import com.messq.auth.AuthDtos.UserDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class DashboardController {
    private final AuthService auth;
    private final AccountRepository accounts;
    public DashboardController(AuthService auth, AccountRepository accounts) { this.auth = auth; this.accounts = accounts; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","UP"); }
    @GetMapping("/me") public UserDto me(@AuthenticationPrincipal Jwt jwt) { return UserDto.from(auth.current(jwt)); }
    @GetMapping("/dashboard") public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) {
        Account account = auth.current(jwt);
        List<Metric> metrics = account.getRole() == Role.ADMIN ? List.of(new Metric("Registered accounts", Long.toString(accounts.count()))) : List.of();
        return new Dashboard("Welcome, " + account.getName(), "Mess assignment pending", metrics, List.of());
    }
    @GetMapping("/admin/users") @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> users(@AuthenticationPrincipal Jwt jwt) {
        auth.current(jwt);
        return accounts.findAll(PageRequest.of(0,100, Sort.by("id"))).stream().map(UserDto::from).toList();
    }
    @GetMapping("/staff/service") @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String,String> service(@AuthenticationPrincipal Jwt jwt) {
        auth.current(jwt);
        return Map.of("message", "Staff access verified. Live meal operations will be implemented in Phase 2.");
    }
    public record Metric(String label, String value) {}
    public record MenuItem(String name, String note) {}
    public record Dashboard(String title, String location, List<Metric> metrics, List<MenuItem> menu) {}
}
