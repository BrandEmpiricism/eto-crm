package com.brandempiricism.etocrm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class OidcTokenValidationTest {
    private static final String ISSUER = "https://identity.example.test";
    private final KeyPair keys = keys();

    @Test void signedTokenRequiresTrustedSignatureIssuerAudienceExpiryAndSubject() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(OidcTokenConfiguration.validator(ISSUER, "eto-crm"));
        assertThat(decoder.decode(token(keys, claims())).getSubject()).isEqualTo("verified-user");
        for (var invalid : new JWTClaimsSet.Builder[] {
                claims().issuer("https://untrusted.example.test"), claims().audience("another-app"),
                claims().audience((String) null),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(300))),
                claims().expirationTime(null), claims().subject(null), claims().subject(" ")}) {
            var token = token(keys, invalid);
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
        var forged = token(keys(), claims());
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).audience("eto-crm").subject("verified-user")
            .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }
    private static String token(KeyPair key, JWTClaimsSet.Builder claims) throws Exception {
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
        return token.serialize();
    }
    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException(failure); }
    }
}
