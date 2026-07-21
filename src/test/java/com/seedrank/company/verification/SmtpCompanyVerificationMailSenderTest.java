package com.seedrank.company.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpCompanyVerificationMailSenderTest {
    @Test
    void sendsTheRawTokenOnlyInsideTheCompanyEmailConfirmationLink() {
        JavaMailSender provider = mock(JavaMailSender.class);
        var sender = new SmtpCompanyVerificationMailSender(
                provider,
                "https://frontend.example/company/confirm?source=mail",
                "no-reply@seedrank.example");
        Instant expiresAt = Instant.parse("2026-07-21T06:30:00Z");

        sender.send("member@corp.example", "url_safe-token", expiresAt);

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(provider).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@seedrank.example");
        assertThat(message.getValue().getTo()).containsExactly("member@corp.example");
        assertThat(message.getValue().getText())
                .contains("https://frontend.example/company/confirm?source=mail&token=url_safe-token")
                .contains(expiresAt.toString());
    }
}
