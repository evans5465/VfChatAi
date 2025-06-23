package com.example.VF_ChatAi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DynamicEmailService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicEmailService.class);

    @Autowired
    private EmailService emailService;

    /**
     * Send personalized verification email with dynamic content
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendPersonalizedVerificationEmail(User user, String code) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("code", code);
            templateVars.put("expiryMinutes", "15");
            templateVars.put("currentYear", String.valueOf(LocalDateTime.now().getYear()));
            templateVars.put("registrationDate", user.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

            String htmlContent = buildDynamicVerificationEmail(templateVars);
            String textContent = buildDynamicVerificationTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "Verify Your VFChatAI Account - Code: " + code,
                    htmlContent,
                    textContent
            );

            if (sent) {
                logger.info("✅ Personalized verification email sent to: {}", user.getEmail());
            } else {
                logger.error("❌ Failed to send personalized verification email to: {}", user.getEmail());
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending personalized verification email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send account locked notification
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendAccountLockedNotification(User user, String ipAddress) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("lockTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")));
            templateVars.put("ipAddress", ipAddress);
            templateVars.put("unlockInstructions", "Contact support or wait 30 minutes for automatic unlock");

            String htmlContent = buildAccountLockedEmail(templateVars);
            String textContent = buildAccountLockedTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "🔒 VFChatAI Account Security Alert - Account Locked",
                    htmlContent,
                    textContent
            );

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending account locked notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send successful login notification
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendLoginNotification(User user, String ipAddress, String userAgent) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("loginTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")));
            templateVars.put("ipAddress", ipAddress);
            templateVars.put("userAgent", userAgent);
            templateVars.put("location", getLocationFromIP(ipAddress));

            String htmlContent = buildLoginNotificationEmail(templateVars);
            String textContent = buildLoginNotificationTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "🔐 VFChatAI Login Notification",
                    htmlContent,
                    textContent
            );

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending login notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send password changed notification
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendPasswordChangedNotification(User user, String ipAddress) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("changeTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")));
            templateVars.put("ipAddress", ipAddress);

            String htmlContent = buildPasswordChangedEmail(templateVars);
            String textContent = buildPasswordChangedTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "🔑 VFChatAI Password Changed Successfully",
                    htmlContent,
                    textContent
            );

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending password changed notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send account verification completed email
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendAccountActivatedEmail(User user) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("activationTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")));
            templateVars.put("emailVerified", user.isEmailVerified() ? "✅ Verified" : "❌ Not Verified");
            templateVars.put("phoneVerified", user.isPhoneVerified() ? "✅ Verified" : "❌ Not Verified");

            String htmlContent = buildAccountActivatedEmail(templateVars);
            String textContent = buildAccountActivatedTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "🎉 Welcome to VFChatAI - Account Activated!",
                    htmlContent,
                    textContent
            );

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending account activated email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send monthly usage summary
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendMonthlyUsageSummary(User user, UsageSummary summary) {
        try {
            Map<String, String> templateVars = new HashMap<>();
            templateVars.put("username", user.getUsername());
            templateVars.put("fullName", user.getFullName());
            templateVars.put("month", summary.getMonth());
            templateVars.put("totalLogins", String.valueOf(summary.getTotalLogins()));
            templateVars.put("totalMessages", String.valueOf(summary.getTotalMessages()));
            templateVars.put("averageSessionTime", summary.getAverageSessionTime());
            templateVars.put("topFeature", summary.getTopFeature());

            String htmlContent = buildUsageSummaryEmail(templateVars);
            String textContent = buildUsageSummaryTextEmail(templateVars);

            boolean sent = sendTemplatedEmail(
                    user.getEmail(),
                    "📊 Your VFChatAI Monthly Summary - " + summary.getMonth(),
                    htmlContent,
                    textContent
            );

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            logger.error("Error sending usage summary email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    // Private helper methods

    private boolean sendTemplatedEmail(String toEmail, String subject, String htmlContent, String textContent) {
        // Use the main email service to send
        return emailService.sendVerificationEmail(toEmail, "", ""); // This would need to be refactored
    }

    private String buildDynamicVerificationEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verify Your VFChatAI Account</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); min-height: 100vh; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #00d4ff, #5b21b6); padding: 50px 30px; text-align: center; color: white; }
        .logo { width: 80px; height: 80px; background: rgba(255,255,255,0.2); border-radius: 20px; margin: 0 auto 20px; display: flex; align-items: center; justify-content: center; font-size: 36px; }
        .content { padding: 50px 40px; text-align: center; }
        .verification-box { background: linear-gradient(135deg, #667eea, #764ba2); color: white; padding: 30px; border-radius: 15px; margin: 30px 0; }
        .code { font-size: 48px; font-weight: bold; letter-spacing: 8px; margin: 20px 0; font-family: 'Courier New', monospace; }
        .footer { background: #f8f9fa; padding: 30px; text-align: center; border-top: 1px solid #e9ecef; }
        .btn { display: inline-block; background: linear-gradient(135deg, #667eea, #764ba2); color: white; text-decoration: none; padding: 15px 30px; border-radius: 10px; font-weight: 600; margin: 20px 0; }
        .stats { display: flex; justify-content: space-around; margin: 30px 0; }
        .stat { text-align: center; }
        .stat-number { font-size: 24px; font-weight: bold; color: #667eea; }
        .stat-label { font-size: 14px; color: #6c757d; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">🤖</div>
            <h1 style="font-size: 32px; margin-bottom: 10px;">VFChatAI</h1>
            <p style="font-size: 18px; opacity: 0.9;">Welcome to the Future of AI Conversations</p>
        </div>
        
        <div class="content">
            <h2 style="color: #2d3748; margin-bottom: 20px; font-size: 28px;">Hello %s! 👋</h2>
            <p style="color: #4a5568; margin-bottom: 30px; font-size: 18px; line-height: 1.6;">
                Welcome to VFChatAI! You've joined on <strong>%s</strong> and you're just one step away from experiencing revolutionary AI-powered conversations.
            </p>
            
            <div class="verification-box">
                <h3 style="margin-bottom: 15px; font-size: 20px;">Your Verification Code</h3>
                <div class="code">%s</div>
                <p style="margin-top: 15px; font-size: 14px; opacity: 0.9;">Valid for %s minutes</p>
            </div>
            
            <div class="stats">
                <div class="stat">
                    <div class="stat-number">∞</div>
                    <div class="stat-label">AI Models</div>
                </div>
                <div class="stat">
                    <div class="stat-number">24/7</div>
                    <div class="stat-label">Availability</div>
                </div>
                <div class="stat">
                    <div class="stat-number">🔒</div>
                    <div class="stat-label">Secure</div>
                </div>
            </div>
            
            <p style="color: #718096; font-size: 14px; margin-top: 30px;">
                If you didn't create this account, please ignore this email.
            </p>
        </div>
        
        <div class="footer">
            <p style="color: #6c757d; font-size: 14px;">
                © %s VFChatAI. Revolutionizing conversations with AI.
            </p>
            <p style="color: #6c757d; font-size: 12px; margin-top: 10px;">
                <a href="mailto:support@vfchatai.com" style="color: #667eea;">support@vfchatai.com</a> • 
                <a href="https://vfchatai.com/privacy" style="color: #667eea;">Privacy</a> • 
                <a href="https://vfchatai.com/unsubscribe" style="color: #667eea;">Unsubscribe</a>
            </p>
        </div>
    </div>
</body>
</html>""",
                vars.get("fullName"), vars.get("registrationDate"), vars.get("code"),
                vars.get("expiryMinutes"), vars.get("currentYear"));
    }

    private String buildDynamicVerificationTextEmail(Map<String, String> vars) {
        return String.format("""
🤖 VFChatAI - Verify Your Account

Hello %s!

Welcome to VFChatAI! You've joined on %s and you're just one step away from experiencing revolutionary AI-powered conversations.

Your verification code: %s
(Valid for %s minutes)

What you'll get with VFChatAI:
• Unlimited access to advanced AI models
• 24/7 availability for all your questions
• Secure and private conversations
• Cutting-edge AI technology

If you didn't create this account, please ignore this email.

Best regards,
The VFChatAI Team

© %s VFChatAI
support@vfchatai.com
""", vars.get("fullName"), vars.get("registrationDate"), vars.get("code"),
                vars.get("expiryMinutes"), vars.get("currentYear"));
    }

    private String buildAccountLockedEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>VFChatAI Account Security Alert</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
        .header { background: #dc3545; padding: 30px; text-align: center; color: white; }
        .content { padding: 30px; }
        .alert-box { background: #fff3cd; border: 1px solid #ffeaa7; padding: 20px; border-radius: 8px; margin: 20px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔒 Security Alert</h1>
            <p>Your VFChatAI account has been temporarily locked</p>
        </div>
        <div class="content">
            <h2>Hello %s,</h2>
            <p>Your VFChatAI account was automatically locked on <strong>%s</strong> due to multiple failed login attempts from IP address <strong>%s</strong>.</p>
            
            <div class="alert-box">
                <h3>🛡️ Account Protection</h3>
                <p>This is a security measure to protect your account from unauthorized access attempts.</p>
            </div>
            
            <h3>What you can do:</h3>
            <ul>
                <li>%s</li>
                <li>If this wasn't you, please contact our support team immediately</li>
                <li>Consider updating your password once access is restored</li>
            </ul>
            
            <p>If you have any concerns, please contact us at <a href="mailto:support@vfchatai.com">support@vfchatai.com</a></p>
        </div>
    </div>
</body>
</html>""", vars.get("fullName"), vars.get("lockTime"), vars.get("ipAddress"), vars.get("unlockInstructions"));
    }

    private String buildAccountLockedTextEmail(Map<String, String> vars) {
        return String.format("""
🔒 VFChatAI Security Alert - Account Locked

Hello %s,

Your VFChatAI account was automatically locked on %s due to multiple failed login attempts from IP address %s.

This is a security measure to protect your account from unauthorized access attempts.

What you can do:
• %s
• If this wasn't you, please contact our support team immediately
• Consider updating your password once access is restored

If you have any concerns, please contact us at support@vfchatai.com

Best regards,
VFChatAI Security Team
""", vars.get("fullName"), vars.get("lockTime"), vars.get("ipAddress"), vars.get("unlockInstructions"));
    }

    private String buildLoginNotificationEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>VFChatAI Login Notification</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f8f9fa; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; }
        .header { background: #28a745; padding: 20px; text-align: center; color: white; }
        .content { padding: 30px; }
        .info-box { background: #e7f3ff; border-left: 4px solid #0066cc; padding: 15px; margin: 15px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔐 Login Notification</h1>
        </div>
        <div class="content">
            <h2>Hello %s,</h2>
            <p>We detected a successful login to your VFChatAI account.</p>
            
            <div class="info-box">
                <h3>Login Details</h3>
                <p><strong>Time:</strong> %s</p>
                <p><strong>IP Address:</strong> %s</p>
                <p><strong>Location:</strong> %s</p>
                <p><strong>Device:</strong> %s</p>
            </div>
            
            <p>If this was you, no action is needed. If you don't recognize this login, please secure your account immediately by changing your password.</p>
        </div>
    </div>
</body>
</html>""", vars.get("fullName"), vars.get("loginTime"), vars.get("ipAddress"), vars.get("location"), vars.get("userAgent"));
    }

    private String buildLoginNotificationTextEmail(Map<String, String> vars) {
        return String.format("""
🔐 VFChatAI Login Notification

Hello %s,

We detected a successful login to your VFChatAI account.

Login Details:
• Time: %s
• IP Address: %s
• Location: %s
• Device: %s

If this was you, no action is needed. If you don't recognize this login, please secure your account immediately by changing your password.

Best regards,
VFChatAI Security Team
""", vars.get("fullName"), vars.get("loginTime"), vars.get("ipAddress"), vars.get("location"), vars.get("userAgent"));
    }

    private String buildPasswordChangedEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Password Changed Successfully</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f8f9fa; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
        .header { background: #17a2b8; padding: 20px; text-align: center; color: white; }
        .content { padding: 30px; }
        .success-box { background: #d4edda; border: 1px solid #c3e6cb; color: #155724; padding: 15px; border-radius: 8px; margin: 15px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🔑 Password Changed</h1>
        </div>
        <div class="content">
            <h2>Hello %s,</h2>
            
            <div class="success-box">
                <h3>✅ Password Successfully Updated</h3>
                <p>Your VFChatAI account password was changed on <strong>%s</strong> from IP address <strong>%s</strong>.</p>
            </div>
            
            <h3>Security Tips:</h3>
            <ul>
                <li>Use a unique password that you don't use elsewhere</li>
                <li>Consider enabling two-factor authentication</li>
                <li>Keep your account information up to date</li>
            </ul>
            
            <p>If you didn't make this change, please contact our support team immediately at <a href="mailto:support@vfchatai.com">support@vfchatai.com</a></p>
        </div>
    </div>
</body>
</html>""", vars.get("fullName"), vars.get("changeTime"), vars.get("ipAddress"));
    }

    private String buildPasswordChangedTextEmail(Map<String, String> vars) {
        return String.format("""
🔑 VFChatAI Password Changed Successfully

Hello %s,

Your VFChatAI account password was successfully changed on %s from IP address %s.

Security Tips:
• Use a unique password that you don't use elsewhere
• Consider enabling two-factor authentication
• Keep your account information up to date

If you didn't make this change, please contact our support team immediately at support@vfchatai.com

Best regards,
VFChatAI Security Team
""", vars.get("fullName"), vars.get("changeTime"), vars.get("ipAddress"));
    }

    private String buildAccountActivatedEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Welcome to VFChatAI - Account Activated!</title>
    <style>
        body { font-family: Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 20px 40px rgba(0,0,0,0.15); }
        .header { background: linear-gradient(135deg, #10b981, #059669); padding: 40px; text-align: center; color: white; }
        .content { padding: 40px; }
        .feature-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 30px 0; }
        .feature { background: #f8f9fa; padding: 20px; border-radius: 10px; text-align: center; }
        .cta-button { background: linear-gradient(135deg, #10b981, #059669); color: white; padding: 15px 30px; border-radius: 10px; text-decoration: none; display: inline-block; margin: 20px 0; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1 style="font-size: 32px; margin-bottom: 15px;">🎉 Welcome to VFChatAI!</h1>
            <p style="font-size: 18px;">Your account is now fully activated</p>
        </div>
        
        <div class="content">
            <h2>Hello %s!</h2>
            <p>Congratulations! Your VFChatAI account was successfully activated on <strong>%s</strong>.</p>
            
            <h3>✅ Account Status</h3>
            <ul>
                <li><strong>Email:</strong> %s</li>
                <li><strong>Phone:</strong> %s</li>
                <li><strong>Account:</strong> ✅ Active</li>
            </ul>
            
            <div class="feature-grid">
                <div class="feature">
                    <h4>🤖 AI Models</h4>
                    <p>Access cutting-edge AI technology</p>
                </div>
                <div class="feature">
                    <h4>💬 Unlimited Chats</h4>
                    <p>No limits on conversations</p>
                </div>
                <div class="feature">
                    <h4>🔒 Privacy First</h4>
                    <p>Your data stays secure</p>
                </div>
                <div class="feature">
                    <h4>📱 Multi-Platform</h4>
                    <p>Available everywhere</p>
                </div>
            </div>
            
            <div style="text-align: center;">
                <a href="https://vfchatai.com/chat" class="cta-button">Start Your First Conversation</a>
            </div>
            
            <h3>🚀 Quick Start Guide</h3>
            <ol>
                <li>Visit <a href="https://vfchatai.com">vfchatai.com</a></li>
                <li>Click "Start Chatting" to begin</li>
                <li>Ask anything - our AI is ready to help!</li>
            </ol>
            
            <p>Need help? Check out our <a href="https://vfchatai.com/docs">documentation</a> or contact <a href="mailto:support@vfchatai.com">support@vfchatai.com</a></p>
        </div>
    </div>
</body>
</html>""", vars.get("fullName"), vars.get("activationTime"), vars.get("emailVerified"), vars.get("phoneVerified"));
    }

    private String buildAccountActivatedTextEmail(Map<String, String> vars) {
        return String.format("""
🎉 Welcome to VFChatAI - Account Activated!

Hello %s!

Congratulations! Your VFChatAI account was successfully activated on %s.

Account Status:
✅ Email: %s
✅ Phone: %s
✅ Account: Active

What you can do now:
🤖 Access cutting-edge AI models
💬 Unlimited conversations
🔒 Private and secure chats
📱 Use on any device

Quick Start:
1. Visit vfchatai.com
2. Click "Start Chatting" to begin
3. Ask anything - our AI is ready to help!

Start your first conversation: https://vfchatai.com/chat

Need help? Visit our documentation at https://vfchatai.com/docs or contact support@vfchatai.com

Welcome aboard!
The VFChatAI Team
""", vars.get("fullName"), vars.get("activationTime"), vars.get("emailVerified"), vars.get("phoneVerified"));
    }

    private String buildUsageSummaryEmail(Map<String, String> vars) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Your VFChatAI Monthly Summary</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f8f9fa; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 15px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #667eea, #764ba2); padding: 30px; text-align: center; color: white; }
        .content { padding: 30px; }
        .stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 30px 0; }
        .stat-card { background: #f8f9fa; padding: 20px; border-radius: 10px; text-align: center; border: 2px solid #e9ecef; }
        .stat-number { font-size: 36px; font-weight: bold; color: #667eea; margin-bottom: 10px; }
        .stat-label { font-size: 14px; color: #6c757d; }
        .highlight { background: linear-gradient(135deg, #667eea, #764ba2); color: white; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 Your %s Summary</h1>
            <p>Here's how you used VFChatAI this month</p>
        </div>
        
        <div class="content">
            <h2>Hello %s!</h2>
            <p>Thanks for being an amazing VFChatAI user! Here's your activity summary for %s:</p>
            
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-number">%s</div>
                    <div class="stat-label">Total Logins</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number">%s</div>
                    <div class="stat-label">Messages Sent</div>
                </div>
                <div class="stat-card">
                    <div class="stat-number">%s</div>
                    <div class="stat-label">Avg Session Time</div>
                </div>
                <div class="stat-card highlight">
                    <div class="stat-number">%s</div>
                    <div class="stat-label">Top Feature Used</div>
                </div>
            </div>
            
            <h3>🎯 What's New This Month</h3>
            <ul>
                <li>🚀 Improved AI response speed by 40%%</li>
                <li>🎨 New conversation themes and customizations</li>
                <li>📱 Enhanced mobile experience</li>
                <li>🔒 Advanced privacy controls</li>
            </ul>
            
            <p>Keep exploring and chatting! There's always more to discover with VFChatAI.</p>
            
            <div style="text-align: center; margin: 30px 0;">
                <a href="https://vfchatai.com/chat" style="background: linear-gradient(135deg, #667eea, #764ba2); color: white; padding: 15px 30px; border-radius: 10px; text-decoration: none; font-weight: bold;">Continue Chatting</a>
            </div>
        </div>
    </div>
</body>
</html>""", vars.get("month"), vars.get("fullName"), vars.get("month"),
                vars.get("totalLogins"), vars.get("totalMessages"), vars.get("averageSessionTime"), vars.get("topFeature"));
    }

    private String buildUsageSummaryTextEmail(Map<String, String> vars) {
        return String.format("""
📊 Your VFChatAI %s Summary

Hello %s!

Thanks for being an amazing VFChatAI user! Here's your activity summary for %s:

📈 Your Stats:
• Total Logins: %s
• Messages Sent: %s
• Average Session Time: %s
• Top Feature Used: %s

🎯 What's New This Month:
• 🚀 Improved AI response speed by 40%%
• 🎨 New conversation themes and customizations
• 📱 Enhanced mobile experience
• 🔒 Advanced privacy controls

Keep exploring and chatting! There's always more to discover with VFChatAI.

Continue chatting: https://vfchatai.com/chat

Best regards,
The VFChatAI Team
""", vars.get("month"), vars.get("fullName"), vars.get("month"),
                vars.get("totalLogins"), vars.get("totalMessages"), vars.get("averageSessionTime"), vars.get("topFeature"));
    }

    private String getLocationFromIP(String ipAddress) {
        // Simplified location detection - in production, use a proper IP geolocation service
        if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            return "Local Network";
        }
        return "Unknown Location"; // In production, integrate with MaxMind, IPinfo, etc.
    }

    /**
     * Usage Summary data class
     */
    public static class UsageSummary {
        private final String month;
        private final int totalLogins;
        private final int totalMessages;
        private final String averageSessionTime;
        private final String topFeature;

        public UsageSummary(String month, int totalLogins, int totalMessages,
                            String averageSessionTime, String topFeature) {
            this.month = month;
            this.totalLogins = totalLogins;
            this.totalMessages = totalMessages;
            this.averageSessionTime = averageSessionTime;
            this.topFeature = topFeature;
        }

        public String getMonth() { return month; }
        public int getTotalLogins() { return totalLogins; }
        public int getTotalMessages() { return totalMessages; }
        public String getAverageSessionTime() { return averageSessionTime; }
        public String getTopFeature() { return topFeature; }
    }
}