package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.rest.controller.model.ErrorBody;
import dev.jordy.jordylab.identity.service.AccountLockedException;
import dev.jordy.jordylab.identity.service.AccountRejectedException;
import dev.jordy.jordylab.identity.service.AccountRevokedException;
import dev.jordy.jordylab.identity.service.AdminAccountProtectedException;
import dev.jordy.jordylab.identity.service.AlreadyApprovedException;
import dev.jordy.jordylab.identity.service.InvalidCredentialsException;
import dev.jordy.jordylab.identity.service.InvalidRequestException;
import dev.jordy.jordylab.identity.service.InvalidUsernameException;
import dev.jordy.jordylab.identity.service.LastAdminProtectedException;
import dev.jordy.jordylab.identity.service.MachineTokenAlreadyRevokedException;
import dev.jordy.jordylab.identity.service.MachineTokenNotFoundException;
import dev.jordy.jordylab.identity.service.OwnAccountOperationException;
import dev.jordy.jordylab.identity.service.PasskeyNotFoundException;
import dev.jordy.jordylab.identity.service.SessionNotFoundException;
import dev.jordy.jordylab.identity.service.UserNotFoundException;
import dev.jordy.jordylab.identity.service.UserNotApprovedException;
import dev.jordy.jordylab.identity.service.UsernameTakenException;
import dev.jordy.jordylab.identity.service.WeakPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the identity module's exceptions to the contract's reason codes (spec 019 contracts/identity-api.md). */
@RestControllerAdvice(basePackages = "dev.jordy.jordylab.identity")
public class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorBody> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody("INVALID_CREDENTIALS", "Invalid username or password"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorBody> handleAccountLocked(AccountLockedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody("ACCOUNT_LOCKED", "Account locked until " + exception.getLockedUntil()));
    }

    @ExceptionHandler(AccountRejectedException.class)
    public ResponseEntity<ErrorBody> handleAccountRejected(AccountRejectedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("ACCOUNT_REJECTED", "This account was rejected"));
    }

    @ExceptionHandler(AccountRevokedException.class)
    public ResponseEntity<ErrorBody> handleAccountRevoked(AccountRevokedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("ACCOUNT_REVOKED", "This account's access was revoked"));
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ErrorBody> handleUsernameTaken(UsernameTakenException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("USERNAME_TAKEN", "That username is already taken"));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ErrorBody> handleWeakPassword(WeakPasswordException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("WEAK_PASSWORD", exception.getMessage()));
    }

    @ExceptionHandler(InvalidUsernameException.class)
    public ResponseEntity<ErrorBody> handleInvalidUsername(InvalidUsernameException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_USERNAME", exception.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorBody> handleInvalidRequest(InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(OwnAccountOperationException.class)
    public ResponseEntity<ErrorBody> handleOwnAccount(OwnAccountOperationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("OWN_ACCOUNT", "You cannot change your own account from here"));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorBody> handleSessionNotFound(SessionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("SESSION_NOT_FOUND", "That session is no longer active"));
    }

    @ExceptionHandler(AdminAccountProtectedException.class)
    public ResponseEntity<ErrorBody> handleAdminProtected(AdminAccountProtectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("ADMIN_PROTECTED", "Admin accounts cannot be deleted"));
    }

    @ExceptionHandler(MachineTokenNotFoundException.class)
    public ResponseEntity<ErrorBody> handleMachineNotFound(MachineTokenNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("MACHINE_NOT_FOUND", "That machine no longer exists"));
    }

    @ExceptionHandler(MachineTokenAlreadyRevokedException.class)
    public ResponseEntity<ErrorBody> handleMachineAlreadyRevoked(MachineTokenAlreadyRevokedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("ALREADY_REVOKED", "That machine token is already revoked"));
    }

    @ExceptionHandler(PasskeyNotFoundException.class)
    public ResponseEntity<ErrorBody> handlePasskeyNotFound(PasskeyNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("PASSKEY_NOT_FOUND", "This passkey is not enrolled for your account"));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorBody> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("USER_NOT_FOUND", "User not found"));
    }

    @ExceptionHandler(LastAdminProtectedException.class)
    public ResponseEntity<ErrorBody> handleLastAdminProtected(LastAdminProtectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("LAST_ADMIN_PROTECTED", "The last approved admin cannot be removed"));
    }

    @ExceptionHandler(UserNotApprovedException.class)
    public ResponseEntity<ErrorBody> handleUserNotApproved(UserNotApprovedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("USER_NOT_APPROVED", "The account is not approved"));
    }

    @ExceptionHandler(AlreadyApprovedException.class)
    public ResponseEntity<ErrorBody> handleAlreadyApproved(AlreadyApprovedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("ALREADY_APPROVED", "The account is already approved"));
    }
}
