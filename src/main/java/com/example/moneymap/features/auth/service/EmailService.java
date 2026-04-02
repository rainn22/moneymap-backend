package com.example.moneymap.features.auth.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;
    @Value("${app.mail.from:no-reply@moneymap.local}")
    private String fromEmail;
    @Value("${app.frontend.verify-url:http://localhost:3000/auth/verify}")
    private String verifyUrl;

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationLink = verifyUrl + "?token=" + token;
        log.info("Sending verification email to={} verifyUrl={} token={}",
                toEmail, verifyUrl, tokenFingerprint(token));
        sendEmail(toEmail, "Verify your MoneyMap Account",
                "Welcome to MoneyMap!\n\nPlease click the link below to verify your email address:\n" + verificationLink);
    }

    public void sendBudgetExceededAlert(String toEmail, String alertMessage) {
        sendEmail(toEmail, "MoneyMap Budget Alert", alertMessage);
    }

    private void sendEmail(String toEmail, String subject, String body) {
        if (!mailEnabled) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Email sending failed to={} subject={} reason={}", toEmail, subject, e.getMessage());
        }
    }

    private String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return "missing";
        }

        String normalizedToken = token.trim();
        int visibleChars = Math.min(6, normalizedToken.length());
        return normalizedToken.substring(normalizedToken.length() - visibleChars);
    }
}
