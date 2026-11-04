package com.minibank.auth.service;

import com.minibank.auth.domain.RefreshToken;
import com.minibank.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a refresh token family in its own transaction.
 *
 * <p>This exists because of a subtle ordering trap. Reuse detection ends by rejecting the request,
 * and rejecting means throwing — which rolls back the surrounding transaction. Revoking the family
 * inside that transaction would therefore be undone by the very exception that reports the
 * detection, leaving the stolen family alive: the check would appear to work (the caller gets a
 * 401) while silently doing nothing.
 *
 * <p>Committing separately makes the revocation survive the rejection. It is a separate bean because
 * Spring's transaction proxy does not intercept self-invocation, so calling a {@code REQUIRES_NEW}
 * method on {@code this} would quietly run in the caller's transaction and reintroduce the bug.
 */
@Service
public class TokenFamilyRevoker {

    private static final Logger log = LoggerFactory.getLogger(TokenFamilyRevoker.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public TokenFamilyRevoker(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    /**
     * Revokes every live token in a family and commits immediately.
     *
     * @return how many tokens were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamilyNow(UUID familyId, RefreshToken.RevocationReason reason) {
        List<RefreshToken> live = refreshTokenRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId);
        live.forEach(token -> token.revoke(clock.instant(), reason));
        if (!live.isEmpty()) {
            log.warn("Revoked {} refresh token(s) in family {} ({})", live.size(), familyId, reason);
        }
        return live.size();
    }
}
