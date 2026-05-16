package com.cafe.apigateway.filter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    AUTH_TOKEN_INVALID("G001", "유효하지 않은 토큰입니다.",HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_LOGOUT("G002", "이미 로그아웃된 토큰입니다..", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRE ("G003", "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
