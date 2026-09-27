package gdgrvce.iudex.server.config;

import gdgrvce.iudex.server.security.JwtAuthenticationFilter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Sets up the stateless JWT security used by the API. */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean h2ConsoleEnabled;
    private final String h2ConsolePath;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
                          @Value("${spring.h2.console.path:/h2-console}") String h2ConsolePath) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
        this.h2ConsolePath = h2ConsolePath;
    }

    /** Says so loudly, because an open database shell is easy to leave on by accident. */
    @PostConstruct
    void warnAboutTheConsole() {
        if (h2ConsoleEnabled) {
            log.warn("""

                    The H2 console is enabled and reachable WITHOUT AUTHENTICATION at {}.
                    It is a full database shell. This belongs on a development machine
                    only -- do not run with it enabled anywhere reachable by others.""", h2ConsolePath);
        }
    }

    /**
     * Allows the public auth routes and requires a valid token everywhere else.
     *
     * <p>The console is exempted from authentication only when it is switched on
     * at all, so a deployment that leaves it off has no such rule in its filter
     * chain and cannot be surprised by one.</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Safe to disable: the API is stateless and authenticates from a
                // Bearer token, never from an ambient cookie.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/auth/register", "/auth/login").permitAll();
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers(h2ConsolePath + "/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // The console renders in frames. Nothing else here does, so the default
        // deny stands unless the console is actually running.
        if (h2ConsoleEnabled) {
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }

    /** Hashes passwords before they are stored and checks them during login. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Gives the login service a way to check username and password credentials. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
