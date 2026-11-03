package dev.jordy.jordylab.settings.service;

import dev.jordy.jordylab.identity.UserAccountLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The admin's per-person chat limit (spec 014 FR-010, FR-013): refused for their own account, and refused for an admin,
 * who is never limited. Everything is checked before the limit table is touched. The target is read through identity's
 * public lookup (spec 019) instead of an external provider.
 */
@Service
@RequiredArgsConstructor
public class UserChatLimitService {

    private final UserAccountLookup userAccountLookup;
    private final GuestChatLimitService guestChatLimitService;

    public void setPersonal(UUID callerId, UUID targetUserId, int dailyLimit) {
        UserAccountLookup.AccountRef target = requireTarget(callerId, targetUserId);
        if ("admin".equals(target.role())) {
            throw new AdminAccountProtectedException(targetUserId);
        }
        guestChatLimitService.savePersonal(target.id().toString(), dailyLimit, callerId.toString());
    }

    /** Idempotent: clearing a limit that was never set is accepted. */
    public void clearPersonal(UUID callerId, UUID targetUserId) {
        requireTarget(callerId, targetUserId);
        guestChatLimitService.clearPersonal(targetUserId.toString());
    }

    private UserAccountLookup.AccountRef requireTarget(UUID callerId, UUID targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new CannotChangeOwnAccountException(targetUserId);
        }

        return userAccountLookup.find(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }
}
