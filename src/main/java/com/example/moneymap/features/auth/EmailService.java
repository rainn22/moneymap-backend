package com.example.moneymap.features.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String token) {
        String verificationLink = "http://localhost:3000/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("nonsorany@gmail.com");

        message.setTo(toEmail);
        message.setSubject("Verify your MoneyMap Account");
        message.setText("Welcome to MoneyMap!\n\nPlease click the link below to verify your email address:\n"
                + verificationLink);

        try {
            mailSender.send(message);
            System.out.println("Email sent successfully to " + toEmail);
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            // Added this so you can still verify manually if it fails
            System.out.println("MANUAL VERIFICATION LINK: " + verificationLink);
        }
    }
}
