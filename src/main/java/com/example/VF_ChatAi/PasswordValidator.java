package com.example.VF_ChatAi;

import org.springframework.stereotype.Component;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class PasswordValidator {

    private final PasswordEncoder passwordEncoder;
    private final Set<String> commonPasswords;

    // Password policy constants
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final int MIN_STRENGTH_SCORE = 3; // Out of 5

    // Regex patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?`~]");
    private static final Pattern REPEATED_CHAR_PATTERN = Pattern.compile("(.)\\1{2,}"); // 3+ repeated chars
    private static final Pattern SEQUENTIAL_PATTERN = Pattern.compile("(012|123|234|345|456|567|678|789|890|abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)");

    public PasswordValidator() {
        this.passwordEncoder = new BCryptPasswordEncoder(12); // Strong cost factor
        this.commonPasswords = loadCommonPasswords();
    }

    /**
     * Validates password against all security criteria
     */
    public ValidationResult validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (password == null) {
            errors.add("Password cannot be null");
            return new ValidationResult(false, errors, warnings, PasswordStrength.VERY_WEAK, 0);
        }

        // Length validation
        if (password.length() < MIN_LENGTH) {
            errors.add(String.format("Password must be at least %d characters long", MIN_LENGTH));
        }

        if (password.length() > MAX_LENGTH) {
            errors.add(String.format("Password cannot exceed %d characters", MAX_LENGTH));
        }

        // Character type requirements
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter (A-Z)");
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter (a-z)");
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one number (0-9)");
        }

        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one special character (!@#$%^&*()_+-=[]{}|;':\",./<>?`~)");
        }

        // Security validations
        if (REPEATED_CHAR_PATTERN.matcher(password).find()) {
            warnings.add("Avoid using repeated characters (e.g., aaa, 111)");
        }

        if (SEQUENTIAL_PATTERN.matcher(password.toLowerCase()).find()) {
            warnings.add("Avoid using sequential characters (e.g., 123, abc)");
        }

        // Common password check
        if (isCommonPassword(password)) {
            errors.add("This password is too common and easily guessable. Please choose a more unique password");
        }

        // Dictionary word check
        if (containsDictionaryWords(password)) {
            warnings.add("Consider avoiding common dictionary words in your password");
        }

        // Keyboard pattern check
        if (containsKeyboardPatterns(password)) {
            warnings.add("Avoid keyboard patterns (e.g., qwerty, asdf)");
        }

        // Calculate strength
        PasswordStrength strength = calculateStrength(password);
        int score = calculateScore(password);

        boolean isValid = errors.isEmpty() && score >= MIN_STRENGTH_SCORE;

        return new ValidationResult(isValid, errors, warnings, strength, score);
    }

    /**
     * Simplified validation for backward compatibility
     */
    public boolean isValidPassword(String password) {
        return validatePassword(password).isValid();
    }

    /**
     * Get validation errors for backward compatibility
     */
    public List<String> getPasswordErrors(String password) {
        return validatePassword(password).getErrors();
    }

    /**
     * Get password strength for backward compatibility
     */
    public String getPasswordStrength(String password) {
        return validatePassword(password).getStrength().toString();
    }

    /**
     * Hash password securely
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Verify password against hash
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * Check if password needs rehashing (e.g., if bcrypt cost factor was increased)
     */
    public boolean needsRehashing(String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2")) {
            return true; // Not bcrypt or null
        }
        return passwordEncoder.upgradeEncoding(hashedPassword);
    }

    /**
     * Generate a secure random password
     */
    public String generateSecurePassword(int length) {
        if (length < MIN_LENGTH) {
            length = MIN_LENGTH;
        }
        if (length > MAX_LENGTH) {
            length = MAX_LENGTH;
        }

        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        Random random = new Random();
        StringBuilder password = new StringBuilder();

        // Ensure at least one character from each category
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill remaining length with random characters from all categories
        String allChars = uppercase + lowercase + digits + special;
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the password to avoid predictable patterns
        List<Character> chars = password.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        Collections.shuffle(chars);

        return chars.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    /**
     * Calculate password strength score (0-5)
     */
    private int calculateScore(String password) {
        int score = 0;

        if (password.length() >= MIN_LENGTH) score++;
        if (UPPERCASE_PATTERN.matcher(password).find()) score++;
        if (LOWERCASE_PATTERN.matcher(password).find()) score++;
        if (DIGIT_PATTERN.matcher(password).find()) score++;
        if (SPECIAL_CHAR_PATTERN.matcher(password).find()) score++;

        // Bonus points for extra security
        if (password.length() >= 12) score++; // Longer password
        if (hasMultipleCharTypes(password, 3)) score++; // Character diversity
        if (!isCommonPassword(password)) score++; // Not common
        if (!containsKeyboardPatterns(password)) score++; // No keyboard patterns

        return Math.min(score, 5); // Cap at 5
    }

    /**
     * Calculate password strength enum
     */
    private PasswordStrength calculateStrength(String password) {
        int score = calculateScore(password);

        switch (score) {
            case 0:
            case 1:
                return PasswordStrength.VERY_WEAK;
            case 2:
                return PasswordStrength.WEAK;
            case 3:
                return PasswordStrength.MEDIUM;
            case 4:
                return PasswordStrength.STRONG;
            case 5:
                return PasswordStrength.VERY_STRONG;
            default:
                return PasswordStrength.VERY_WEAK;
        }
    }

    /**
     * Check if password contains multiple character types
     */
    private boolean hasMultipleCharTypes(String password, int minTypes) {
        int types = 0;
        if (UPPERCASE_PATTERN.matcher(password).find()) types++;
        if (LOWERCASE_PATTERN.matcher(password).find()) types++;
        if (DIGIT_PATTERN.matcher(password).find()) types++;
        if (SPECIAL_CHAR_PATTERN.matcher(password).find()) types++;
        return types >= minTypes;
    }

    /**
     * Check if password is in common passwords list
     */
    private boolean isCommonPassword(String password) {
        return commonPasswords.contains(password.toLowerCase());
    }

    /**
     * Check if password contains dictionary words
     */
    private boolean containsDictionaryWords(String password) {
        String lower = password.toLowerCase();
        String[] commonWords = {
                "password", "admin", "user", "login", "welcome", "hello", "world",
                "computer", "internet", "website", "email", "phone", "mobile",
                "qwerty", "asdf", "zxcv", "love", "hate", "life", "death",
                "money", "work", "home", "family", "friend", "secret", "private"
        };

        for (String word : commonWords) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check for keyboard patterns
     */
    private boolean containsKeyboardPatterns(String password) {
        String lower = password.toLowerCase();
        String[] patterns = {
                "qwerty", "asdf", "zxcv", "qaz", "wsx", "edc", "rfv", "tgb", "yhn", "ujm",
                "123", "456", "789", "147", "258", "369", "abc", "def", "ghi", "jkl",
                "mno", "pqr", "stu", "vwx", "yz"
        };

        for (String pattern : patterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load common passwords from a predefined list
     */
    private Set<String> loadCommonPasswords() {
        Set<String> passwords = new HashSet<>();

        // Top 100 most common passwords
        String[] common = {
                "123456", "password", "12345678", "qwerty", "123456789", "12345",
                "1234", "111111", "1234567", "dragon", "123123", "baseball", "abc123",
                "football", "monkey", "letmein", "696969", "shadow", "master", "666666",
                "qwertyuiop", "123321", "mustang", "1234567890", "michael", "654321",
                "pussy", "superman", "1qaz2wsx", "7777777", "fuckyou", "121212",
                "000000", "qazwsx", "123qwe", "killer", "trustno1", "jordan", "jennifer",
                "zxcvbnm", "asdfgh", "hunter", "buster", "soccer", "harley", "batman",
                "andrew", "tigger", "sunshine", "iloveyou", "fuckme", "2000", "charlie",
                "robert", "thomas", "hockey", "ranger", "daniel", "starwars", "klaster",
                "112233", "george", "asshole", "computer", "michelle", "jessica",
                "pepper", "1111", "zxcvbn", "555555", "11111111", "131313", "freedom",
                "777777", "pass", "fuck", "maggie", "159753", "aaaaaa", "ginger",
                "princess", "joshua", "cheese", "amanda", "summer", "love", "ashley",
                "6969", "nicole", "chelsea", "biteme", "matthew", "access", "yankees",
                "987654321", "dallas", "austin", "thunder", "taylor", "matrix"
        };

        Collections.addAll(passwords, common);
        return passwords;
    }

    /**
     * Password strength enumeration
     */
    public enum PasswordStrength {
        VERY_WEAK("Very Weak", 0),
        WEAK("Weak", 1),
        MEDIUM("Medium", 2),
        STRONG("Strong", 3),
        VERY_STRONG("Very Strong", 4);

        private final String displayName;
        private final int level;

        PasswordStrength(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final PasswordStrength strength;
        private final int score;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings,
                                PasswordStrength strength, int score) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
            this.strength = strength;
            this.score = score;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return new ArrayList<>(errors); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        public PasswordStrength getStrength() { return strength; }
        public int getScore() { return score; }

        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, strength=%s, score=%d, errors=%d, warnings=%d}",
                    valid, strength, score, errors.size(), warnings.size());
        }
    }
}