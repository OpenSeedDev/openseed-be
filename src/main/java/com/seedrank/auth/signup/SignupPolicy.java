package com.seedrank.auth.signup;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class SignupPolicy {

    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Set<String> RESERVED_PROFILE_IDS = Set.of(
            "admin", "administrator", "root", "system", "support", "seedrank", "openseed");

    String normalizeAndValidateEmail(String email) {
        if (email == null) {
            throw SignupValidationException.invalidEmail();
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw SignupValidationException.invalidEmail();
        }
        return normalized;
    }

    void validatePassword(String password) {
        if (password == null
                || password.isBlank()
                || password.length() < PASSWORD_MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw SignupValidationException.invalidPassword();
        }
    }

    void validateProfileId(String profileId) {
        if (profileId == null
                || !PROFILE_ID_PATTERN.matcher(profileId).matches()
                || RESERVED_PROFILE_IDS.contains(profileId.toLowerCase(Locale.ROOT))) {
            throw SignupValidationException.invalidProfileId();
        }
    }
}
