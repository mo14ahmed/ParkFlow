package com.parkflow.ParkFlow.user;

import com.parkflow.ParkFlow.user.UserDtos.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Admin only: review parking providers. */
@RestController
@RequestMapping("/api/admin/providers")
public class AdminController {
    private final AccountService accounts;

    public AdminController(AccountService accounts) { this.accounts = accounts; }

    /** All providers, newest first. Add ?status=PENDING to see only the ones waiting for review. */
    @GetMapping
    public List<ProviderView> list(@RequestParam(required = false) AccountStatus status) {
        return accounts.providers(status);
    }

    @PostMapping("/{id}/approve")
    public UserView approve(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return accounts.review(id, AccountStatus.ACTIVE, MeController.userId(jwt));
    }

    @PostMapping("/{id}/reject")
    public UserView reject(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return accounts.review(id, AccountStatus.REJECTED, MeController.userId(jwt));
    }
}
