package com.example.VF_ChatAi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class VerificationService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SMSService smsService;

    @Value("${verification.code.length:6}")
    private int codeLength;

    @Value("${verification.code.expiry.minutes:15}")
    private int codeExpiryMinutes;

    @Value("${verification.max.attempts:5}")
    private int maxVerificationAttempts;

    @Value("${verification.resend.cooldown.seconds:60}")
    private int resendCooldownSeconds;

    // Rate limiting maps
    private final Map<String, LocalDateTime> lastEmailSent = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastSmsSent = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyEmailCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailySmsCount = new ConcurrentHashMap<>();

    private final SecureRandom secureRandom = new SecureRandom();

    // Constants
    private static final int MAX_DAILY_EMAILS = 10;
    private static final int MAX_DAILY_SMS = 5;
    private static final String EMAIL_TYPE = "email";
    private static final String SMS_TYPE = "sms";

    /**
     * Generate secure verification code
     */
    public String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(secureRandom.nextInt(10));
        }
        String generatedCode = code.toString();

        logger.debug("Generated verification code of length: {}", codeLength);
        return generatedCode;
    }

    /**
     * Send email verification with comprehensive error handling
     */
    @Async
    public CompletableFuture<VerificationResult> sendEmailVerificationAsync(User user) {
        return CompletableFuture.completedFuture(sendEmailVerification(user));
    }

    public VerificationResult sendEmailVerification(User user) {
        logger.info("Attempting to send email verification for user: {}", user.getUsername());

        try {
            // Validation checks
            VerificationResult validationResult = validateVerificationRequest(user, EMAIL_TYPE);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }

            // Rate limiting check
            if (!canSendEmail(user)) {
                logger.warn("Email rate limit exceeded for user: {}", user.getUsername());
                return new VerificationResult(false, "Rate limit exceeded. Please wait before requesting another code.",
                        VerificationResult.ErrorType.RATE_LIMITED);
            }

            // Generate and store code
            String code = generateVerificationCode();
            user.setEmailVerificationCode(code);
            user.setEmailCodeExpiry(LocalDateTime.now().plusMinutes(codeExpiryMinutes));

            // Reset attempts on new code generation
            user.setEmailVerificationAttempts(0);

            userRepository.save(user);

            // Send email
            boolean emailSent = emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), code);

            if (emailSent) {
                // Update rate limiting
                updateEmailRateLimit(user);

                logger.info("Email verification sent successfully for user: {}", user.getUsername());
                return new VerificationResult(true, "Verification code sent to your email address", null);
            } else {
                logger.error("Failed to send email verification for user: {}", user.getUsername());
                return new VerificationResult(false, "Failed to send verification email. Please try again later.",
                        VerificationResult.ErrorType.DELIVERY_FAILED);
            }

        } catch (Exception e) {
            logger.error("Error sending email verification for user: {}: {}", user.getUsername(), e.getMessage(), e);
            return new VerificationResult(false, "Internal error occurred. Please try again later.",
                    VerificationResult.ErrorType.INTERNAL_ERROR);
        }
    }

    /**
     * Send SMS verification with comprehensive error handling
     */
    @Async
    public CompletableFuture<VerificationResult> sendSMSVerificationAsync(User user) {
        return CompletableFuture.completedFuture(sendSMSVerification(user));
    }

    public VerificationResult sendSMSVerification(User user) {
        logger.info("Attempting to send SMS verification for user: {}", user.getUsername());

        try {
            // Validation checks
            VerificationResult validationResult = validateVerificationRequest(user, SMS_TYPE);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }

            // Rate limiting check
            if (!canSendSms(user)) {
                logger.warn("SMS rate limit exceeded for user: {}", user.getUsername());
                return new VerificationResult(false, "SMS rate limit exceeded. Please wait before requesting another code.",
                        VerificationResult.ErrorType.RATE_LIMITED);
            }

            // Generate and store code
            String code = generateVerificationCode();
            user.setPhoneVerificationCode(code);
            user.setPhoneCodeExpiry(LocalDateTime.now().plusMinutes(codeExpiryMinutes));

            // Reset attempts on new code generation
            user.setPhoneVerificationAttempts(0);

            userRepository.save(user);

            // Send SMS
            boolean smsSent = smsService.sendVerificationSMS(user.getPhone(), user.getUsername(), code);

            if (smsSent) {
                // Update rate limiting
                updateSmsRateLimit(user);

                logger.info("SMS verification sent successfully for user: {}", user.getUsername());
                return new VerificationResult(true, "Verification code sent to your phone number", null);
            } else {
                logger.error("Failed to send SMS verification for user: {}", user.getUsername());
                return new VerificationResult(false, "Failed to send SMS verification. Please try again later.",
                        VerificationResult.ErrorType.DELIVERY_FAILED);
            }

        } catch (Exception e) {
            logger.error("Error sending SMS verification for user: {}: {}", user.getUsername(), e.getMessage(), e);
            return new VerificationResult(false, "Internal error occurred. Please try again later.",
                    VerificationResult.ErrorType.INTERNAL_ERROR);
        }
    }

    /**
     * Verify email code with comprehensive validation
     */
    public VerificationResult verifyEmailCode(String username, String code) {
        logger.info("Attempting email verification for user: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                logger.warn("Email verification attempted for non-existent user: {}", username);
                return new VerificationResult(false, "User not found", VerificationResult.ErrorType.USER_NOT_FOUND);
            }

            User user = userOpt.get();

            // Check if already verified
            if (user.isEmailVerified()) {
                logger.info("Email already verified for user: {}", username);
                return new VerificationResult(true, "Email is already verified", null);
            }

            // Check if code exists
            if (user.getEmailVerificationCode() == null) {
                logger.warn("No email verification code found for user: {}", username);
                return new VerificationResult(false, "No verification code found. Please request a new one.",
                        VerificationResult.ErrorType.NO_CODE_FOUND);
            }

            // Check attempts
            if (user.hasReachedMaxVerificationAttempts(EMAIL_TYPE)) {
                logger.warn("Max email verification attempts reached for user: {}", username);
                return new VerificationResult(false, "Maximum verification attempts exceeded. Please request a new code.",
                        VerificationResult.ErrorType.MAX_ATTEMPTS_EXCEEDED);
            }

            // Check expiry
            if (user.isVerificationCodeExpired(EMAIL_TYPE)) {
                logger.warn("Email verification code expired for user: {}", username);
                return new VerificationResult(false, "Verification code has expired. Please request a new one.",
                        VerificationResult.ErrorType.CODE_EXPIRED);
            }

            // Increment attempt count
            user.incrementVerificationAttempts(EMAIL_TYPE);

            // Verify code
            if (!user.getEmailVerificationCode().equals(code)) {
                userRepository.save(user);
                logger.warn("Invalid email verification code for user: {}", username);

                int remainingAttempts = maxVerificationAttempts - user.getEmailVerificationAttempts();
                return new VerificationResult(false,
                        String.format("Invalid verification code. %d attempts remaining.", remainingAttempts),
                        VerificationResult.ErrorType.INVALID_CODE);
            }

            // Success! Mark email as verified
            user.setEmailVerified(true);
            user.setEmailVerificationCode(null);
            user.setEmailCodeExpiry(null);
            user.setEmailVerificationAttempts(0);

            // Activate account if this completes verification requirements
            if (shouldActivateAccount(user)) {
                user.activateAccount();
                logger.info("Account activated for user: {}", username);
            }

            userRepository.save(user);

            logger.info("Email verification successful for user: {}", username);
            return new VerificationResult(true, "Email verified successfully! " +
                    (user.isAccountActive() ? "Account is now active." : ""), null);

        } catch (Exception e) {
            logger.error("Error during email verification for user: {}: {}", username, e.getMessage(), e);
            return new VerificationResult(false, "Internal error occurred. Please try again later.",
                    VerificationResult.ErrorType.INTERNAL_ERROR);
        }
    }

    /**
     * Verify SMS code with comprehensive validation
     */
    public VerificationResult verifySMSCode(String username, String code) {
        logger.info("Attempting SMS verification for user: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                logger.warn("SMS verification attempted for non-existent user: {}", username);
                return new VerificationResult(false, "User not found", VerificationResult.ErrorType.USER_NOT_FOUND);
            }

            User user = userOpt.get();

            // Check if already verified
            if (user.isPhoneVerified()) {
                logger.info("Phone already verified for user: {}", username);
                return new VerificationResult(true, "Phone is already verified", null);
            }

            // Check if code exists
            if (user.getPhoneVerificationCode() == null) {
                logger.warn("No SMS verification code found for user: {}", username);
                return new VerificationResult(false, "No verification code found. Please request a new one.",
                        VerificationResult.ErrorType.NO_CODE_FOUND);
            }

            // Check attempts
            if (user.hasReachedMaxVerificationAttempts(SMS_TYPE)) {
                logger.warn("Max SMS verification attempts reached for user: {}", username);
                return new VerificationResult(false, "Maximum verification attempts exceeded. Please request a new code.",
                        VerificationResult.ErrorType.MAX_ATTEMPTS_EXCEEDED);
            }

            // Check expiry
            if (user.isVerificationCodeExpired(SMS_TYPE)) {
                logger.warn("SMS verification code expired for user: {}", username);
                return new VerificationResult(false, "Verification code has expired. Please request a new one.",
                        VerificationResult.ErrorType.CODE_EXPIRED);
            }

            // Increment attempt count
            user.incrementVerificationAttempts(SMS_TYPE);

            // Verify code
            if (!user.getPhoneVerificationCode().equals(code)) {
                userRepository.save(user);
                logger.warn("Invalid SMS verification code for user: {}", username);

                int remainingAttempts = maxVerificationAttempts - user.getPhoneVerificationAttempts();
                return new VerificationResult(false,
                        String.format("Invalid verification code. %d attempts remaining.", remainingAttempts),
                        VerificationResult.ErrorType.INVALID_CODE);
            }

            // Success! Mark phone as verified
            user.setPhoneVerified(true);
            user.setPhoneVerificationCode(null);
            user.setPhoneCodeExpiry(null);
            user.setPhoneVerificationAttempts(0);

            // Activate account if this completes verification requirements
            if (shouldActivateAccount(user)) {
                user.activateAccount();
                logger.info("Account activated for user: {}", username);
            }

            userRepository.save(user);

            logger.info("SMS verification successful for user: {}", username);
            return new VerificationResult(true, "Phone verified successfully! " +
                    (user.isAccountActive() ? "Account is now active." : ""), null);

        } catch (Exception e) {
            logger.error("Error during SMS verification for user: {}: {}", username, e.getMessage(), e);
            return new VerificationResult(false, "Internal error occurred. Please try again later.",
                    VerificationResult.ErrorType.INTERNAL_ERROR);
        }
    }

    /**
     * Validate verification request
     */
    private VerificationResult validateVerificationRequest(User user, String verificationType) {
        if (user == null) {
            return new VerificationResult(false, "User not found", VerificationResult.ErrorType.USER_NOT_FOUND);
        }

        if (EMAIL_TYPE.equals(verificationType)) {
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                return new VerificationResult(false, "No email address associated with this account",
                        VerificationResult.ErrorType.NO_CONTACT_INFO);
            }

            if (user.isEmailVerified()) {
                return new VerificationResult(false, "Email is already verified",
                        VerificationResult.ErrorType.ALREADY_VERIFIED);
            }
        } else if (SMS_TYPE.equals(verificationType)) {
            if (user.getPhone() == null || user.getPhone().isEmpty()) {
                return new VerificationResult(false, "No phone number associated with this account",
                        VerificationResult.ErrorType.NO_CONTACT_INFO);
            }

            if (user.isPhoneVerified()) {
                return new VerificationResult(false, "Phone is already verified",
                        VerificationResult.ErrorType.ALREADY_VERIFIED);
            }
        }

        return new VerificationResult(true, "Validation passed", null);
    }

    /**
     * Check if account should be activated
     */
    private boolean shouldActivateAccount(User user) {
        // Account is activated when at least one verification method is complete
        // and the user has provided at least one contact method
        boolean hasVerifiedContact = user.isEmailVerified() || user.isPhoneVerified();
        return hasVerifiedContact && !user.isAccountActive();
    }

    /**
     * Rate limiting for email
     */
    private boolean canSendEmail(User user) {
        String key = user.getUsername();
        LocalDateTime now = LocalDateTime.now();

        // Check cooldown period
        LocalDateTime lastSent = lastEmailSent.get(key);
        if (lastSent != null && lastSent.plusSeconds(resendCooldownSeconds).isAfter(now)) {
            return false;
        }

        // Check daily limit
        Integer dailyCount = dailyEmailCount.get(key);
        return dailyCount == null || dailyCount < MAX_DAILY_EMAILS;
    }

    /**
     * Rate limiting for SMS
     */
    private boolean canSendSms(User user) {
        String key = user.getUsername();
        LocalDateTime now = LocalDateTime.now();

        // Check cooldown period
        LocalDateTime lastSent = lastSmsSent.get(key);
        if (lastSent != null && lastSent.plusSeconds(resendCooldownSeconds).isAfter(now)) {
            return false;
        }

        // Check daily limit
        Integer dailyCount = dailySmsCount.get(key);
        return dailyCount == null || dailyCount < MAX_DAILY_SMS;
    }

    /**
     * Update email rate limiting
     */
    private void updateEmailRateLimit(User user) {
        String key = user.getUsername();
        LocalDateTime now = LocalDateTime.now();

        lastEmailSent.put(key, now);
        dailyEmailCount.merge(key, 1, Integer::sum);
    }

    /**
     * Update SMS rate limiting
     */
    private void updateSmsRateLimit(User user) {
        String key = user.getUsername();
        LocalDateTime now = LocalDateTime.now();

        lastSmsSent.put(key, now);
        dailySmsCount.merge(key, 1, Integer::sum);
    }

    /**
     * Cleanup expired verification codes - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredCodes() {
        logger.info("Starting cleanup of expired verification codes");

        try {
            LocalDateTime now = LocalDateTime.now();

            int expiredEmailCodes = userRepository.clearExpiredEmailVerificationCodes(now);
            int expiredPhoneCodes = userRepository.clearExpiredPhoneVerificationCodes(now);
            int expiredResetTokens = userRepository.clearExpiredPasswordResetTokens(now);

            logger.info("Cleanup completed - Email codes: {}, Phone codes: {}, Reset tokens: {}",
                    expiredEmailCodes, expiredPhoneCodes, expiredResetTokens);

        } catch (Exception e) {
            logger.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Reset daily rate limits - runs daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyLimits() {
        logger.info("Resetting daily rate limits");
        dailyEmailCount.clear();
        dailySmsCount.clear();
    }

    /**
     * Get verification statistics for a user
     */
    public VerificationStats getVerificationStats(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        return new VerificationStats(
                user.isEmailVerified(),
                user.isPhoneVerified(),
                user.isAccountActive(),
                user.getEmailVerificationAttempts(),
                user.getPhoneVerificationAttempts(),
                user.getEmailCodeExpiry(),
                user.getPhoneCodeExpiry()
        );
    }

    /**
     * Verification result class
     */
    public static class VerificationResult {
        private final boolean success;
        private final String message;
        private final ErrorType errorType;

        public VerificationResult(boolean success, String message, ErrorType errorType) {
            this.success = success;
            this.message = message;
            this.errorType = errorType;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ErrorType getErrorType() { return errorType; }

        public enum ErrorType {
            USER_NOT_FOUND,
            NO_CONTACT_INFO,
            ALREADY_VERIFIED,
            RATE_LIMITED,
            DELIVERY_FAILED,
            NO_CODE_FOUND,
            CODE_EXPIRED,
            INVALID_CODE,
            MAX_ATTEMPTS_EXCEEDED,
            INTERNAL_ERROR
        }
    }

    /**
     * Verification statistics class
     */
    public static class VerificationStats {
        private final boolean emailVerified;
        private final boolean phoneVerified;
        private final boolean accountActive;
        private final int emailAttempts;
        private final int phoneAttempts;
        private final LocalDateTime emailCodeExpiry;
        private final LocalDateTime phoneCodeExpiry;

        public VerificationStats(boolean emailVerified, boolean phoneVerified, boolean accountActive,
                                 int emailAttempts, int phoneAttempts,
                                 LocalDateTime emailCodeExpiry, LocalDateTime phoneCodeExpiry) {
            this.emailVerified = emailVerified;
            this.phoneVerified = phoneVerified;
            this.accountActive = accountActive;
            this.emailAttempts = emailAttempts;
            this.phoneAttempts = phoneAttempts;
            this.emailCodeExpiry = emailCodeExpiry;
            this.phoneCodeExpiry = phoneCodeExpiry;
        }

        // Getters
        public boolean isEmailVerified() { return emailVerified; }
        public boolean isPhoneVerified() { return phoneVerified; }
        public boolean isAccountActive() { return accountActive; }
        public int getEmailAttempts() { return emailAttempts; }
        public int getPhoneAttempts() { return phoneAttempts; }
        public LocalDateTime getEmailCodeExpiry() { return emailCodeExpiry; }
        public LocalDateTime getPhoneCodeExpiry() { return phoneCodeExpiry; }

        public boolean hasActiveEmailCode() {
            return emailCodeExpiry != null && LocalDateTime.now().isBefore(emailCodeExpiry);
        }

        public boolean hasActivePhoneCode() {
            return phoneCodeExpiry != null && LocalDateTime.now().isBefore(phoneCodeExpiry);
        }

        public boolean needsVerification() {
            return !emailVerified && !phoneVerified;
        }

        public String getVerificationSummary() {
            StringBuilder summary = new StringBuilder();

            if (emailVerified) {
                summary.append("Email verified. ");
            } else if (hasActiveEmailCode()) {
                summary.append("Email verification pending. ");
            }

            if (phoneVerified) {
                summary.append("Phone verified. ");
            } else if (hasActivePhoneCode()) {
                summary.append("Phone verification pending. ");
            }

            if (accountActive) {
                summary.append("Account is active.");
            } else {
                summary.append("Account requires verification.");
            }

            return summary.toString().trim();
        }
    }
}