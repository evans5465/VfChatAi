package com.example.VF_ChatAi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordValidator passwordValidator;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private Validator validator;

    @Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.security.account-lockout-duration:30}")
    private int accountLockoutDurationMinutes;

    @Value("${app.security.session-timeout:30}")
    private int sessionTimeoutMinutes;

    // Session tracking
    private final Map<String, UserSession> activeSessions = new HashMap<>();

    /**
     * Register a new user with comprehensive validation and security
     */
    public UserRegistrationResult registerUser(String username, String password, String email,
                                               String phone, HttpServletRequest request) {
        logger.info("Registration attempt for username: {}", username);

        try {
            // Input validation and sanitization
            username = sanitizeInput(username);
            email = sanitizeInput(email);
            phone = sanitizeInput(phone);

            // Create user object for validation
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPhone(phone);

            // Bean validation
            Set<ConstraintViolation<User>> violations = validator.validate(user);
            if (!violations.isEmpty()) {
                List<String> errors = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toList());
                return new UserRegistrationResult(false, "Validation failed: " + String.join(", ", errors), null);
            }

            // Business logic validation
            ValidationResult businessValidation = validateRegistrationData(username, email, phone, password);
            if (!businessValidation.isValid()) {
                return new UserRegistrationResult(false, businessValidation.getMessage(), null);
            }

            // Password validation
            PasswordValidator.ValidationResult passwordResult = passwordValidator.validatePassword(password);
            if (!passwordResult.isValid()) {
                String errorMessage = "Password validation failed: " + String.join(", ", passwordResult.getErrors());
                return new UserRegistrationResult(false, errorMessage, null);
            }

            // Hash password securely
            String hashedPassword = passwordValidator.hashPassword(password);

            // Create user
            User newUser = new User(username, hashedPassword, email, phone);
            newUser.setCreatedByIp(getClientIpAddress(request));
            newUser.setTimezone(extractTimezoneFromRequest(request));
            newUser.setLanguage(extractLanguageFromRequest(request));

            // Save user
            User savedUser = userRepository.save(newUser);

            logger.info("User registered successfully: {}", username);

            // Determine verification message
            String verificationMessage = buildVerificationMessage(email, phone);

            return new UserRegistrationResult(true,
                    "User registered successfully! " + verificationMessage, savedUser);

        } catch (Exception e) {
            logger.error("Registration failed for username {}: {}", username, e.getMessage(), e);
            return new UserRegistrationResult(false,
                    "Registration failed due to internal error. Please try again.", null);
        }
    }

    /**
     * Validate user login with security measures
     */
    public LoginResult validateLogin(String identifier, String password, HttpServletRequest request) {
        logger.info("Login attempt for identifier: {}", identifier);

        try {
            // Sanitize input
            identifier = sanitizeInput(identifier);

            if (identifier == null || identifier.isEmpty() || password == null || password.isEmpty()) {
                return new LoginResult(false, "Username and password are required", null, null);
            }

            // Find user by username, email, or phone
            Optional<User> userOpt = userRepository.findByUsernameOrEmailOrPhone(identifier);

            if (userOpt.isEmpty()) {
                // Simulate processing time to prevent username enumeration
                passwordValidator.hashPassword("dummy_password");
                logger.warn("Login attempt with non-existent identifier: {}", identifier);
                return new LoginResult(false, "Invalid credentials", null, null);
            }

            User user = userOpt.get();
            String clientIp = getClientIpAddress(request);

            // Check if account is locked
            if (user.isAccountLocked()) {
                logger.warn("Login attempt for locked account: {}", user.getUsername());
                return new LoginResult(false,
                        "Account is locked due to multiple failed login attempts. Please contact support.",
                        null, null);
            }

            // Verify password
            boolean passwordValid = passwordValidator.verifyPassword(password, user.getPasswordHash());

            if (!passwordValid) {
                // Increment failed attempts
                user.incrementFailedLoginAttempts();
                user.setLastLoginIp(clientIp);
                userRepository.save(user);

                logger.warn("Failed login attempt for user: {} from IP: {}", user.getUsername(), clientIp);

                int remainingAttempts = maxLoginAttempts - user.getFailedLoginAttempts();
                if (remainingAttempts <= 0) {
                    return new LoginResult(false, "Account has been locked due to too many failed attempts", null, null);
                } else {
                    return new LoginResult(false,
                            String.format("Invalid credentials. %d attempts remaining before account lockout.", remainingAttempts),
                            null, null);
                }
            }

            // Check if account is active
            if (!user.canLogin()) {
                logger.warn("Login attempt for inactive account: {}", user.getUsername());
                return new LoginResult(false, getAccountStatusMessage(user), null, null);
            }

            // Successful login
            user.resetFailedLoginAttempts();
            user.setLastLogin(LocalDateTime.now());
            user.setLastLoginIp(clientIp);

            // Check if password needs rehashing
            if (passwordValidator.needsRehashing(user.getPasswordHash())) {
                String newHash = passwordValidator.hashPassword(password);
                user.setPasswordHash(newHash);
                logger.info("Password rehashed for user: {}", user.getUsername());
            }

            userRepository.save(user);

            // Create session
            UserSession session = createUserSession(user, request);

            logger.info("Successful login for user: {} from IP: {}", user.getUsername(), clientIp);

            return new LoginResult(true, "Login successful", user, session);

        } catch (Exception e) {
            logger.error("Login error for identifier {}: {}", identifier, e.getMessage(), e);
            return new LoginResult(false, "Login failed due to internal error", null, null);
        }
    }

    /**
     * Delete user with security validation
     */
    public DeletionResult deleteUser(String identifier, String password, HttpServletRequest request) {
        logger.info("Deletion attempt for identifier: {}", identifier);

        try {
            identifier = sanitizeInput(identifier);

            if (identifier == null || identifier.isEmpty() || password == null || password.isEmpty()) {
                return new DeletionResult(false, "Username and password are required");
            }

            Optional<User> userOpt = userRepository.findByUsernameOrEmailOrPhone(identifier);

            if (userOpt.isEmpty()) {
                return new DeletionResult(false, "User not found");
            }

            User user = userOpt.get();

            // Verify password
            if (!passwordValidator.verifyPassword(password, user.getPasswordHash())) {
                logger.warn("Failed deletion attempt for user: {} - invalid password", user.getUsername());
                return new DeletionResult(false, "Invalid password");
            }

            String username = user.getUsername();
            String clientIp = getClientIpAddress(request);

            // Remove user
            userRepository.delete(user);

            // Invalidate all sessions for this user
            invalidateUserSessions(username);

            logger.info("User deleted successfully: {} from IP: {}", username, clientIp);

            return new DeletionResult(true, "User deleted successfully");

        } catch (Exception e) {
            logger.error("Deletion error for identifier {}: {}", identifier, e.getMessage(), e);
            return new DeletionResult(false, "Deletion failed due to internal error");
        }
    }

    /**
     * Get all users with security filtering
     */
    public List<UserDTO> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            return users.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving users: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Initiate password recovery
     */
    public PasswordRecoveryResult initiatePasswordRecovery(String identifier, HttpServletRequest request) {
        logger.info("Password recovery initiated for identifier: {}", identifier);

        try {
            identifier = sanitizeInput(identifier);

            if (identifier == null || identifier.isEmpty()) {
                return new PasswordRecoveryResult(false, "Email or phone number is required");
            }

            Optional<User> userOpt = userRepository.findByUsernameOrEmailOrPhone(identifier);

            if (userOpt.isEmpty()) {
                // Don't reveal if user exists - always return success message
                return new PasswordRecoveryResult(true, "If the provided information matches our records, you will receive recovery instructions.");
            }

            User user = userOpt.get();

            // Generate secure reset token
            String resetToken = generateSecureToken();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1)); // 1 hour expiry

            userRepository.save(user);

            // Send recovery email/SMS (async)
            String finalIdentifier = identifier;
            CompletableFuture.runAsync(() -> {
                try {
                    if (finalIdentifier.contains("@") && user.getEmail() != null) {
                        // Send email
                        // emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetToken);
                    } else if (user.getPhone() != null) {
                        // Send SMS
                        // smsService.sendPasswordResetSMS(user.getPhone(), user.getUsername(), resetToken);
                    }
                } catch (Exception e) {
                    logger.error("Error sending password recovery for user {}: {}", user.getUsername(), e.getMessage());
                }
            });

            logger.info("Password recovery token generated for user: {}", user.getUsername());

            return new PasswordRecoveryResult(true, "If the provided information matches our records, you will receive recovery instructions.");

        } catch (Exception e) {
            logger.error("Password recovery error for identifier {}: {}", identifier, e.getMessage(), e);
            return new PasswordRecoveryResult(false, "Recovery failed due to internal error");
        }
    }

    /**
     * Reset password with token
     */
    public PasswordResetResult resetPassword(String token, String newPassword) {
        logger.info("Password reset attempt with token: {}", token.substring(0, Math.min(token.length(), 8)) + "...");

        try {
            if (token == null || token.isEmpty() || newPassword == null || newPassword.isEmpty()) {
                return new PasswordResetResult(false, "Token and new password are required");
            }

            Optional<User> userOpt = userRepository.findByValidPasswordResetToken(token, LocalDateTime.now());

            if (userOpt.isEmpty()) {
                return new PasswordResetResult(false, "Invalid or expired reset token");
            }

            User user = userOpt.get();

            // Validate new password
            PasswordValidator.ValidationResult passwordResult = passwordValidator.validatePassword(newPassword);
            if (!passwordResult.isValid()) {
                String errorMessage = "Password validation failed: " + String.join(", ", passwordResult.getErrors());
                return new PasswordResetResult(false, errorMessage);
            }

            // Hash new password
            String hashedPassword = passwordValidator.hashPassword(newPassword);

            // Update user
            user.setPasswordHash(hashedPassword);
            user.setPasswordResetToken(null);
            user.setPasswordResetExpiry(null);
            user.setFailedLoginAttempts(0); // Reset failed attempts
            user.setAccountLocked(false); // Unlock if locked

            userRepository.save(user);

            // Invalidate all sessions for security
            invalidateUserSessions(user.getUsername());

            logger.info("Password reset successful for user: {}", user.getUsername());

            return new PasswordResetResult(true, "Password reset successfully");

        } catch (Exception e) {
            logger.error("Password reset error: {}", e.getMessage(), e);
            return new PasswordResetResult(false, "Password reset failed due to internal error");
        }
    }

    /**
     * Check password strength
     */
    public PasswordStrengthResult checkPasswordStrength(String password) {
        try {
            PasswordValidator.ValidationResult result = passwordValidator.validatePassword(password);
            return new PasswordStrengthResult(
                    result.getStrength().toString(),
                    result.getScore(),
                    result.getErrors(),
                    result.getWarnings(),
                    result.isValid()
            );
        } catch (Exception e) {
            logger.error("Error checking password strength: {}", e.getMessage(), e);
            return new PasswordStrengthResult("Error", 0,
                    Arrays.asList("Unable to check password strength"), new ArrayList<>(), false);
        }
    }

    // Helper methods

    private ValidationResult validateRegistrationData(String username, String email, String phone, String password) {
        // Check required fields
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, "Username is required");
        }

        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, "Password is required");
        }

        if ((email == null || email.trim().isEmpty()) && (phone == null || phone.trim().isEmpty())) {
            return new ValidationResult(false, "Either email or phone number is required");
        }

        // Check duplicates
        if (userRepository.existsByUsername(username)) {
            return new ValidationResult(false, "Username already exists");
        }

        if (email != null && !email.trim().isEmpty() && userRepository.existsByEmail(email)) {
            return new ValidationResult(false, "Email address already exists");
        }

        if (phone != null && !phone.trim().isEmpty() && userRepository.existsByPhone(phone)) {
            return new ValidationResult(false, "Phone number already exists");
        }

        return new ValidationResult(true, "Validation passed");
    }

    private String buildVerificationMessage(String email, String phone) {
        if (email != null && !email.isEmpty() && phone != null && !phone.isEmpty()) {
            return "Choose either email or SMS verification to activate your account.";
        } else if (email != null && !email.isEmpty()) {
            return "Please verify your email to activate your account.";
        } else if (phone != null && !phone.isEmpty()) {
            return "Please verify your phone number to activate your account.";
        }
        return "";
    }

    private String getAccountStatusMessage(User user) {
        if (!user.isAccountActive()) {
            return "Account not verified. Please verify your email/phone before logging in.";
        } else if (user.isAccountLocked()) {
            return "Account is locked. Please contact support.";
        } else if (user.isAccountExpired()) {
            return "Account has expired due to inactivity. Please contact support.";
        }
        return "Account access denied.";
    }

    private UserSession createUserSession(User user, HttpServletRequest request) {
        String sessionId = generateSecureToken();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(sessionTimeoutMinutes);

        UserSession session = new UserSession(
                sessionId,
                user.getUsername(),
                user.getId(),
                LocalDateTime.now(),
                expiryTime,
                getClientIpAddress(request),
                request.getHeader("User-Agent")
        );

        activeSessions.put(sessionId, session);
        return session;
    }

    private void invalidateUserSessions(String username) {
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getUsername().equals(username));
    }

    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                user.isAccountActive(),
                user.getStatus().toString(),
                user.getRole().toString(),
                user.getCreatedAt(),
                user.getLastLogin()
        );
    }

    private String sanitizeInput(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private String getClientIpAddress(HttpServletRequest request) {
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

    private String extractTimezoneFromRequest(HttpServletRequest request) {
        // Try to extract timezone from Accept-Language or custom header
        String timezone = request.getHeader("X-Timezone");
        return timezone != null ? timezone : "UTC";
    }

    private String extractLanguageFromRequest(HttpServletRequest request) {
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            return acceptLanguage.split(",")[0].substring(0, 2);
        }
        return "en";
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }

    // Result classes
    public static class UserRegistrationResult {
        private final boolean success;
        private final String message;
        private final User user;

        public UserRegistrationResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
    }

    public static class LoginResult {
        private final boolean success;
        private final String message;
        private final User user;
        private final UserSession session;

        public LoginResult(boolean success, String message, User user, UserSession session) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.session = session;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
        public UserSession getSession() { return session; }
    }

    public static class DeletionResult {
        private final boolean success;
        private final String message;

        public DeletionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class PasswordRecoveryResult {
        private final boolean success;
        private final String message;

        public PasswordRecoveryResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class PasswordResetResult {
        private final boolean success;
        private final String message;

        public PasswordResetResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class PasswordStrengthResult {
        private final String strength;
        private final int score;
        private final List<String> errors;
        private final List<String> warnings;
        private final boolean valid;

        public PasswordStrengthResult(String strength, int score, List<String> errors,
                                      List<String> warnings, boolean valid) {
            this.strength = strength;
            this.score = score;
            this.errors = errors;
            this.warnings = warnings;
            this.valid = valid;
        }

        public String getStrength() { return strength; }
        public int getScore() { return score; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean isValid() { return valid; }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    // DTO classes
    public static class UserDTO {
        private final Long id;
        private final String username;
        private final String email;
        private final String phone;
        private final boolean emailVerified;
        private final boolean phoneVerified;
        private final boolean accountActive;
        private final String status;
        private final String role;
        private final LocalDateTime createdAt;
        private final LocalDateTime lastLogin;

        public UserDTO(Long id, String username, String email, String phone,
                       boolean emailVerified, boolean phoneVerified, boolean accountActive,
                       String status, String role, LocalDateTime createdAt, LocalDateTime lastLogin) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.phone = phone;
            this.emailVerified = emailVerified;
            this.phoneVerified = phoneVerified;
            this.accountActive = accountActive;
            this.status = status;
            this.role = role;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
        }

        // Getters
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public boolean isEmailVerified() { return emailVerified; }
        public boolean isPhoneVerified() { return phoneVerified; }
        public boolean isAccountActive() { return accountActive; }
        public String getStatus() { return status; }
        public String getRole() { return role; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastLogin() { return lastLogin; }
    }

    public static class UserSession {
        private final String sessionId;
        private final String username;
        private final Long userId;
        private final LocalDateTime createdAt;
        private final LocalDateTime expiresAt;
        private final String ipAddress;
        private final String userAgent;

        public UserSession(String sessionId, String username, Long userId,
                           LocalDateTime createdAt, LocalDateTime expiresAt,
                           String ipAddress, String userAgent) {
            this.sessionId = sessionId;
            this.username = username;
            this.userId = userId;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
        }

        // Getters
        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
        public Long getUserId() { return userId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}