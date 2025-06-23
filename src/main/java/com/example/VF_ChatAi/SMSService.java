package com.example.VF_ChatAi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

@Service
public class SMSService {

    private static final Logger logger = LoggerFactory.getLogger(SMSService.class);

    @Value("${sms.service.provider:mock}")
    private String smsProvider;

    @Value("${sms.api.key:}")
    private String apiKey;

    @Value("${sms.api.secret:}")
    private String apiSecret;

    @Value("${sms.from.number:+1234567890}")
    private String fromNumber;

    @Value("${sms.twilio.account.sid:}")
    private String twilioAccountSid;

    @Value("${sms.retry.attempts:3}")
    private int retryAttempts;

    @Value("${sms.timeout.seconds:30}")
    private int timeoutSeconds;

    /**
     * Send verification SMS with retry logic
     */
    public boolean sendVerificationSMS(String toPhone, String username, String code) {
        logger.info("Sending verification SMS to: {} for user: {}", toPhone, username);

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                boolean result = sendSMSInternal(toPhone, username, code);
                if (result) {
                    logger.info("✅ Verification SMS sent successfully to: {} on attempt: {}", toPhone, attempt);
                    return true;
                } else {
                    logger.warn("❌ Failed to send verification SMS to: {} on attempt: {}", toPhone, attempt);
                }
            } catch (Exception e) {
                logger.error("❌ Error sending verification SMS to: {} on attempt: {}: {}",
                        toPhone, attempt, e.getMessage());
            }

