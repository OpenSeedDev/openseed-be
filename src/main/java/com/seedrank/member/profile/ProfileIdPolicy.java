package com.seedrank.member.profile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ProfileIdPolicy {

    private static final Pattern ALLOWED_FORMAT = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "seedrank", "openseed");

    public void validate(String profileId) {
        if (profileId == null
                || !ALLOWED_FORMAT.matcher(profileId).matches()
                || RESERVED.contains(profileId.toLowerCase(Locale.ROOT))) {
            throw new ProfileIdValidationException();
        }
    }
}
