// src/main/java/com/example/ticketdesk/request/WorkRequestDtos.java
// API 입출력 DTO 를 record 로 모아 둡니다. 도메인 객체를 그대로 내보내지 않습니다.
package com.example.ticketdesk.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class WorkRequestDtos {

    private WorkRequestDtos() { }

    /** 등록 입력 — 기본 트랙의 zod 스키마와 같은 규칙 */
    public record CreateRequest(
            @NotBlank(message = "제목을 입력하세요")
            @Size(min = 5, max = 100, message = "제목은 5~100자로 입력하세요")
            String title,

            @NotBlank(message = "내용을 입력하세요")
            @Size(min = 10, max = 4000, message = "내용은 10~4000자로 입력하세요")
            String body,

            @FutureOrPresent(message = "마감일은 오늘 이후여야 합니다")
            LocalDate dueDate) { }

    /** 상태 변경 입력 */
    public record StatusChange(@NotNull(message = "상태를 선택하세요") WorkRequest.Status status) { }

    /** 응답 — 필요한 필드만 골라 내보냅니다 */
    public record Response(
            Long id, String title, String body, LocalDate dueDate,
            WorkRequest.Status status, WorkRequest.Category category,
            WorkRequest.Priority priority, String summary,
            String requesterId, Instant createdAt) {

        public static Response from(WorkRequest w) {
            return new Response(w.getId(), w.getTitle(), w.getBody(), w.getDueDate(),
                    w.getStatus(), w.getCategory(), w.getPriority(), w.getSummary(),
                    w.getRequesterId(), w.getCreatedAt());
        }
    }
}
