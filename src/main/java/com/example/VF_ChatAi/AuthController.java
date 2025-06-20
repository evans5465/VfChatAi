package com.example.VF_ChatAi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication and account management")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationService verificationService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with email/phone verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "User already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> register(
            @Parameter(description = "Username (3-50 characters, alphanumeric and underscore only)")
            @RequestParam @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,

            @Parameter(description = "Password (minimum 8 characters with complexity requirements)")
            @RequestParam @NotBlank String password,

            @Parameter(description = "Email address (optional but recommended)")
            @RequestParam(required = false) @Email String email,

            @Parameter(description = "Phone number in international format (optional)")
            @RequestParam(required = false)
            @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone,

            HttpServletRequest request) {

        logger.info("Registration request for username: {} from IP: {}", username, getClientIp(request));

        try {
            UserService.UserRegistrationResult result = userService.registerUser(
                    username, password, email, phone, request);

            if (result.isSuccess()) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("message", result.getMessage());
                responseData.put("username", username);
                responseData.put("requiresVerification", true);

                // Add verification options
                Map<String, Boolean> verificationOptions = new HashMap<>();
                if (email != null && !email.isEmpty()) {
                    verificationOptions.put("email", true);
                }
                if (phone != null && !phone.isEmpty()) {
                    verificationOptions.put("sms", true);
                }
                responseData.put("verificationOptions", verificationOptions);

                return ResponseEntity.ok(StandardApiResponse.success(responseData, result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), "REGISTRATION_FAILED"));
            }

        } catch (Exception e) {
            logger.error("Registration error for username {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Registration failed due to internal error", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with username/email/phone and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account locked"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> login(
            @Parameter(description = "Username, email, or phone number")
            @RequestParam @NotBlank String identifier,

            @Parameter(description = "User password")
            @RequestParam @NotBlank String password,

            HttpServletRequest request) {

        logger.info("Login request for identifier: {} from IP: {}", identifier, getClientIp(request));

        try {
            UserService.LoginResult result = userService.validateLogin(identifier, password, request);

            if (result.isSuccess()) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("message", result.getMessage());
                responseData.put("user", convertToPublicUserData(result.getUser()));
                responseData.put("sessionId", result.getSession().getSessionId());
                responseData.put("expiresAt", result.getSession().getExpiresAt());

                return ResponseEntity.ok(StandardApiResponse.success(responseData, "Login successful"));
            } else {
                HttpStatus status = result.getMessage().contains("locked") ?
                        HttpStatus.LOCKED : HttpStatus.UNAUTHORIZED;
                return ResponseEntity.status(status)
                        .body(StandardApiResponse.error(result.getMessage(), "LOGIN_FAILED"));
            }

        } catch (Exception e) {
            logger.error("Login error for identifier {}: {}", identifier, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Login failed due to internal error", "INTERNAL_ERROR"));
        }
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete user account", description = "Permanently delete user account with password verification")
    public ResponseEntity<StandardApiResponse<String>> deleteUser(
            @Parameter(description = "Username, email, or phone number")
            @RequestParam @NotBlank String identifier,

            @Parameter(description = "User password for verification")
            @RequestParam @NotBlank String password,

            HttpServletRequest request) {

        logger.info("Deletion request for identifier: {} from IP: {}", identifier, getClientIp(request));

        try {
            UserService.DeletionResult result = userService.deleteUser(identifier, password, request);

            if (result.isSuccess()) {
                return ResponseEntity.ok(StandardApiResponse.success(result.getMessage(), result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), "DELETION_FAILED"));
            }

        } catch (Exception e) {
            logger.error("Deletion error for identifier {}: {}", identifier, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Deletion failed due to internal error", "INTERNAL_ERROR"));
        }
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Retrieve list of all registered users (admin only)")
    public ResponseEntity<StandardApiResponse<List<UserService.UserDTO>>> getAllUsers() {
        try {
            List<UserService.UserDTO> users = userService.getAllUsers();
            return ResponseEntity.ok(StandardApiResponse.success(users, "Users retrieved successfully"));
        } catch (Exception e) {
            logger.error("Error retrieving users: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Failed to retrieve users", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password recovery", description = "Send password recovery instructions to user's email/phone")
    public ResponseEntity<StandardApiResponse<String>> forgotPassword(
            @Parameter(description = "Email or phone number")
            @RequestParam @NotBlank String identifier,

            HttpServletRequest request) {

        logger.info("Password recovery request for identifier: {} from IP: {}", identifier, getClientIp(request));

        try {
            UserService.PasswordRecoveryResult result = userService.initiatePasswordRecovery(identifier, request);
            return ResponseEntity.ok(StandardApiResponse.success(result.getMessage(), result.getMessage()));
        } catch (Exception e) {
            logger.error("Password recovery error for identifier {}: {}", identifier, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Password recovery failed", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token", description = "Reset password using recovery token")
    public ResponseEntity<StandardApiResponse<String>> resetPassword(
            @Parameter(description = "Password reset token")
            @RequestParam @NotBlank String token,

            @Parameter(description = "New password")
            @RequestParam @NotBlank String newPassword) {

        logger.info("Password reset attempt with token: {}...", token.substring(0, Math.min(token.length(), 8)));

        try {
            UserService.PasswordResetResult result = userService.resetPassword(token, newPassword);

            if (result.isSuccess()) {
                return ResponseEntity.ok(StandardApiResponse.success(result.getMessage(), result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), "RESET_FAILED"));
            }

        } catch (Exception e) {
            logger.error("Password reset error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Password reset failed", "INTERNAL_ERROR"));
        }
    }

    @GetMapping("/password-strength")
    @Operation(summary = "Check password strength", description = "Analyze password strength and provide feedback")
    public ResponseEntity<StandardApiResponse<UserService.PasswordStrengthResult>> checkPasswordStrength(
            @Parameter(description = "Password to analyze")
            @RequestParam @NotBlank String password) {

        try {
            UserService.PasswordStrengthResult result = userService.checkPasswordStrength(password);
            return ResponseEntity.ok(StandardApiResponse.success(result, "Password strength analyzed"));
        } catch (Exception e) {
            logger.error("Password strength check error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Password strength check failed", "INTERNAL_ERROR"));
        }
    }

    // Verification endpoints
    @PostMapping("/send-email-verification")
    @Operation(summary = "Send email verification", description = "Send verification code to user's email")
    public ResponseEntity<StandardApiResponse<String>> sendEmailVerification(
            @Parameter(description = "Username")
            @RequestParam @NotBlank String username) {

        logger.info("Email verification request for user: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            User user = userOpt.get();
            VerificationService.VerificationResult result = verificationService.sendEmailVerification(user);

            if (result.isSuccess()) {
                return ResponseEntity.ok(StandardApiResponse.success(result.getMessage(), result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), result.getErrorType().toString()));
            }

        } catch (Exception e) {
            logger.error("Email verification error for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Email verification failed", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/send-sms-verification")
    @Operation(summary = "Send SMS verification", description = "Send verification code to user's phone")
    public ResponseEntity<StandardApiResponse<String>> sendSMSVerification(
            @Parameter(description = "Username")
            @RequestParam @NotBlank String username) {

        logger.info("SMS verification request for user: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            User user = userOpt.get();
            VerificationService.VerificationResult result = verificationService.sendSMSVerification(user);

            if (result.isSuccess()) {
                return ResponseEntity.ok(StandardApiResponse.success(result.getMessage(), result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), result.getErrorType().toString()));
            }

        } catch (Exception e) {
            logger.error("SMS verification error for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("SMS verification failed", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email code", description = "Verify email with the provided code")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> verifyEmail(
            @Parameter(description = "Username")
            @RequestParam @NotBlank String username,

            @Parameter(description = "6-digit verification code")
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {

        logger.info("Email verification attempt for user: {}", username);

        try {
            VerificationService.VerificationResult result = verificationService.verifyEmailCode(username, code);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("message", result.getMessage());
            responseData.put("verified", result.isSuccess());

            if (result.isSuccess()) {
                // Get updated user info
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    responseData.put("accountActive", user.isAccountActive());
                    responseData.put("emailVerified", user.isEmailVerified());
                }

                return ResponseEntity.ok(StandardApiResponse.success(responseData, result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), result.getErrorType().toString()));
            }

        } catch (Exception e) {
            logger.error("Email verification error for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Email verification failed", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/verify-sms")
    @Operation(summary = "Verify SMS code", description = "Verify phone number with the provided code")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> verifySMS(
            @Parameter(description = "Username")
            @RequestParam @NotBlank String username,

            @Parameter(description = "6-digit verification code")
            @RequestParam @NotBlank @Size(min = 6, max = 6) String code) {

        logger.info("SMS verification attempt for user: {}", username);

        try {
            VerificationService.VerificationResult result = verificationService.verifySMSCode(username, code);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("message", result.getMessage());
            responseData.put("verified", result.isSuccess());

            if (result.isSuccess()) {
                // Get updated user info
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    responseData.put("accountActive", user.isAccountActive());
                    responseData.put("phoneVerified", user.isPhoneVerified());
                }

                return ResponseEntity.ok(StandardApiResponse.success(responseData, result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), result.getErrorType().toString()));
            }

        } catch (Exception e) {
            logger.error("SMS verification error for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("SMS verification failed", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/choose-verification")
    @Operation(summary = "Choose verification method", description = "Send verification code via chosen method (email or sms)")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> chooseVerification(
            @Parameter(description = "Username")
            @RequestParam @NotBlank String username,

            @Parameter(description = "Verification method: 'email' or 'sms'")
            @RequestParam @NotBlank @Pattern(regexp = "^(email|sms)$") String method) {

        logger.info("Verification method choice for user: {} - method: {}", username, method);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            User user = userOpt.get();
            VerificationService.VerificationResult result;

            if ("email".equals(method)) {
                result = verificationService.sendEmailVerification(user);
            } else {
                result = verificationService.sendSMSVerification(user);
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("message", result.getMessage());
            responseData.put("method", method);
            responseData.put("sent", result.isSuccess());

            if (result.isSuccess()) {
                return ResponseEntity.ok(StandardApiResponse.success(responseData, result.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error(result.getMessage(), result.getErrorType().toString()));
            }

        } catch (Exception e) {
            logger.error("Verification choice error for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Verification failed", "INTERNAL_ERROR"));
        }
    }

    @GetMapping("/verification-status/{username}")
    @Operation(summary = "Get verification status", description = "Get detailed verification status for a user")
    public ResponseEntity<StandardApiResponse<VerificationService.VerificationStats>> getVerificationStatus(
            @Parameter(description = "Username")
            @PathVariable @NotBlank String username) {

        try {
            VerificationService.VerificationStats stats = verificationService.getVerificationStats(username);
            if (stats == null) {
                return ResponseEntity.badRequest()
                        .body(StandardApiResponse.error("User not found", "USER_NOT_FOUND"));
            }

            return ResponseEntity.ok(StandardApiResponse.success(stats, "Verification status retrieved"));
        } catch (Exception e) {
            logger.error("Error getting verification status for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Failed to get verification status", "INTERNAL_ERROR"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidate user session")
    public ResponseEntity<StandardApiResponse<String>> logout(
            @Parameter(description = "Session ID")
            @RequestParam @NotBlank String sessionId) {

        try {
            // Implementation would invalidate the session
            // For now, return success
            return ResponseEntity.ok(StandardApiResponse.success("Logged out successfully", "Logout successful"));
        } catch (Exception e) {
            logger.error("Logout error for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Logout failed", "INTERNAL_ERROR"));
        }
    }

    @GetMapping("/validate-session")
    @Operation(summary = "Validate session", description = "Check if session is valid and active")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> validateSession(
            @Parameter(description = "Session ID")
            @RequestParam @NotBlank String sessionId) {

        try {
            // Implementation would validate session
            // For now, return mock response
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("valid", true);
            sessionData.put("sessionId", sessionId);

            return ResponseEntity.ok(StandardApiResponse.success(sessionData, "Session is valid"));
        } catch (Exception e) {
            logger.error("Session validation error for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StandardApiResponse.error("Session validation failed", "INTERNAL_ERROR"));
        }
    }

    // Helper methods
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private Map<String, Object> convertToPublicUserData(User user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail());
        userData.put("phone", user.getPhone());
        userData.put("emailVerified", user.isEmailVerified());
        userData.put("phoneVerified", user.isPhoneVerified());
        userData.put("accountActive", user.isAccountActive());
        userData.put("status", user.getStatus().toString());
        userData.put("role", user.getRole().toString());
        userData.put("createdAt", user.getCreatedAt());
        userData.put("lastLogin", user.getLastLogin());
        userData.put("fullName", user.getFullName());
        return userData;
    }

    /**
     * Standard API Response wrapper
     */
    public static class StandardApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private String errorCode;
        private long timestamp;

        public StandardApiResponse() {
            this.timestamp = System.currentTimeMillis();
        }

        public StandardApiResponse(boolean success, String message, T data, String errorCode) {
            this();
            this.success = success;
            this.message = message;
            this.data = data;
            this.errorCode = errorCode;
        }

        public static <T> StandardApiResponse<T> success(T data, String message) {
            return new StandardApiResponse<>(true, message, data, null);
        }

        public static <T> StandardApiResponse<T> error(String message, String errorCode) {
            return new StandardApiResponse<>(false, message, null, errorCode);
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }

        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}