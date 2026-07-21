package com.seedrank.company.verification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpCompanyVerificationMailSender implements CompanyVerificationMailSender {
    private final JavaMailSender mailSender;
    private final String confirmUrl;
    private final String mailFrom;

    SmtpCompanyVerificationMailSender(
            JavaMailSender mailSender,
            @Value("${app.company-verification.confirm-url}") String confirmUrl,
            @Value("${app.company-verification.mail-from}") String mailFrom) {
        this.mailSender = mailSender;
        this.confirmUrl = confirmUrl;
        this.mailFrom = mailFrom;
    }

    @Override
    public void send(String companyEmail, String rawToken, Instant expiresAt) {
        String separator = confirmUrl.contains("?") ? "&" : "?";
        String link = confirmUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        var message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(companyEmail);
        message.setSubject("[SeedRank] 회사 이메일을 인증해 주세요");
        message.setText("아래 링크에서 회사 이메일 인증을 완료해 주세요.\n\n"
                + link + "\n\n만료 시각: " + expiresAt);
        mailSender.send(message);
    }
}
