package com.cafe.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${spring.jwt.secret}")
    private String secret;

    @Value("${gateway.secret}")
    private String gatewaySecret;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static final List<String> PERMIT_PATHS = List.of(
            "/login", "/auth/signup", "/auth/reissue"
    );

    @Override
    public int getOrder() { return -1; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (PERMIT_PATHS.contains(path)) {
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r
                            .header("X-Gateway-Secret", gatewaySecret))
                    .build();
            return chain.filter(mutated);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, ErrorCode.AUTH_TOKEN_INVALID);
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, ErrorCode.AUTH_TOKEN_EXPIRE);
        } catch (Exception e) {
            return unauthorized(exchange, ErrorCode.AUTH_TOKEN_INVALID);
        }

        String jti = claims.getId();
        String uuid = claims.getSubject();

        String roleRaw = claims.get("role", String.class);
        if (!StringUtils.hasText(roleRaw))
            return unauthorized(exchange, ErrorCode.AUTH_TOKEN_EXPIRE);
        String role = roleRaw.replace("ROLE_", "");

        return redisTemplate.hasKey("blacklist:" + jti)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return unauthorized(exchange, ErrorCode.AUTH_TOKEN_LOGOUT);
                    }
                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id", uuid)
                                    .header("X-User-Role", role)
                                    .header("X-Gateway-Secret", gatewaySecret))
                            .build();
                    return chain.filter(mutated);
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ErrorCode errorCode) {
        exchange.getResponse().setStatusCode(errorCode.getStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":\"%s\", \"message\":\"%s\"}", errorCode.getCode(), errorCode.getMessage());

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
