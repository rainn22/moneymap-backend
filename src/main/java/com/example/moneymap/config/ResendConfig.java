package com.example.moneymap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resend.Resend;

@Configuration
public class ResendConfig {

    @Bean
    public Resend resendClient(@Value("${app.mail.resend.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new Resend(apiKey);
    }
}
