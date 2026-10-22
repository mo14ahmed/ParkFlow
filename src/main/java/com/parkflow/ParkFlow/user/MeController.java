package com.parkflow.ParkFlow.user;

import com.parkflow.ParkFlow.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** The logged-in user's own account. Logging out is done by the client: it just forgets the token. */
@RestController
@RequestMapping("/api/me")
public class MeController {
    private final AccountService accounts;

    public MeController(AccountService accounts) { this.accounts = accounts; }

    static Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @GetMapping
    public UserView me(@AuthenticationPrincipal Jwt jwt) { return UserView.of(accounts.get(userId(jwt))); }

    @PutMapping
    public UserView update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate body) {
        return accounts.updateProfile(userId(jwt), body);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange body) {
        accounts.changePassword(userId(jwt), body);
    }
}
