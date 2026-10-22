package com.parkflow.ParkFlow.user;

import com.parkflow.ParkFlow.building.BuildingRepository;
import com.parkflow.ParkFlow.config.TokenService;
import com.parkflow.ParkFlow.user.UserDtos.*;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    /** Compared against when the username does not exist, so a wrong username takes as long as a wrong password. */
    private final String dummyHash;
    private final UserRepository users;
    private final BuildingRepository buildings;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public AccountService(UserRepository users, BuildingRepository buildings, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.buildings = buildings;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    static String normalize(String username) { return username.trim().toLowerCase(Locale.ROOT); }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional
    public AuthResponse signup(SignupRequest r) {
        if (r.role() == Role.ADMIN) throw bad("Choose Customer or Parking provider.");
        String username = normalize(r.username());
        if (users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken. Try another one.");
        String company = clean(r.companyName());
        if (r.role() == Role.PROVIDER && company == null) throw bad("Enter your company name.");

        AccountStatus status = r.role() == Role.PROVIDER ? AccountStatus.PENDING : AccountStatus.ACTIVE;
        User u = new User(username, encoder.encode(r.password()), r.firstName().trim(), r.lastName().trim(),
                r.phone().trim(), r.role(), status);
        if (r.role() == Role.PROVIDER) u.setCompanyName(company);
        if (r.role() == Role.CUSTOMER) u.setWorkLocation(clean(r.workLocation()));
        users.save(u);
        return authResponse(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        User u = users.findByUsername(normalize(r.username())).orElse(null);
        boolean ok = encoder.matches(r.password(), u == null ? dummyHash : u.getPasswordHash());
        if (u == null || !ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        return authResponse(u);
    }

    private AuthResponse authResponse(User u) {
        TokenService.IssuedToken t = tokens.issue(u);
        return new AuthResponse(t.value(), t.expiresAt(), UserView.of(u));
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your account no longer exists. Log in again."));
    }

    @Transactional
    public UserView updateProfile(Long id, ProfileUpdate r) {
        User u = get(id);
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setPhone(r.phone().trim());
        if (u.getRole() == Role.PROVIDER) {
            String company = clean(r.companyName());
            if (company == null) throw bad("Enter your company name.");
            u.setCompanyName(company);
        }
        if (u.getRole() == Role.CUSTOMER) u.setWorkLocation(clean(r.workLocation()));
        return UserView.of(users.save(u));
    }

    @Transactional
    public void changePassword(Long id, PasswordChange r) {
        User u = get(id);
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) throw bad("Your current password is not right.");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
    }

    @Transactional(readOnly = true)
    public List<ProviderView> providers(AccountStatus status) {
        List<User> list = status == null
                ? users.findByRoleOrderByCreatedAtDesc(Role.PROVIDER)
                : users.findByRoleAndStatusOrderByCreatedAtDesc(Role.PROVIDER, status);
        return list.stream().map(u -> new ProviderView(UserView.of(u), buildings.countByProviderId(u.getId()))).toList();
    }

    @Transactional
    public UserView review(Long providerId, AccountStatus decision, Long adminId) {
        User u = users.findById(providerId)
                .filter(x -> x.getRole() == Role.PROVIDER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found."));
        u.review(decision, adminId);
        return UserView.of(users.save(u));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
