package com.parkflow.ParkFlow.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response shapes for accounts. Kept in one file so the API is easy to read. */
public final class UserDtos {
    private UserDtos() {}

    static final String USERNAME_RULE = "^[A-Za-z0-9._-]{3,30}$";
    static final String PHONE_RULE = "^[0-9+()\\- ]{7,20}$";

    public record SignupRequest(
            @NotBlank(message = "Enter a username.")
            @Pattern(regexp = USERNAME_RULE, message = "Username must be 3 to 30 letters, numbers, dots, dashes or underscores.")
            String username,
            @NotBlank(message = "Enter a password.")
            @Size(min = 8, max = 100, message = "Password must be 8 to 100 characters.")
            String password,
            @NotBlank(message = "Enter your first name.") @Size(max = 50, message = "First name is too long.")
            String firstName,
            @NotBlank(message = "Enter your last name.") @Size(max = 50, message = "Last name is too long.")
            String lastName,
            @NotBlank(message = "Enter your phone number.")
            @Pattern(regexp = PHONE_RULE, message = "Phone number must be 7 to 20 digits (spaces, +, - and brackets are allowed).")
            String phone,
            @NotNull(message = "Choose Customer or Parking provider.")
            Role role,
            @Size(max = 100, message = "Company name is too long.")
            String companyName,
            @Size(max = 200, message = "Work location is too long.")
            String workLocation) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your username.") String username,
            @NotBlank(message = "Enter your password.") String password) {}

    public record ProfileUpdate(
            @NotBlank(message = "Enter your first name.") @Size(max = 50, message = "First name is too long.")
            String firstName,
            @NotBlank(message = "Enter your last name.") @Size(max = 50, message = "Last name is too long.")
            String lastName,
            @NotBlank(message = "Enter your phone number.")
            @Pattern(regexp = PHONE_RULE, message = "Phone number must be 7 to 20 digits (spaces, +, - and brackets are allowed).")
            String phone,
            @Size(max = 100, message = "Company name is too long.")
            String companyName,
            @Size(max = 200, message = "Work location is too long.")
            String workLocation) {}

    public record PasswordChange(
            @NotBlank(message = "Enter your current password.") String currentPassword,
            @NotBlank(message = "Enter a new password.")
            @Size(min = 8, max = 100, message = "New password must be 8 to 100 characters.")
            String newPassword) {}

    public record UserView(Long id, String username, String firstName, String lastName, String phone,
                           Role role, AccountStatus status, String companyName, String workLocation,
                           Instant createdAt, Instant reviewedAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(), u.getPhone(),
                    u.getRole(), u.getStatus(), u.getCompanyName(), u.getWorkLocation(), u.getCreatedAt(), u.getReviewedAt());
        }
    }

    public record AuthResponse(String token, Instant expiresAt, UserView user) {}

    public record ProviderView(UserView provider, long buildingCount) {}
}
