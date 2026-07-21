ALTER TABLE seed_unit_lots
    ADD COLUMN purchase_request_key VARCHAR(100);

UPDATE seed_unit_lots
SET purchase_request_key = 'legacy:' || id::text;

ALTER TABLE seed_unit_lots
    ALTER COLUMN purchase_request_key SET NOT NULL;

ALTER TABLE seed_unit_lots
    ADD CONSTRAINT uk_seed_unit_lots_user_request
        UNIQUE (user_id, purchase_request_key);
