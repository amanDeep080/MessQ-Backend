package com.messq.auth;
import jakarta.validation.constraints.*;
public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password, @NotNull Role role) {}
    public record RegisterRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password) {}
    public record UserDto(Long id, String name, String email, Role role) {
        public static UserDto from(Account a) { return new UserDto(a.getId(), a.getName(), a.getEmail(), a.getRole()); }
    }
    public record AuthResponse(String accessToken, long expiresIn, UserDto user) {}
}
