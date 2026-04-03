package com.example.moneymap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resend.Resend;

@Configuration
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "resend")
public class ResendConfig {

    @Bean
    public Resend resendClient(@Value("${app.mail.resend.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is required when MAIL_PROVIDER=resend");
        }
        return new Resend(apiKey);
    }
}
