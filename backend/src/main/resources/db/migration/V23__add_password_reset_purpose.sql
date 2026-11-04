-- A fourth verification purpose: proving control of a contact in order to reset a forgotten password.
--
-- It has to be its own purpose rather than reusing LOGIN. The purpose is fixed when a code is
-- requested and checked again when it is redeemed, which is what stops a code obtained for one flow
-- from working in another. Reusing LOGIN here would mean a code sent to "reset your password" would
-- also sign somebody straight in, and vice versa.
ALTER TABLE verification_codes
    DROP CONSTRAINT ck_verification_codes_purpose;

ALTER TABLE verification_codes
    ADD CONSTRAINT ck_verification_codes_purpose CHECK (
        purpose IN ('LOGIN', 'REGISTRATION', 'CONTACT_VERIFICATION', 'PASSWORD_RESET')
    );
