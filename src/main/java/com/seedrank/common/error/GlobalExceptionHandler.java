package com.seedrank.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.seedrank.auth.signup.EmailAlreadyExistsException;
import com.seedrank.auth.signup.SignupValidationException;
import com.seedrank.auth.login.InvalidCredentialsException;
import com.seedrank.auth.login.InvalidRefreshTokenException;
import com.seedrank.auth.login.InvalidAccessTokenException;
import com.seedrank.company.profile.CompanyEmailDomainNotAllowedException;
import com.seedrank.company.profile.CompanyProfileAlreadyExistsException;
import com.seedrank.company.profile.CompanyProfileValidationException;
import com.seedrank.company.verification.CompanyAlreadyVerifiedException;
import com.seedrank.company.verification.CompanyProfileRequiredException;
import com.seedrank.company.verification.InvalidCompanyVerificationTokenException;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.idea.archive.IdeaNotArchivableException;
import com.seedrank.feedback.manage.FeedbackNotFoundException;
import com.seedrank.feedback.accept.FeedbackAlreadyAcceptedException;
import com.seedrank.idea.publish.IdeaAlreadyPublishedException;
import com.seedrank.idea.publish.IdeaNotReadyToPublishException;
import com.seedrank.idea.update.IdeaNotPublishedException;
import com.seedrank.ai.job.IdempotencyKeyReusedException;
import com.seedrank.ai.job.AiJobNotFoundException;
import com.seedrank.ai.job.AiJobAlreadySelectedException;
import com.seedrank.ai.job.AiJobNotSelectableException;
import com.seedrank.member.profile.ProfileIdValidationException;
import com.seedrank.messaging.thread.MessageThreadIdeaNotFoundException;
import com.seedrank.messaging.thread.VerifiedCompanyRequiredException;
import com.seedrank.unit.purchase.InsufficientPointException;
import com.seedrank.unit.purchase.IdempotencyKeyValidationException;
import com.seedrank.unit.purchase.PurchaseLimitExceededException;
import com.seedrank.unit.purchase.SelfUnitPurchaseException;
import com.seedrank.unit.purchase.UnitPriceChangedException;
import com.seedrank.unit.purchase.UnitPurchaseIdeaNotFoundException;
import com.seedrank.unit.recovery.PartialRecoveryNotSupportedException;
import com.seedrank.unit.recovery.SeedUnitLockedException;
import com.seedrank.unit.recovery.SeedUnitLotNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletRequest;
import com.seedrank.ops.http.RequestIdFilter;
import org.springframework.http.converter.HttpMessageNotReadableException;

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR",
                "입력값을 확인해 주세요.",
                requestId(request),
                List.of()));
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

    @ExceptionHandler(CompanyEmailDomainNotAllowedException.class)
    ResponseEntity<ApiError> handleCompanyEmailDomainNotAllowed(
            CompanyEmailDomainNotAllowedException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "COMPANY_EMAIL_DOMAIN_NOT_ALLOWED", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(CompanyProfileAlreadyExistsException.class)
    ResponseEntity<ApiError> handleCompanyProfileAlreadyExists(
            CompanyProfileAlreadyExistsException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "COMPANY_PROFILE_ALREADY_EXISTS", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(CompanyProfileValidationException.class)
    ResponseEntity<ApiError> handleCompanyProfileValidation(
            CompanyProfileValidationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR", "입력값을 확인해 주세요.", requestId(request),
                List.of(new ApiFieldError(exception.getField(), exception.getMessage()))));
    }

    @ExceptionHandler(CompanyProfileRequiredException.class)
    ResponseEntity<ApiError> handleCompanyProfileRequired(
            CompanyProfileRequiredException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "COMPANY_PROFILE_REQUIRED", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(CompanyAlreadyVerifiedException.class)
    ResponseEntity<ApiError> handleCompanyAlreadyVerified(
            CompanyAlreadyVerifiedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "COMPANY_ALREADY_VERIFIED", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(InvalidCompanyVerificationTokenException.class)
    ResponseEntity<ApiError> handleInvalidCompanyVerificationToken(
            InvalidCompanyVerificationTokenException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                "INVALID_COMPANY_VERIFICATION_TOKEN", exception.getMessage(), requestId(request), List.of()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR", "입력값을 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdeaNotReadyToPublishException.class)
    ResponseEntity<ApiError> handleIdeaNotReadyToPublish(
            IdeaNotReadyToPublishException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "IDEA_NOT_READY_TO_PUBLISH", "게시 필수 내용을 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdeaAlreadyPublishedException.class)
    ResponseEntity<ApiError> handleIdeaAlreadyPublished(
            IdeaAlreadyPublishedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "IDEA_ALREADY_PUBLISHED", "이미 게시된 아이디어입니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdeaNotPublishedException.class)
    ResponseEntity<ApiError> handleIdeaNotPublished(
            IdeaNotPublishedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "IDEA_NOT_PUBLISHED", "게시된 아이디어만 수정할 수 있습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdeaNotArchivableException.class)
    ResponseEntity<ApiError> handleIdeaNotArchivable(
            IdeaNotArchivableException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "IDEA_NOT_PUBLISHED", "게시된 아이디어만 보관할 수 있습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdeaDraftNotFoundException.class)
    ResponseEntity<ApiError> handleIdeaDraftNotFound(
            IdeaDraftNotFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "IDEA_NOT_FOUND",
                "아이디어를 찾을 수 없습니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(VerifiedCompanyRequiredException.class)
    ResponseEntity<ApiError> handleVerifiedCompanyRequired(
            VerifiedCompanyRequiredException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                "VERIFIED_COMPANY_REQUIRED", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(MessageThreadIdeaNotFoundException.class)
    ResponseEntity<ApiError> handleMessageThreadIdeaNotFound(
            MessageThreadIdeaNotFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "IDEA_NOT_FOUND", exception.getMessage(), requestId(request), List.of()));
    }

    @ExceptionHandler(FeedbackNotFoundException.class)
    ResponseEntity<ApiError> handleFeedbackNotFound(
            FeedbackNotFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "FEEDBACK_NOT_FOUND",
                "피드백을 찾을 수 없습니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(FeedbackAlreadyAcceptedException.class)
    ResponseEntity<ApiError> handleFeedbackAlreadyAccepted(
            FeedbackAlreadyAcceptedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "FEEDBACK_ALREADY_ACCEPTED",
                "이미 채택된 피드백입니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(UnitPurchaseIdeaNotFoundException.class)
    ResponseEntity<ApiError> handleUnitPurchaseIdeaNotFound(
            UnitPurchaseIdeaNotFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "IDEA_NOT_FOUND", "아이디어를 찾을 수 없습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(SelfUnitPurchaseException.class)
    ResponseEntity<ApiError> handleSelfUnitPurchase(SelfUnitPurchaseException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                "SELF_UNIT_PURCHASE", "본인 아이디어의 Unit은 구매할 수 없습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(UnitPriceChangedException.class)
    ResponseEntity<ApiError> handleUnitPriceChanged(UnitPriceChangedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "PRICE_CHANGED", "Unit 가격이 변경되었습니다. 현재 가격을 다시 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(InsufficientPointException.class)
    ResponseEntity<ApiError> handleInsufficientPoint(InsufficientPointException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "INSUFFICIENT_POINT", "사용 가능한 Point가 부족합니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(PurchaseLimitExceededException.class)
    ResponseEntity<ApiError> handlePurchaseLimitExceeded(
            PurchaseLimitExceededException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "PURCHASE_LIMIT_EXCEEDED", "Seed Unit 구매 한도를 초과했습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdempotencyKeyValidationException.class)
    ResponseEntity<ApiError> handleIdempotencyKeyValidation(
            IdempotencyKeyValidationException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_ERROR", "Idempotency-Key를 확인해 주세요.", requestId(request), List.of()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ApiError> handleAiIdempotencyKeyReused(
            IdempotencyKeyReusedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key가 다른 요청에 이미 사용됐습니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(com.seedrank.unit.purchase.IdempotencyKeyReusedException.class)
    ResponseEntity<ApiError> handleUnitPurchaseIdempotencyKeyReused(
            com.seedrank.unit.purchase.IdempotencyKeyReusedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "IDEMPOTENCY_KEY_REUSED", "같은 Idempotency-Key를 다른 구매에 사용할 수 없습니다.",
                requestId(request), List.of()));
    }

    @ExceptionHandler(SeedUnitLotNotFoundException.class)
    ResponseEntity<ApiError> handleSeedUnitLotNotFound(
            SeedUnitLotNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "UNIT_LOT_NOT_FOUND", "회수할 Seed Unit Lot을 찾을 수 없습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(SeedUnitLockedException.class)
    ResponseEntity<ApiError> handleSeedUnitLocked(SeedUnitLockedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "UNIT_LOCKED", "구매 후 24시간이 지나야 회수할 수 있습니다.", requestId(request), List.of()));
    }

    @ExceptionHandler(PartialRecoveryNotSupportedException.class)
    ResponseEntity<ApiError> handlePartialRecoveryNotSupported(
            PartialRecoveryNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                "PARTIAL_RECOVERY_NOT_SUPPORTED", "Lot은 전체 Unit만 회수할 수 있습니다.",
                requestId(request), List.of()));
    }

    @ExceptionHandler(AiJobNotFoundException.class)
    ResponseEntity<ApiError> handleAiJobNotFound(
            AiJobNotFoundException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                "AI_JOB_NOT_FOUND",
                "AI 생성 Job을 찾을 수 없습니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(AiJobNotSelectableException.class)
    ResponseEntity<ApiError> handleAiJobNotSelectable(
            AiJobNotSelectableException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "AI_JOB_NOT_SELECTABLE",
                "완료된 AI 후보 결과만 Draft로 선택할 수 있습니다.",
                requestId(request),
                List.of()));
    }

    @ExceptionHandler(AiJobAlreadySelectedException.class)
    ResponseEntity<ApiError> handleAiJobAlreadySelected(
            AiJobAlreadySelectedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "AI_JOB_ALREADY_SELECTED",
                "이 AI Job에서는 이미 Draft가 생성됐습니다.",
                requestId(request),
                List.of()));
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
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return requestId instanceof String value ? value : "unavailable";
    }
}
