package com.catchmind_be.common.exception;

import com.catchmind_be.common.exception.code.ErrorCode;
import com.catchmind_be.common.exception.response.ApiResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // ✅ @Valid @RequestBody 유효성 실패
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors()
        .stream()
        .map(FieldErrorResponse::from)
        .collect(Collectors.toList());
    log.warn("Validation failed: {}", fieldErrors);
    return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "요청 값이 유효하지 않습니다.", fieldErrors);
  }

  // ✅ @Validated + @ModelAttribute/@RequestParam 유효성 실패
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleBindException(BindException ex) {
    List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors()
        .stream()
        .map(FieldErrorResponse::from)
        .collect(Collectors.toList());
    log.warn("Bind validation failed: {}", fieldErrors);
    return buildErrorResponse(ErrorCode.VALIDATION_ERROR, "요청 값이 유효하지 않습니다.", fieldErrors);
  }

  // ✅ 파라미터 타입 불일치
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String errorMsg = String.format("'%s' 파라미터는 '%s' 타입이어야 합니다.",
        ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "알 수 없음");
    log.warn("Type mismatch: {}", errorMsg);
    return buildErrorResponse(ErrorCode.TYPE_MISMATCH, errorMsg);
  }

  // ✅ 필수 파라미터 누락
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
    String errorMsg = String.format("필수 파라미터 '%s'가 누락되었습니다.", ex.getParameterName());
    log.warn("Missing parameter: {}", errorMsg);
    return buildErrorResponse(ErrorCode.MISSING_REQUIRED_PARAMETER, errorMsg);
  }

  // ✅ JSON 파싱 실패
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
    log.warn("Invalid JSON format", ex);
    return buildErrorResponse(ErrorCode.INVALID_JSON_FORMAT, ErrorCode.INVALID_JSON_FORMAT.getMessage());
  }

  // ✅ 지원하지 않는 HTTP 메서드
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    log.warn("Unsupported method: {}", ex.getMethod());
    return buildErrorResponse(ErrorCode.INVALID_REQUEST, "허용되지 않은 요청 메서드입니다.");
  }

  // ✅ 지원하지 않는 미디어 타입
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
    log.warn("Unsupported media type", ex);
    return buildErrorResponse(ErrorCode.INVALID_REQUEST, "지원하지 않는 미디어 타입입니다.");
  }

  // ✅ 커스텀 예외
  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
    log.warn("CustomException: {}", ex.getErrorCode().getCode());
    return buildErrorResponse(ex.getErrorCode(), ex.getMessage());
  }

  // ✅ 그 외 모든 예외 (서버 내부 오류)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
    log.error("Unexpected error", ex);
    return buildErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
  }

  // ✅ 공통 응답 생성 (데이터 없는 경우)
  private ResponseEntity<ApiResponse<Void>> buildErrorResponse(BaseErrorCode errorCode, String message) {
    ApiResponse<Void> response = ApiResponse.error(errorCode, message);
    return new ResponseEntity<>(response, errorCode.getHttpStatus());
  }

  // ✅ 공통 응답 생성 (데이터 포함)
  private <T> ResponseEntity<ApiResponse<T>> buildErrorResponse(BaseErrorCode errorCode, String message, T data) {
    ApiResponse<T> response = ApiResponse.error(errorCode, message, data);
    return new ResponseEntity<>(response, errorCode.getHttpStatus());
  }

  // ✅ 필드 오류 응답 DTO
  public record FieldErrorResponse(String field, String message) {
    public static FieldErrorResponse from(FieldError error) {
      return new FieldErrorResponse(error.getField(), error.getDefaultMessage());
    }
  }
}