package com.library.security;

import com.library.config.LibraryProperties;
import com.library.entity.AdminUser;
import com.library.model.AuditAction;
import com.library.repository.AdminUserRepository;
import com.library.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe bootstrap of the login accounts, from configuration only (no credential is hardcoded
 * in the source, and passwords are never logged).
 *   ADMIN : created only when NO account exists yet.
 *   STAFF : created when configured and not present yet (needs the admin to exist first).
 */
@Component
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);
    static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;
    private final LibraryProperties props;
    private final AuditService audit;

    public InitialAdminInitializer(AdminUserRepository repository, PasswordEncoder encoder,
                                   LibraryProperties props, AuditService audit) {
        this.repository = repository;
        this.encoder = encoder;
        this.props = props;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createAdminIfNoAccountExists();
        createStaffIfConfigured();
    }

    private void createAdminIfNoAccountExists() {
        if (repository.count() > 0) return;
        String username = props.getAdmin().getInitialUsername();
        String password = props.getAdmin().getInitialPassword();
        if (blank(username) || blank(password)) {
            log.warn("No administrator account exists. Set library.admin.initial-username and "
                    + "library.admin.initial-password (config/application.properties) and restart.");
            return;
        }
        create(username, password, "ADMIN", "Initial administrator");
    }

    private void createStaffIfConfigured() {
        String username = props.getAdmin().getInitialStaffUsername();
        String password = props.getAdmin().getInitialStaffPassword();
        if (blank(username) || blank(password)) return;
        if (repository.count() == 0) return;                       // admin must come first
        if (repository.findByUsername(username.trim()).isPresent()) return;
        create(username, password, "STAFF", "Staff account");
    }

    private void create(String username, String password, String role, String label) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("{} NOT created: its password is shorter than {} characters.", label, MIN_PASSWORD_LENGTH);
            return;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username.trim());
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        repository.save(user);
        audit.record(AuditAction.ADMIN_CREATED, "SYSTEM", "AdminUser", user.getId(),
                label + " '" + user.getUsername() + "' (" + role + ") created from configuration");
        log.info("{} '{}' created.", label, user.getUsername());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
