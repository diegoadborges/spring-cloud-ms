package me.diego.spring.cloud.ms.security.config.filter;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.diego.spring.cloud.ms.core.domain.ApplicationUser;
import me.diego.spring.cloud.ms.core.property.JwtConfiguration;
import me.diego.spring.cloud.ms.exception.domain.InvalidTokenException;
import me.diego.spring.cloud.ms.token.security.token.converter.TokenConverter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GatewayAuthorizationFilter implements WebFilter {
    private final TokenConverter tokenConverter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        List<String> headerList = exchange.getRequest().getHeaders().get(JwtConfiguration.HEADER_NAME);

        if (headerList == null || headerList.isEmpty()) {
            return chain.filter(exchange);
        }

        String header = headerList.get(0);

        if (!header.startsWith(JwtConfiguration.HEADER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = header.replace(JwtConfiguration.HEADER_PREFIX, "").trim();

        String signedToken = tokenConverter.decryptToken(token).serialize();

        tokenConverter.validateTokenSignature(signedToken);

        Authentication auth = createAuthentication(signedToken);

        if (JwtConfiguration.TYPE.equalsIgnoreCase("signed")) {
            ServerHttpRequest mutateRequest = exchange.getRequest()
                    .mutate()
                    .header(JwtConfiguration.HEADER_NAME, JwtConfiguration.HEADER_PREFIX + signedToken).build();

            return chain.filter(exchange.mutate().request(mutateRequest).build())
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }

        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private Authentication createAuthentication(String signedToken) {
        try {
            JWTClaimsSet claims = getClaims(signedToken);
            Instant expirationTime = claims.getExpirationTime().toInstant();

            if (expirationTime.isBefore(Instant.now())) {
                throw new InvalidTokenException(HttpStatus.UNAUTHORIZED);
            }

            List<String> authorities = claims.getStringListClaim("authorities");
            String username = claims.getSubject();

            ApplicationUser applicationUser = ApplicationUser.builder()
                    .id(claims.getLongClaim("userId"))
                    .username(username)
                    .role(String.join(",", authorities))
                    .build();

            var auth = new UsernamePasswordAuthenticationToken(applicationUser, null, parseRoles(authorities));
            auth.setDetails(signedToken);
            return auth;
        } catch (ParseException e) {
            log.error("Error setting security context", e);
            throw new InvalidTokenException(HttpStatus.UNAUTHORIZED);
        }
    }

    private JWTClaimsSet getClaims(String signedToken) {
        JWTClaimsSet claims;
        try {
            claims = SignedJWT.parse(signedToken).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new InvalidTokenException(HttpStatus.UNAUTHORIZED);
        }

        return claims;
    }

    private List<SimpleGrantedAuthority> parseRoles(List<String> roles) {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
