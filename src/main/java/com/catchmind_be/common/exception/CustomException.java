package com.catchmind_be.common.exception;

import com.catchmind_be.common.exception.code.ErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
  private final BaseErrorCode errorCode;

  public CustomException(BaseErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public CustomException(ErrorCode errorCode, String message) {
    super(errorCode.getMessage() + message);
    this.errorCode = errorCode;
  }

}
