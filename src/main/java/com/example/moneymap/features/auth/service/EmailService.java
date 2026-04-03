package com.example.moneymap.features.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.SendEmailRequest;
import com.resend.services.emails.model.SendEmailResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ObjectProvider<Resend> resendProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.provider:smtp}")
    private String mailProvider;

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
            log.warn("Email sending skipped because app.mail.enabled is false to={} subject={}", toEmail, subject);
            return;
        }

        if ("resend".equalsIgnoreCase(mailProvider)) {
            sendWithResend(toEmail, subject, body);
            return;
        }

        sendWithSmtp(toEmail, subject, body);
    }

    private void sendWithSmtp(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            log.info("Sending email with SMTP from={} to={} subject={}", fromEmail, toEmail, subject);
            mailSender.send(message);
            log.info("Email sent successfully with SMTP to={} subject={}", toEmail, subject);
        } catch (Exception e) {
            log.error("Email sending failed with SMTP from={} to={} subject={} reason={}",
                    fromEmail, toEmail, subject, e.getMessage(), e);
        }
    }

    private void sendWithResend(String toEmail, String subject, String body) {
        Resend resend = resendProvider.getIfAvailable();
        if (resend == null) {
            log.error("Email sending failed because Resend is selected but app.mail.resend.api-key is missing");
            return;
        }

        SendEmailRequest params = SendEmailRequest.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject(subject)
                .text(body)
                .build();

        try {
            log.info("Sending email with Resend from={} to={} subject={}", fromEmail, toEmail, subject);
            SendEmailResponse response = resend.emails().send(params);
            log.info("Email sent successfully with Resend to={} subject={} emailId={}",
                    toEmail, subject, response.getId());
        } catch (ResendException e) {
            log.error("Email sending failed with Resend from={} to={} subject={} reason={}",
                    fromEmail, toEmail, subject, e.getMessage(), e);
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
