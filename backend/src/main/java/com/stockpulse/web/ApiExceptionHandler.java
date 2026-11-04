package com.stockpulse.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps expected failures to stable API codes and keeps detailed diagnostics in server logs. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ApiError(String code,String message,String requestId) { }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(java.util.NoSuchElementException ex,HttpServletRequest req) {
        String code=ex.getMessage()!=null&&ex.getMessage().contains("Product")?"PRODUCT_NOT_FOUND":"RESOURCE_NOT_FOUND";
        return report(HttpStatus.NOT_FOUND,code,ex.getMessage()==null?"We couldn't find that item.":ex.getMessage(),req,ex,false);
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException ex,HttpServletRequest req) {
        return report(HttpStatus.CONFLICT,"OPERATION_CONFLICT",safeMessage(ex,"This change conflicts with the current inventory state."),req,ex,false);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex,HttpServletRequest req) {
        return report(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Your account does not have permission to do that.",req,ex,false);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> constraint(DataIntegrityViolationException ex,HttpServletRequest req) {
        return report(HttpStatus.CONFLICT,"DATA_CONSTRAINT_VIOLATION","This item conflicts with existing data. Check the SKU and try again.",req,ex,false);
    }
    @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class})
    ResponseEntity<ApiError> badRequest(RuntimeException ex,HttpServletRequest req) {
        return report(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",safeMessage(ex,"Check the entered values and try again."),req,ex,false);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex,HttpServletRequest req) {
        String message=ex.getBindingResult().getFieldErrors().stream().findFirst().map(e->e.getField()+": "+e.getDefaultMessage()).orElse("Check the entered values and try again.");
        return report(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",message,req,ex,false);
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate(OptimisticLockingFailureException ex,HttpServletRequest req) {
        return report(HttpStatus.CONFLICT,"CONCURRENT_UPDATE","This record changed while you were reviewing it. Refresh and try again.",req,ex,false);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex,HttpServletRequest req) {
        return report(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","Something went wrong. Please try again. If it continues, share the reference code with support.",req,ex,true);
    }
    private ResponseEntity<ApiError> report(HttpStatus status,String code,String message,HttpServletRequest req,Exception ex,boolean stack) {
        String requestId=UUID.randomUUID().toString();
        if(stack) log.error("api_error code={} requestId={} method={} path={}",code,requestId,req.getMethod(),req.getRequestURI(),ex);
        else log.warn("api_error code={} requestId={} status={} method={} path={} detail={}",code,requestId,status.value(),req.getMethod(),req.getRequestURI(),ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(new ApiError(code,message,requestId));
    }
    private String safeMessage(RuntimeException ex,String fallback) { return ex.getMessage()==null||ex.getMessage().isBlank()?fallback:ex.getMessage(); }
}
