package com.example.VF_ChatAi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Value("${email.service.provider:mock}")
    private String emailProvider;

    @Value("${email.api.key:}")
    private String apiKey;

    @Value("${email.from.address:noreply@vfchatai.com}")
    private String fromAddress;

    @Value("${email.from.name:VFChatAI}")
    private String fromName;

    @Value("${email.sendgrid.api.url:https://api.sendgrid.com/v3/mail/send}")
    private String sendGridApiUrl;

    @Value("${email.mailgun.domain:}")
    private String mailgunDomain;

    @Value("${email.retry.attempts:3}")
    private int retryAttempts;

    @Value("${email.timeout.seconds:30}")
    private int timeoutSeconds;

    /**
     * Send verification email with retry logic
     */
    public boolean sendVerificationEmail(String toEmail, String username, String code) {
        logger.info("Sending verification email to: {} for user: {}", toEmail, username);

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                boolean result = sendEmailInternal(toEmail, username, code);
                if (result) {
                    logger.info("✅ Verification email sent successfully to: {} on attempt: {}", toEmail, attempt);
                    return true;
                } else {
                    logger.warn("❌ Failed to send verification email to: {} on attempt: {}", toEmail, attempt);
                }
            } catch (Exception e) {
                logger.error("❌ Error sending verification email to: {} on attempt: {}: {}",
                        toEmail, attempt, e.getMessage());
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

        logger.error("❌ Failed to send verification email to: {} after {} attempts", toEmail, retryAttempts);
        return false;
    }

    /**
     * Send password reset email
     */
    public boolean sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        logger.info("Sending password reset email to: {} for user: {}", toEmail, username);

        try {
            String subject = "Reset Your VFChatAI Password";
            String htmlBody = buildPasswordResetEmailBody(username, resetToken);
            String textBody = buildPasswordResetTextBody(username, resetToken);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            logger.error("Error sending password reset email to: {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send welcome email after successful verification
     */
    @Async
    public CompletableFuture<Boolean> sendWelcomeEmailAsync(String toEmail, String username) {
        return CompletableFuture.completedFuture(sendWelcomeEmail(toEmail, username));
    }

    public boolean sendWelcomeEmail(String toEmail, String username) {
        logger.info("Sending welcome email to: {} for user: {}", toEmail, username);

        try {
            String subject = "Welcome to VFChatAI! 🎉";
            String htmlBody = buildWelcomeEmailBody(username);
            String textBody = buildWelcomeTextBody(username);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            logger.error("Error sending welcome email to: {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Internal email sending with provider selection
     */
    private boolean sendEmailInternal(String toEmail, String username, String code) {
        String subject = "Verify Your VFChatAI Account - Code: " + code;
        String htmlBody = buildVerificationEmailBody(username, code);
        String textBody = buildVerificationTextBody(username, code);

        return sendEmail(toEmail, subject, htmlBody, textBody);
    }

    /**
     * Core email sending method with provider routing
     */
    private boolean sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        switch (emailProvider.toLowerCase()) {
            case "sendgrid":
                return sendWithSendGrid(toEmail, subject, htmlBody, textBody);
            case "mailgun":
                return sendWithMailgun(toEmail, subject, htmlBody, textBody);
            case "smtp":
                return sendWithSMTP(toEmail, subject, htmlBody, textBody);
            default:
                return sendMockEmail(toEmail, subject, htmlBody, textBody);
        }
    }

    /**
     * SendGrid implementation
     */
    private boolean sendWithSendGrid(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("SendGrid API key not configured, falling back to mock");
                return sendMockEmail(toEmail, subject, htmlBody, textBody);
            }

            String jsonPayload = buildSendGridPayload(toEmail, subject, htmlBody, textBody);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sendGridApiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 202) {
                logger.debug("✅ SendGrid email sent successfully to: {}", toEmail);
                return true;
            } else {
                logger.error("❌ SendGrid error {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ SendGrid email sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Mailgun implementation
     */
    private boolean sendWithMailgun(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            if (apiKey == null || apiKey.isEmpty() || mailgunDomain == null || mailgunDomain.isEmpty()) {
                logger.warn("Mailgun not properly configured, falling back to mock");
                return sendMockEmail(toEmail, subject, htmlBody, textBody);
            }

            String formData = buildMailgunFormData(toEmail, subject, htmlBody, textBody);
            String auth = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mailgun.net/v3/" + mailgunDomain + "/messages"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.debug("✅ Mailgun email sent successfully to: {}", toEmail);
                return true;
            } else {
                logger.error("❌ Mailgun error {}: {}", response.statusCode(), response.body());
                return false;
            }

        } catch (Exception e) {
            logger.error("❌ Mailgun email sending failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * SMTP implementation placeholder
     */
    private boolean sendWithSMTP(String toEmail, String subject, String htmlBody, String textBody) {
        logger.info("📧 SMTP email sending not implemented yet, using mock");
        return sendMockEmail(toEmail, subject, htmlBody, textBody);
    }

    /**
     * Mock implementation for testing
     */
    private boolean sendMockEmail(String toEmail, String subject, String htmlBody, String textBody) {
        logger.info("📧 MOCK EMAIL SENT");
        logger.info("To: {}", toEmail);
        logger.info("Subject: {}", subject);
        logger.info("HTML Body Length: {} chars", htmlBody.length());
        logger.info("Text Body: {}", textBody);
        logger.info("⏰ Email would be delivered in production");
        return true;
    }

    // Email body builders
    private String buildVerificationEmailBody(String username, String code) {
        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verify Your Email - VFChatAI</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #e2e8f0; background-color: #0f172a; }
        .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; overflow: hidden; border: 1px solid #334155; }
        .header { background: linear-gradient(135deg, #00d4ff, #5b21b6, #1e40af); padding: 40px 30px; text-align: center; }
        .logo { width: 60px; height: 60px; background: linear-gradient(135deg, #00d4ff, #5b21b6); border-radius: 12px; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: bold; color: white; }
        .header h1 { font-size: 24px; font-weight: 600; color: white; margin-bottom: 8px; }
        .content { padding: 40px 30px; text-align: center; }
        .verification-code { background: linear-gradient(135deg, #00d4ff, #5b21b6); color: white; font-size: 32px; font-weight: bold; letter-spacing: 8px; padding: 20px; border-radius: 12px; margin: 30px 0; font-family: 'Courier New', monospace; }
        .footer { background-color: #0f172a; padding: 25px 30px; text-align: center; border-top: 1px solid #334155; }
        .btn { display: inline-block; background: linear-gradient(135deg, #00d4ff, #5b21b6); color: white; text-decoration: none; padding: 16px 32px; border-radius: 10px; font-weight: 600; margin: 20px 0; }
        @media (max-width: 600px) { .container { margin: 0 10px; } .content { padding: 30px 20px; } }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">AI</div>
            <h1>VFChatAI</h1>
            <p style="color: #e2e8f0;">Verify Your Account</p>
        </div>
        
        <div class="content">
            <h2 style="color: #f1f5f9; margin-bottom: 20px;">Hello %s! 👋</h2>
            <p style="color: #cbd5e1; margin-bottom: 30px;">
                Welcome to VFChatAI! You're one step away from accessing the future of AI-powered conversations.
                Please use the verification code below to activate your account:
            </p>
            
            <div class="verification-code">%s</div>
            
            <p style="color: #64748b; font-size: 14px; margin-top: 20px;">
                This code will expire in 15 minutes for your security.
            </p>
            
            <p style="color: #64748b; font-size: 13px; margin-top: 30px;">
                If you didn't create an account with VFChatAI, please ignore this email.
            </p>
        </div>
        
        <div class="footer">
            <p style="color: #64748b; font-size: 13px;">
                © 2025 VFChatAI. Revolutionizing conversations with AI.
            </p>
            <p style="color: #64748b; font-size: 12px; margin-top: 10px;">
                <a href="mailto:support@vfchatai.com" style="color: #00d4ff;">support@vfchatai.com</a>
            </p>
        </div>
    </div>
</body>
</html>""", username, code);
    }

    private String buildVerificationTextBody(String username, String code) {
        return String.format("""
Hello %s!

Welcome to VFChatAI! Please use the following verification code to activate your account:

%s

This code will expire in 15 minutes for your security.

If you didn't create an account with VFChatAI, please ignore this email.

Best regards,
The VFChatAI Team
support@vfchatai.com
""", username, code);
    }

    private String buildPasswordResetEmailBody(String username, String resetToken) {
        String resetUrl = "https://vfchatai.com/reset-password?token=" + resetToken;
        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reset Your Password - VFChatAI</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #e2e8f0; background-color: #0f172a; }
        .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; overflow: hidden; border: 1px solid #334155; }
        .header { background: linear-gradient(135deg, #ef4444, #dc2626); padding: 40px 30px; text-align: center; }
        .logo { width: 60px; height: 60px; background: linear-gradient(135deg, #ef4444, #dc2626); border-radius: 12px; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: bold; color: white; }
        .content { padding: 40px 30px; text-align: center; }
        .reset-btn { display: inline-block; background: linear-gradient(135deg, #ef4444, #dc2626); color: white; text-decoration: none; padding: 16px 32px; border-radius: 10px; font-weight: 600; margin: 20px 0; }
        .footer { background-color: #0f172a; padding: 25px 30px; text-align: center; border-top: 1px solid #334155; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">🔒</div>
            <h1 style="color: white;">Password Reset Request</h1>
        </div>
        
        <div class="content">
            <h2 style="color: #f1f5f9; margin-bottom: 20px;">Hello %s!</h2>
            <p style="color: #cbd5e1; margin-bottom: 30px;">
                We received a request to reset your VFChatAI account password. Click the button below to create a new password:
            </p>
            
            <a href="%s" class="reset-btn">Reset Password</a>
            
            <p style="color: #64748b; font-size: 14px; margin-top: 20px;">
                This link will expire in 1 hour for your security.
            </p>
            
            <p style="color: #64748b; font-size: 13px; margin-top: 30px;">
                If you didn't request this password reset, please ignore this email or contact support if you have concerns.
            </p>
        </div>
        
        <div class="footer">
            <p style="color: #64748b; font-size: 13px;">
                © 2025 VFChatAI. Revolutionizing conversations with AI.
            </p>
        </div>
    </div>
</body>
</html>""", username, resetUrl);
    }

    private String buildPasswordResetTextBody(String username, String resetToken) {
        String resetUrl = "https://vfchatai.com/reset-password?token=" + resetToken;
        return String.format("""
Hello %s!

We received a request to reset your VFChatAI account password.

To reset your password, please visit the following link:
%s

This link will expire in 1 hour for your security.

If you didn't request this password reset, please ignore this email or contact support if you have concerns.

Best regards,
The VFChatAI Team
support@vfchatai.com
""", username, resetUrl);
    }

    private String buildWelcomeEmailBody(String username) {
        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Welcome to VFChatAI</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #e2e8f0; background-color: #0f172a; }
        .container { max-width: 600px; margin: 0 auto; background-color: #1e293b; border-radius: 12px; overflow: hidden; border: 1px solid #334155; }
        .header { background: linear-gradient(135deg, #10b981, #059669); padding: 40px 30px; text-align: center; }
        .content { padding: 40px 30px; }
        .cta-btn { display: inline-block; background: linear-gradient(135deg, #10b981, #059669); color: white; text-decoration: none; padding: 16px 32px; border-radius: 10px; font-weight: 600; margin: 20px 0; }
        .feature { background: #0f172a; padding: 20px; margin: 15px 0; border-radius: 8px; border-left: 4px solid #10b981; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 style="color: white; font-size: 28px;">🎉 Welcome to VFChatAI!</h1>
            <p style="color: #e2e8f0; margin-top: 10px;">Your AI-powered conversation journey begins now</p>
        </div>
        
        <div class="content">
            <h2 style="color: #f1f5f9; margin-bottom: 20px;">Hello %s!</h2>
            <p style="color: #cbd5e1; margin-bottom: 30px;">
                Congratulations! Your VFChatAI account is now active and ready to use. You're about to experience the future of AI-powered conversations.
            </p>
            
            <div class="feature">
                <h3 style="color: #10b981; margin-bottom: 10px;">🤖 Advanced AI Models</h3>
                <p style="color: #cbd5e1;">Chat with state-of-the-art AI models trained on the latest data.</p>
            </div>
            
            <div class="feature">
                <h3 style="color: #10b981; margin-bottom: 10px;">💡 Smart Conversations</h3>
                <p style="color: #cbd5e1;">Get intelligent responses, creative ideas, and helpful assistance.</p>
            </div>
            
            <div class="feature">
                <h3 style="color: #10b981; margin-bottom: 10px;">🔒 Privacy First</h3>
                <p style="color: #cbd5e1;">Your conversations are private and secure with enterprise-grade encryption.</p>
            </div>
            
            <div style="text-align: center; margin: 30px 0;">
                <a href="https://vfchatai.com/chat" class="cta-btn">Start Chatting Now</a>
            </div>
            
            <p style="color: #64748b; font-size: 14px; text-align: center;">
                Need help getting started? Check out our <a href="https://vfchatai.com/guide" style="color: #10b981;">Quick Start Guide</a>
            </p>
        </div>
        
        <div style="background-color: #0f172a; padding: 25px 30px; text-align: center; border-top: 1px solid #334155;">
            <p style="color: #64748b; font-size: 13px;">
                © 2025 VFChatAI. Revolutionizing conversations with AI.
            </p>
        </div>
    </div>
</body>
</html>""", username);
    }

    private String buildWelcomeTextBody(String username) {
        return String.format("""
🎉 Welcome to VFChatAI, %s!

Congratulations! Your VFChatAI account is now active and ready to use.

What you can do with VFChatAI:
• Chat with advanced AI models
• Get intelligent responses and creative ideas
• Enjoy private and secure conversations
• Access cutting-edge AI technology

Get started: https://vfchatai.com/chat
Quick Start Guide: https://vfchatai.com/guide

Need help? Contact us at support@vfchatai.com

Best regards,
The VFChatAI Team
""", username);
    }

    // Helper methods for building provider-specific payloads
    private String buildSendGridPayload(String toEmail, String subject, String htmlBody, String textBody) {
        return String.format("""
            {
                "personalizations": [{
                    "to": [{"email": "%s"}],
                    "subject": "%s"
                }],
                "from": {"email": "%s", "name": "%s"},
                "content": [
                    {
                        "type": "text/html",
                        "value": "%s"
                    },
                    {
                        "type": "text/plain",
                        "value": "%s"
                    }
                ]
            }
            """, toEmail, subject, fromAddress, fromName,
                htmlBody.replace("\"", "\\\"").replace("\n", "\\n"),
                textBody.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    private String buildMailgunFormData(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            return String.format(
                    "from=%s <%s>&to=%s&subject=%s&html=%s&text=%s",
                    java.net.URLEncoder.encode(fromName, "UTF-8"),
                    java.net.URLEncoder.encode(fromAddress, "UTF-8"),
                    java.net.URLEncoder.encode(toEmail, "UTF-8"),
                    java.net.URLEncoder.encode(subject, "UTF-8"),
                    java.net.URLEncoder.encode(htmlBody, "UTF-8"),
                    java.net.URLEncoder.encode(textBody, "UTF-8")
            );
        } catch (Exception e) {
            logger.error("Error building Mailgun form data: {}", e.getMessage());
            return "";
        }
    }
}