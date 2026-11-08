package com.pluchon.meng.common.web.handler;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.error.DependencyFailures;
import com.pluchon.meng.common.core.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // 未预期异常的日志
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> business(BusinessException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus()).body(
            ApiResponse.failure(exception.errorCode(), exception.data())
        );
    }

    @ExceptionHandler({
        org.springframework.web.multipart.MaxUploadSizeExceededException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
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

    // 依赖不可用返回 503，其余返回 500；两种都留下堆栈，响应里不带细节
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        if (DependencyFailures.unavailable(exception)) {
            LOG.error("Dependency unavailable: {}", exception.getClass().getName(), exception);
            return ResponseEntity.status(503).body(ApiResponse.failure(CommonErrorCode.UNAVAILABLE, null));
        }
        LOG.error("Unhandled exception: {}", exception.getClass().getName(), exception);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, null));
    }
}
