package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.RoleUpdateRequest;
import gdgrvce.iudex.server.dto.UserResponse;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.exception.ResourceNotFoundException;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Account administration.
 *
 * <p>Registration always produces a contestant, so this is the only way an
 * account becomes a contestmaster and the only reason the contest endpoints are
 * reachable at all. The first administrator comes from {@code AdminBootstrap}
 * at startup.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ContestAccessService access;

    public UserService(UserRepository userRepository, ContestAccessService access) {
        this.userRepository = userRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(UserDetails principal) {
        access.requireAdmin(access.currentUser(principal));
        return userRepository.findAllByOrderByUsername().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Moves an account to a different role.
     *
     * <p>An administrator cannot change their own role. That would let the last
     * administrator demote themselves and leave the deployment with no way back
     * in short of editing the database by hand.</p>
     */
    @Transactional
    public UserResponse updateRole(UUID userId, UserDetails principal, RoleUpdateRequest request) {
        User actor = access.currentUser(principal);
        access.requireAdmin(actor);

        if (request == null || request.role() == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (actor.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("An administrator cannot change their own role");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No such user: " + userId));
        target.setRole(request.role());
        return toResponse(userRepository.save(target));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getUsername(), user.getRole());
    }
}
