package com.catchmind_be.common.exception;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
  HttpStatus getHttpStatus();
  String getCode();
  String getMessage();
}
