package com.example.VF_ChatAi;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_phone", columnList = "phone"),
        @Index(name = "idx_active", columnList = "accountActive")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;

    @Column(name = "password_hash", nullable = false)
    @NotBlank(message = "Password is required")
    @JsonIgnore // Never serialize password in JSON responses
    private String passwordHash;

    // Add this field to User.java entity
    @Column(name = "original_password")
    private String originalPassword;

    public String getOriginalPassword() { return originalPassword; }
    public void setOriginalPassword(String originalPassword) { this.originalPassword = originalPassword; }


    @Column(name = "email", unique = true, length = 100)
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Column(name = "phone", unique = true, length = 20)
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Phone number must be in international format (+1234567890)")
    private String phone;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "account_active", nullable = false)
    private boolean accountActive = false;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // Verification fields
    @Column(name = "email_verification_code")
    @JsonIgnore
    private String emailVerificationCode;

    @Column(name = "phone_verification_code")
    @JsonIgnore
    private String phoneVerificationCode;

    @Column(name = "email_code_expiry")
    @JsonIgnore
    private LocalDateTime emailCodeExpiry;

    @Column(name = "phone_code_expiry")
    @JsonIgnore
    private LocalDateTime phoneCodeExpiry;

    @Column(name = "email_verification_attempts")
    private int emailVerificationAttempts = 0;

    @Column(name = "phone_verification_attempts")
    private int phoneVerificationAttempts = 0;

    // Password reset fields
    @Column(name = "password_reset_token")
    @JsonIgnore
    private String passwordResetToken;

    @Column(name = "password_reset_expiry")
    @JsonIgnore
    private LocalDateTime passwordResetExpiry;

    // Audit fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by_ip")
    private String createdByIp;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    // Profile fields
    @Column(name = "first_name", length = 50)
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Column(name = "last_name", length = 50)
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";

    @Column(name = "language", length = 10)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

    // Constructors
    public User() {}

    public User(String username, String passwordHash, String email, String phone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.phone = phone;
        this.passwordChangedAt = LocalDateTime.now();

        // Set default values to avoid validation issues
        this.emailVerified = false;
        this.phoneVerified = false;
        this.accountActive = false;
        this.accountLocked = false;
        this.failedLoginAttempts = 0;
        this.emailVerificationAttempts = 0;
        this.phoneVerificationAttempts = 0;
        this.role = UserRole.USER;
        this.status = AccountStatus.PENDING_VERIFICATION;
        this.timezone = "UTC";
        this.language = "en";
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = LocalDateTime.now();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        if (emailVerified) {
            this.emailVerificationCode = null;
            this.emailCodeExpiry = null;
            this.emailVerificationAttempts = 0;
        }
    }

    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
        if (phoneVerified) {
            this.phoneVerificationCode = null;
            this.phoneCodeExpiry = null;
            this.phoneVerificationAttempts = 0;
        }
    }

    // Add this method for debugging
    public boolean isValidForSave() {
        if (username == null || username.trim().isEmpty()) {
            System.out.println("DEBUG: Username is null or empty");
            return false;
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            System.out.println("DEBUG: Password hash is null or empty");
            return false;
        }
        System.out.println("DEBUG: User validation passed - username: " + username + ", passwordHash length: " + passwordHash.length());
        return true;
    }

    public boolean isAccountActive() { return accountActive; }
    public void setAccountActive(boolean accountActive) {
        this.accountActive = accountActive;
        if (accountActive) {
            this.status = AccountStatus.ACTIVE;
        }
    }

    public boolean isAccountLocked() { return accountLocked; }
    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
        if (accountLocked) {
            this.status = AccountStatus.LOCKED;
        }
    }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }

    public String getEmailVerificationCode() { return emailVerificationCode; }
    public void setEmailVerificationCode(String emailVerificationCode) { this.emailVerificationCode = emailVerificationCode; }

    public String getPhoneVerificationCode() { return phoneVerificationCode; }
    public void setPhoneVerificationCode(String phoneVerificationCode) { this.phoneVerificationCode = phoneVerificationCode; }

    public LocalDateTime getEmailCodeExpiry() { return emailCodeExpiry; }
    public void setEmailCodeExpiry(LocalDateTime emailCodeExpiry) { this.emailCodeExpiry = emailCodeExpiry; }

    public LocalDateTime getPhoneCodeExpiry() { return phoneCodeExpiry; }
    public void setPhoneCodeExpiry(LocalDateTime phoneCodeExpiry) { this.phoneCodeExpiry = phoneCodeExpiry; }

    public int getEmailVerificationAttempts() { return emailVerificationAttempts; }
    public void setEmailVerificationAttempts(int emailVerificationAttempts) { this.emailVerificationAttempts = emailVerificationAttempts; }

    public int getPhoneVerificationAttempts() { return phoneVerificationAttempts; }
    public void setPhoneVerificationAttempts(int phoneVerificationAttempts) { this.phoneVerificationAttempts = phoneVerificationAttempts; }

    public String getPasswordResetToken() { return passwordResetToken; }
    public void setPasswordResetToken(String passwordResetToken) { this.passwordResetToken = passwordResetToken; }

    public LocalDateTime getPasswordResetExpiry() { return passwordResetExpiry; }
    public void setPasswordResetExpiry(LocalDateTime passwordResetExpiry) { this.passwordResetExpiry = passwordResetExpiry; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedByIp() { return createdByIp; }
    public void setCreatedByIp(String createdByIp) { this.createdByIp = createdByIp; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    // Helper methods
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return username;
    }

    public boolean canLogin() {
        return accountActive && !accountLocked && !isAccountExpired();
    }

    public boolean isAccountExpired() {
        // Account expires if not accessed for 365 days
        return lastLogin != null && lastLogin.isBefore(LocalDateTime.now().minusDays(365));
    }

    public boolean needsVerification() {
        return !accountActive && (hasEmailButNotVerified() || hasPhoneButNotVerified());
    }

    public boolean hasEmailButNotVerified() {
        return email != null && !email.isEmpty() && !emailVerified;
    }

    public boolean hasPhoneButNotVerified() {
        return phone != null && !phone.isEmpty() && !phoneVerified;
    }

    public boolean isVerificationCodeExpired(String verificationType) {
        LocalDateTime expiry = "email".equals(verificationType) ? emailCodeExpiry : phoneCodeExpiry;
        return expiry == null || LocalDateTime.now().isAfter(expiry);
    }

    public boolean hasReachedMaxVerificationAttempts(String verificationType) {
        int attempts = "email".equals(verificationType) ? emailVerificationAttempts : phoneVerificationAttempts;
        return attempts >= 5; // Max 5 attempts per verification type
    }

    public void incrementVerificationAttempts(String verificationType) {
        if ("email".equals(verificationType)) {
            this.emailVerificationAttempts++;
        } else if ("phone".equals(verificationType)) {
            this.phoneVerificationAttempts++;
        }
    }

    public void resetVerificationAttempts(String verificationType) {
        if ("email".equals(verificationType)) {
            this.emailVerificationAttempts = 0;
        } else if ("phone".equals(verificationType)) {
            this.phoneVerificationAttempts = 0;
        }
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.accountLocked = true;
            this.status = AccountStatus.LOCKED;
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        if (this.accountLocked && this.status == AccountStatus.LOCKED) {
            this.accountLocked = false;
            this.status = this.accountActive ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION;
        }
    }

    public void activateAccount() {
        this.accountActive = true;
        this.status = AccountStatus.ACTIVE;
        this.accountLocked = false;
        this.failedLoginAttempts = 0;
    }

    // toString, equals, hashCode
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", emailVerified=" + emailVerified +
                ", phoneVerified=" + phoneVerified +
                ", accountActive=" + accountActive +
                ", status=" + status +
                ", role=" + role +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }



    // Enums
    public enum UserRole {
        USER, ADMIN, MODERATOR
    }

    public enum AccountStatus {
        PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, INACTIVE
    }
}