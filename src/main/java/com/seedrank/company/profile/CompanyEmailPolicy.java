package com.seedrank.company.profile;

import java.net.IDN;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class CompanyEmailPolicy {
    private static final Pattern LOCAL_PART = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}$");
    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Set<String> FREE_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "naver.com", "daum.net", "hanmail.net", "kakao.com",
            "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "me.com",
            "proton.me", "protonmail.com");

    NormalizedCompanyEmail normalize(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.strip();
        int separator = email.lastIndexOf('@');
        if (separator <= 0 || separator != email.indexOf('@') || separator == email.length() - 1) {
            throw CompanyProfileValidationException.invalidEmail();
        }

        String localPart = email.substring(0, separator);
        String domain;
        try {
            domain = IDN.toASCII(email.substring(separator + 1), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw CompanyProfileValidationException.invalidEmail();
        }
        if (!LOCAL_PART.matcher(localPart).matches() || !DOMAIN.matcher(domain).matches()) {
            throw CompanyProfileValidationException.invalidEmail();
        }
        if (FREE_DOMAINS.stream().anyMatch(free -> domain.equals(free) || domain.endsWith("." + free))) {
            throw new CompanyEmailDomainNotAllowedException();
        }
        return new NormalizedCompanyEmail(localPart.toLowerCase(Locale.ROOT) + "@" + domain, domain);
    }
}
