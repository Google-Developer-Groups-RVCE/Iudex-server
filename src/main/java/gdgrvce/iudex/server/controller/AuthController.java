package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.AuthResponse;
import gdgrvce.iudex.server.dto.LoginRequest;
import gdgrvce.iudex.server.dto.MeResponse;
import gdgrvce.iudex.server.dto.RegisterRequest;
import gdgrvce.iudex.server.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for registering, logging in, and checking the current user. */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Creates an account and returns the token for the new user. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Checks the login details and returns a fresh token. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Returns the user that the security filter found in the JWT. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UserDetails principal) {
        String role = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .orElse(null);

        return new MeResponse(principal.getUsername(), role);
    }
}
