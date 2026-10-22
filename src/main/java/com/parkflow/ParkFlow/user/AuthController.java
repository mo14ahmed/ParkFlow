package com.parkflow.ParkFlow.user;

import com.parkflow.ParkFlow.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Sign up and log in. Both return a token; send it as "Authorization: Bearer <token>" on every other call. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest body) { return accounts.signup(body); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) { return accounts.login(body); }
}
