package com.seedrank.common.error;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seedrank.auth.signup.EmailAlreadyExistsException;
import com.seedrank.auth.signup.SignupValidationException;
import com.seedrank.auth.login.InvalidCredentialsException;
import com.seedrank.auth.login.InvalidRefreshTokenException;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.member.profile.ProfileIdValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException exception, HttpServletRequest request) {
        var expired = ResponseCookie.from("refresh_token", "").httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/api/v1/auth").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(ApiError.of("INVALID_REFRESH_TOKEN", "인증을 갱신할 수 없습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(InvalidAccessTokenException.class)
    ResponseEntity<ApiError> handleInvalidAccessToken(InvalidAccessTokenException exception, HttpServletRequest request) {
        var expired = ResponseCookie.from("refresh_token", "").httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/api/v1/auth").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(ApiError.of("INVALID_ACCESS_TOKEN", "인증이 유효하지 않습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "입력값을 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ApiError> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "EMAIL_ALREADY_EXISTS",
                exception.getMessage(),
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(SignupValidationException.class)
    ResponseEntity<ApiError> handleSignupValidation(
            SignupValidationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                exception.getCode(),
                exception.getMessage(),
                requestId(request),
                List.of(new ApiFieldError(exception.getField(), exception.getMessage()))));
    }

    @ExceptionHandler(ProfileIdValidationException.class)
    ResponseEntity<ApiError> handleProfileIdValidation(
            ProfileIdValidationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "INVALID_PROFILE_ID",
                exception.getMessage(),
                requestId(request),
                List.of(new ApiFieldError("profileId", exception.getMessage()))));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR",
                "입력값을 확인해 주세요.",
                requestId(request),
                fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                "INTERNAL_SERVER_ERROR",
                "요청을 처리하지 못했습니다.",
                requestId(request),
                List.of()));
    }

    private String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-Id");
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
