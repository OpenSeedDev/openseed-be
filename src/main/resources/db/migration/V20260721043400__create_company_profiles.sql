CREATE TABLE company_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    company_email VARCHAR(254) NOT NULL,
    company_domain VARCHAR(253) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_company_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_company_profiles_email UNIQUE (company_email),
    CONSTRAINT fk_company_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_company_profiles_name_trimmed CHECK (company_name = btrim(company_name) AND length(company_name) BETWEEN 1 AND 100),
    CONSTRAINT ck_company_profiles_email_normalized CHECK (company_email = lower(company_email)),
    CONSTRAINT ck_company_profiles_domain_normalized CHECK (company_domain = lower(company_domain))
);

CREATE INDEX idx_company_profiles_domain ON company_profiles (company_domain);
