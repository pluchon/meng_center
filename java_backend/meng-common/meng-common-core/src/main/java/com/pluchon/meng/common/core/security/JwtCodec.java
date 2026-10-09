package com.pluchon.meng.common.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKey;

/** JWT contains only the session lookup key; Redis controls expiry and revocation. */
public final class JwtCodec {

    private static final String USER_ID_CLAIM = "userId";
    private static final String SESSION_ID_CLAIM = "sessionId";
    private static final Set<String> CLAIM_NAMES = Set.of(USER_ID_CLAIM, SESSION_ID_CLAIM);
    private final SecretKey key;
    private final JwtParser parser;

    public JwtCodec(String base64Secret) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT signing key must be Base64 encoded");
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("JWT signing key must contain 32 bytes");
        }
        key = Keys.hmacShaKeyFor(decoded);
        parser = Jwts.parser().verifyWith(key).sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    public String encode(SessionKey sessionKey) {
        return Jwts.builder()
            .claim(USER_ID_CLAIM, sessionKey.userId())
            .claim(SESSION_ID_CLAIM, sessionKey.sessionId())
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public SessionKey decode(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (!claims.keySet().equals(CLAIM_NAMES)) {
            throw new IllegalArgumentException("Unexpected JWT claims");
        }
        Number userId = claims.get(USER_ID_CLAIM, Number.class);
        String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
        if (userId == null || userId.doubleValue() != userId.longValue()) {
            throw new IllegalArgumentException("Invalid JWT identity");
        }
        return new SessionKey(userId.longValue(), sessionId);
    }
}
