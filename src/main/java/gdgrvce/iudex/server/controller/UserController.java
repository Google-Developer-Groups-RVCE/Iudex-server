package gdgrvce.iudex.server.controller;

import gdgrvce.iudex.server.dto.RoleUpdateRequest;
import gdgrvce.iudex.server.dto.UserResponse;
import gdgrvce.iudex.server.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Administrator-only account management. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return userService.list(principal);
    }

    /** Promotes or demotes an account, typically to make a contestmaster. */
    @PatchMapping("/{userId}/role")
    public UserResponse updateRole(@PathVariable UUID userId,
                                   @AuthenticationPrincipal UserDetails principal,
                                   @RequestBody RoleUpdateRequest request) {
        return userService.updateRole(userId, principal, request);
    }
}
