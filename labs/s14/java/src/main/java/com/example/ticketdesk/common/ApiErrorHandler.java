// src/main/java/com/example/ticketdesk/common/ApiErrorHandler.java
// 사내 표준 오류 응답: RFC 9457 ProblemDetail + 업무 코드 + traceId
package com.example.ticketdesk.common;

import com.example.ticketdesk.request.WorkRequestService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackages = "com.example.ticketdesk")
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    /** 400 — 필드별 메시지를 errors 에 담아 화면이 입력칸 옆에 보여 줄 수 있게 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.computeIfAbsent(fe.getField(), k -> new ArrayList<>()).add(fe.getDefaultMessage()));

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값을 확인하세요");
        body.setProperty("code", "REQ-400-VALIDATION");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** 404 — 업무 코드만 붙여서 */
    @ExceptionHandler(WorkRequestService.NotFound.class)
    public ProblemDetail notFound(WorkRequestService.NotFound ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        body.setProperty("code", "REQ-404");
        return body;
    }

    /** 500 — 내부 정보(스택 트레이스, SQL)는 응답에 넣지 않고 traceId 로 로그와 연결 */
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("처리 중 오류 traceId={}", traceId, ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류입니다. 문의 시 추적 번호를 알려 주세요.");
        body.setProperty("code", "SYS-500");
        body.setProperty("traceId", traceId);
        return body;
    }
}
