package com.parkflow.ParkFlow.config;

import com.parkflow.ParkFlow.user.AccountStatus;
import com.parkflow.ParkFlow.user.Role;
import com.parkflow.ParkFlow.user.User;
import com.parkflow.ParkFlow.user.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account at startup from PARKFLOW_ADMIN_USERNAME and PARKFLOW_ADMIN_PASSWORD.
 * Nobody can sign up as an admin. If the account already exists it is left unchanged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${parkflow.admin.username:}") String username,
                          @Value("${parkflow.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isEmpty() || password.isEmpty()) {
            log.info("No PARKFLOW_ADMIN_USERNAME/PARKFLOW_ADMIN_PASSWORD set: no admin account created.");
            return;
        }
        if (users.existsByUsername(username)) return;
        if (password.length() < 8) {
            log.warn("PARKFLOW_ADMIN_PASSWORD must be at least 8 characters: admin account not created.");
            return;
        }
        users.save(new User(username, encoder.encode(password), "ParkFlow", "Admin", "0000000",
                Role.ADMIN, AccountStatus.ACTIVE));
        log.info("Created admin account '{}'.", username);
    }
}
