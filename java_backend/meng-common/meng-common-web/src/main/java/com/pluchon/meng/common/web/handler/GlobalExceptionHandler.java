package com.pluchon.meng.common.web.handler;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 统一异常处理：业务异常按错误码返回，其余异常不向外透露细节
@RestControllerAdvice
public final class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> business(BusinessException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus()).body(
            ApiResponse.failure(exception.errorCode(), exception.data())
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
    })
    public ResponseEntity<ApiResponse<Void>> invalidArgument(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_ARGUMENT, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.failure(CommonErrorCode.NOT_FOUND, null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405).body(ApiResponse.failure(CommonErrorCode.METHOD_NOT_ALLOWED, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        // 请求内容和异常信息里可能有联系方式、密码或票据，不回显也不记录
        return ResponseEntity.internalServerError().body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, null));
    }
}
