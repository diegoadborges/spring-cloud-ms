package me.diego.spring.cloud.ms.token.security.token.converter;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.diego.spring.cloud.ms.core.property.JwtConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.text.ParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenConverter {

    public SignedJWT decryptToken(String encryptedToken) {
        log.info("Decrypting token");
        try {
            JWEObject jweObject = JWEObject.parse(encryptedToken);

            DirectDecrypter directDecrypter = new DirectDecrypter(JwtConfiguration.PRIVATE_KEY.getBytes());

            jweObject.decrypt(directDecrypter);

            log.info("Token decrypted, return signed token . . .");

            return jweObject.getPayload().toSignedJWT();
        } catch (ParseException | JOSEException e) {
            log.error("Invalid token");
            throw new AccessDeniedException("invalid token");
        }
    }

    public SignedJWT validateTokenSignature(String signedToken) {
        log.info("Starting method validate token signature . . .");
        try {
            SignedJWT signedJWT = SignedJWT.parse(signedToken);

            log.info("Token parsed! Retrieving public key from signed token");

            RSAKey publicKey = RSAKey.parse(signedJWT.getHeader().getJWK().toJSONObject());

            log.info("Public key retrieved, validating signature . . .");

            if (!signedJWT.verify(new RSASSAVerifier(publicKey))) {
                throw new AccessDeniedException("Invalid token signature");
            }

            log.info("The token has a valid signature");

            return signedJWT;
        } catch (ParseException | JOSEException e) {
            log.error("Invalid token");
            throw new AccessDeniedException("invalid token");
        }
    }
}
