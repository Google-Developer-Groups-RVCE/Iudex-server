package gdgrvce.iudex.server.config;

import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates the first administrator so a fresh deployment has a way in.
 *
 * <p>Registration through {@code /auth/register} always produces a contestant,
 * and only an administrator can change a role, so without this nobody could
 * ever become a contestmaster and no contest could be created.</p>
 *
 * <p>This runs only when the database holds no administrator at all. With
 * {@code iudex.admin.username} and {@code iudex.admin.password} set it uses
 * those; otherwise it generates a password and logs it once, which is enough to
 * get a local run going and is never a credential anyone can guess.</p>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String DEFAULT_USERNAME = "admin";
    private static final int GENERATED_PASSWORD_BYTES = 24;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredUsername;
    private final String configuredPassword;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${iudex.admin.username:}") String configuredUsername,
                          @Value("${iudex.admin.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        String username = configuredUsername.isBlank() ? DEFAULT_USERNAME : configuredUsername.trim();
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Cannot create the first administrator: the username '{}' is already taken. "
                    + "Set iudex.admin.username to a free name, or promote the existing account "
                    + "directly in the database.", username);
            return;
        }

        boolean generated = configuredPassword.isBlank();
        String password = generated ? generatePassword() : configuredPassword;

        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        if (generated) {
            log.warn("""

                    Created the first administrator, because none existed.
                      username: {}
                      password: {}
                    This is shown once and is not recoverable. Set iudex.admin.username and
                    iudex.admin.password to choose your own credentials instead.""", username, password);
        } else {
            log.info("Created the first administrator '{}' from configuration.", username);
        }
    }

    private String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
