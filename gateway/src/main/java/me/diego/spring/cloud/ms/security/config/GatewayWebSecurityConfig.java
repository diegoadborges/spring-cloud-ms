package me.diego.spring.cloud.ms.security.config;

import lombok.RequiredArgsConstructor;
import me.diego.spring.cloud.ms.core.property.JwtConfiguration;
import me.diego.spring.cloud.ms.security.config.filter.GatewayAuthorizationFilter;
import me.diego.spring.cloud.ms.token.security.token.converter.TokenConverter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;

@EnableWebFluxSecurity
@Configuration
@RequiredArgsConstructor
@Import(TokenConverter.class)
public class GatewayWebSecurityConfig {
    private final TokenConverter tokenConverter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        String[] swaggerPaths = new String[]{
                "/swagger-resources/**",
                "/webjars/swagger-ui/**",
                "/v3/api-docs/**",
                "/course/v3/api-docs/**",
                "/auth/v3/api-docs/**",
                "/swagger-ui.html",
                "/webjars/swagger-ui/index.html/**"};

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(request -> request.configurationSource(cors -> new CorsConfiguration().applyPermitDefaultValues()))
                .addFilterBefore(new GatewayAuthorizationFilter(tokenConverter), SecurityWebFiltersOrder.AUTHORIZATION)
                .authorizeExchange(req -> req
                        .pathMatchers("/auth/login").permitAll()
                        .pathMatchers(HttpMethod.GET, swaggerPaths).permitAll()
                        .pathMatchers("/course/v1/admin/course/**").hasRole("ROLE_ADMIN")
                        .anyExchange().authenticated()
                )
                .build();

    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("course", r -> r
                        .path("/course/**")
                        .filters(f -> f
                                .rewritePath("/course/(?<path>.*)", "/${path}"))
                        .uri("lb://course"))
                .route("auth", r -> r
                        .path("/auth/**")
                        .filters(f -> f
                                .rewritePath("/auth/(?<path>.*)", "/${path}"))
                        .uri("lb://auth"))
                .build();
    }

}