            // Wait before retry (exponential backoff)
            if (attempt < retryAttempts) {
                try {
                    Thread.sleep(1000 * attempt); // 1s, 2s, 3s...
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.error("❌ Failed to send verification SMS to: {} after {} attempts", toPhone, retryAttempts);
        return false;
    }

    /**
     * Send password reset SMS
     */
    public boolean sendPasswordResetSMS(String toPhone, String username, String resetCode) {
        logger.info("Sending password reset SMS to: {} for user: {}", toPhone, username);

        try {
            String message = String.format(
                    "VFChatAI Password Reset\n\nHello %s! Your password reset code is: %s\n\nThis code expires in 1 hour.\n\nIf you didn't request this, please ignore this message.",
                    username, resetCode
            );

            return sendSMS(toPhone, message);
        } catch (Exception e) {
            logger.error("Error sending password reset SMS to: {}: {}", toPhone, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send welcome SMS after successful verification
     */
    @Async
    public CompletableFuture<Boolean> sendWelcomeSMSAsync(String toPhone, String username) {
        return CompletableFuture.completedFuture(sendWelcomeSMS(toPhone, username));
    }

    public boolean sendWelcomeSMS(String toPhone, String username) {
        logger.info("Sending welcome SMS to: {} for user: {}", toPhone, username);

        try {
            String message = String.format(
                    "🎉 Welcome to VFChatAI, %s!\n\nYour account is now active and ready to use. Experience the future of AI-powered conversations!\n\nStart chatting at: https://vfchatai.com\n\nNeed help? Reply HELP",
                    username
            );

            return sendSMS(toPhone, message);
        } catch (Exception e) {
            logger.error("Error sending welcome SMS to: {}: {}", toPhone, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Internal SMS sending with provider selection
     */
    private boolean sendSMSInternal(String toPhone, String username, String code) {
        String message = String.format(
                "VFChatAI Verification\n\nHello %s! Your verification code is: %s\n\nValid for 15 minutes.\n\nIf you didn't request this, please ignore this message.",
                username, code
        );

        return sendSMS(toPhone, message);
    }

    /**
     * Core SMS sending method with provider routing
     */
    private boolean sendSMS(String toPhone, String message) {
        switch (smsProvider.toLowerCase()) {
            case "twilio":
                return sendWithTwilio(toPhone, message);
            case "nexmo":
            case "vonage":
                return sendWithNexmo(toPhone, message);
            case "aws":
            case "sns":
                return sendWithAWSSNS(toPhone, message);
            case "messagebird":
                return sendWithMessageBird(toPhone, message);
            default:
                return sendMockSMS(toPhone, message);
        }
    }

    /**
     * Twilio implementation
     */
    private boolean sendWithTwilio(String toPhone, String message) {
        try {
            if (apiKey == null || apiKey.isEmpty() || twilioAccountSid == null || twilioAccountSid.isEmpty()) {
                logger.warn("Twilio not properly configured, falling back to mock");
                return sendMockSMS(toPhone, message);
            }

            String formData = String.format(
                    "From=%s&To=%s&Body=%s",
                    java.net.URLEncoder.encode(fromNumber, "UTF-8"),
                    java.net.URLEncoder.encode(toPhone, "UTF-8"),
                    java.net.URLEncoder.encode(message, "UTF-8")
            );

            String auth = Base64.getEncoder().encodeToString((twilioAccountSid + ":" + apiKey).getBytes());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                logger.debug("✅ Twilio SMS sent successfully to: {}", toPhone);
                return true;
            } else {
                logger.error("❌ Twilio error {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Twilio SMS sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Nexmo/Vonage implementation
     */
    private boolean sendWithNexmo(String toPhone, String message) {
        try {
            if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
                logger.warn("Nexmo/Vonage not properly configured, falling back to mock");
                return sendMockSMS(toPhone, message);
            }

            String jsonPayload = String.format("""
                {
                    "from": "%s",
                    "to": "%s",
                    "text": "%s"
                }
                """, fromNumber, toPhone, message.replace("\"", "\\\""));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://rest.nexmo.com/sms/json"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes()))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.debug("✅ Nexmo SMS sent successfully to: {}", toPhone);
                return true;
            } else {
                logger.error("❌ Nexmo error {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Nexmo SMS sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * AWS SNS implementation
     */
    private boolean sendWithAWSSNS(String toPhone, String message) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("AWS SNS not properly configured, falling back to mock");
                return sendMockSMS(toPhone, message);
            }

            // AWS SDK implementation would go here
            // For now, simulate the call
            logger.info("📱 AWS SNS SMS sending would be implemented with AWS SDK");
            return sendMockSMS(toPhone, message);

        } catch (Exception e) {
            logger.error("❌ AWS SNS SMS sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * MessageBird implementation
     */
    private boolean sendWithMessageBird(String toPhone, String message) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("MessageBird not properly configured, falling back to mock");
                return sendMockSMS(toPhone, message);
            }

            String jsonPayload = String.format("""
                {
                    "recipients": ["%s"],
                    "originator": "%s",
                    "body": "%s"
                }
                """, toPhone, fromNumber, message.replace("\"", "\\\""));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://rest.messagebird.com/messages"))
                    .header("Authorization", "AccessKey " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                logger.debug("✅ MessageBird SMS sent successfully to: {}", toPhone);
                return true;
            } else {
                logger.error("❌ MessageBird error {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ MessageBird SMS sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Mock implementation for testing
     */
    private boolean sendMockSMS(String toPhone, String message) {
        logger.info("📱 MOCK SMS SENT");
        logger.info("To: {}", toPhone);
        logger.info("From: {}", fromNumber);
        logger.info("Message: {}", message);
        logger.info("Provider: {}", smsProvider);
        logger.info("⏰ SMS would be delivered in production");
        return true;
    }

    /**
     * Format phone number for international sending
     */
    public String formatPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }

        // Remove all non-digit characters except +
        String cleaned = phone.replaceAll("[^+\\d]", "");

        // Ensure it starts with +
        if (!cleaned.startsWith("+")) {
            // Assume US/Canada if no country code
            if (cleaned.length() == 10) {
                cleaned = "+1" + cleaned;
            } else if (cleaned.length() == 11 && cleaned.startsWith("1")) {
                cleaned = "+" + cleaned;
            } else {
                // Invalid format
                logger.warn("Invalid phone number format: {}", phone);
                return phone;
            }
        }

        return cleaned;
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }

        String formatted = formatPhoneNumber(phone);

        // Check if it matches international format: +[1-9][0-9]{1,14}
        return formatted.matches("^\\+[1-9]\\d{1,14}$");
    }

    /**
     * Get SMS delivery status (placeholder for future implementation)
     */
    public SMSDeliveryStatus getDeliveryStatus(String messageId) {
        // Implementation would query the SMS provider's API for delivery status
        // For now, return a mock status
        return new SMSDeliveryStatus(messageId, "delivered", "Message delivered successfully", System.currentTimeMillis());
    }

    /**
     * Calculate SMS cost estimate
     */
    public double estimateSMSCost(String toPhone, String message) {
        // Basic cost estimation based on message length and destination
        int segments = (int) Math.ceil(message.length() / 160.0);

        // Get country code for pricing
        String countryCode = extractCountryCode(toPhone);
        double baseCost = getBaseCostForCountry(countryCode);

        return segments * baseCost;
    }

    private String extractCountryCode(String phone) {
        if (phone == null || !phone.startsWith("+")) {
            return "1"; // Default to US
        }

        // Extract country code (simplified logic)
        if (phone.startsWith("+1")) return "1";
        if (phone.startsWith("+44")) return "44";
        if (phone.startsWith("+33")) return "33";
        if (phone.startsWith("+49")) return "49";
        if (phone.startsWith("+30")) return "30"; // Greece

        // Default
        return "1";
    }

    private double getBaseCostForCountry(String countryCode) {
        // Simplified pricing model
        switch (countryCode) {
            case "1": return 0.0075; // US/Canada
            case "44": return 0.0080; // UK
            case "33": return 0.0090; // France
            case "49": return 0.0095; // Germany
            case "30": return 0.0085; // Greece
            default: return 0.0100; // Default international rate
        }
    }

    /**
     * SMS Delivery Status class
     */
    public static class SMSDeliveryStatus {
        private final String messageId;
        private final String status;
        private final String description;
        private final long timestamp;

        public SMSDeliveryStatus(String messageId, String status, String description, long timestamp) {
            this.messageId = messageId;
            this.status = status;
            this.description = description;
            this.timestamp = timestamp;
        }

        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("SMSDeliveryStatus{messageId='%s', status='%s', description='%s', timestamp=%d}",
                    messageId, status, description, timestamp);
        }
    }
}