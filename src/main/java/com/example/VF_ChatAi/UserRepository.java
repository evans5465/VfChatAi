package com.example.VF_ChatAi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Basic finders
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    // Existence checks
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // Combined finders for login/recovery
    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier OR u.phone = :identifier")
    Optional<User> findByUsernameOrEmailOrPhone(@Param("identifier") String identifier);

    // Email and phone recovery
    Optional<User> findByEmailOrPhone(String email, String phone);

    // Password reset
    Optional<User> findByPasswordResetToken(String token);

    @Query("SELECT u FROM User u WHERE u.passwordResetToken = :token AND u.passwordResetExpiry > :now")
    Optional<User> findByValidPasswordResetToken(@Param("token") String token, @Param("now") LocalDateTime now);

    // Account status queries
    List<User> findByAccountActiveFalse();
    List<User> findByAccountLockedTrue();
    List<User> findByStatus(User.AccountStatus status);

    // Verification queries
    @Query("SELECT u FROM User u WHERE u.emailVerificationCode = :code AND u.emailCodeExpiry > :now AND u.username = :username")
    Optional<User> findByValidEmailVerificationCode(@Param("username") String username, @Param("code") String code, @Param("now") LocalDateTime now);

    @Query("SELECT u FROM User u WHERE u.phoneVerificationCode = :code AND u.phoneCodeExpiry > :now AND u.username = :username")
    Optional<User> findByValidPhoneVerificationCode(@Param("username") String username, @Param("code") String code, @Param("now") LocalDateTime now);

    // Cleanup expired codes
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.emailVerificationCode = NULL, u.emailCodeExpiry = NULL WHERE u.emailCodeExpiry < :now")
    int clearExpiredEmailVerificationCodes(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.phoneVerificationCode = NULL, u.phoneCodeExpiry = NULL WHERE u.phoneCodeExpiry < :now")
    int clearExpiredPhoneVerificationCodes(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.passwordResetToken = NULL, u.passwordResetExpiry = NULL WHERE u.passwordResetExpiry < :now")
    int clearExpiredPasswordResetTokens(@Param("now") LocalDateTime now);

    // Activity tracking
    @Query("SELECT u FROM User u WHERE u.lastLogin < :cutoff")
    List<User> findInactiveUsers(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT u FROM User u WHERE u.createdAt > :since")
    List<User> findRecentUsers(@Param("since") LocalDateTime since);

    // Statistics queries
    @Query("SELECT COUNT(u) FROM User u WHERE u.accountActive = true")
    long countActiveUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countUsersByStatus(@Param("status") User.AccountStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.emailVerified = true")
    long countEmailVerifiedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.phoneVerified = true")
    long countPhoneVerifiedUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since")
    long countUsersCreatedSince(@Param("since") LocalDateTime since);

    // Security queries
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :threshold")
    List<User> findUsersWithFailedLoginAttempts(@Param("threshold") int threshold);

    @Query("SELECT u FROM User u WHERE u.lastLoginIp = :ip AND u.lastLogin > :since")
    List<User> findUsersByRecentIpAddress(@Param("ip") String ip, @Param("since") LocalDateTime since);

    // Bulk operations
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.accountLocked = true, u.status = 'LOCKED' WHERE u.failedLoginAttempts >= :threshold")
    int lockUsersWithTooManyFailedAttempts(@Param("threshold") int threshold);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.status = 'INACTIVE' WHERE u.lastLogin < :cutoff AND u.status = 'ACTIVE'")
    int markInactiveUsers(@Param("cutoff") LocalDateTime cutoff);

    // Advanced search
    @Query("SELECT u FROM User u WHERE " +
            "(:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
            "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
            "(:status IS NULL OR u.status = :status) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:active IS NULL OR u.accountActive = :active)")
    List<User> findUsersWithFilters(
            @Param("username") String username,
            @Param("email") String email,
            @Param("status") User.AccountStatus status,
            @Param("role") User.UserRole role,
            @Param("active") Boolean active
    );

    // Verification attempt tracking
    @Query("SELECT u FROM User u WHERE u.emailVerificationAttempts >= :maxAttempts")
    List<User> findUsersWithMaxEmailVerificationAttempts(@Param("maxAttempts") int maxAttempts);

    @Query("SELECT u FROM User u WHERE u.phoneVerificationAttempts >= :maxAttempts")
    List<User> findUsersWithMaxPhoneVerificationAttempts(@Param("maxAttempts") int maxAttempts);

    // Email/Phone uniqueness validation (excluding specific user)
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeId")
    boolean existsByEmailExcludingUser(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phone = :phone AND u.id != :excludeId")
    boolean existsByPhoneExcludingUser(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username AND u.id != :excludeId")
    boolean existsByUsernameExcludingUser(@Param("username") String username, @Param("excludeId") Long excludeId);
}