// src/main/java/com/example/ticketdesk/request/WorkRequest.java
// 업무 요청 도메인. 세션 15에서 이 클래스에 JPA 어노테이션을 붙여 Oracle 테이블과 연결합니다.
package com.example.ticketdesk.request;

import java.time.Instant;
import java.time.LocalDate;

public class WorkRequest {

    public enum Status { RECEIVED, IN_PROGRESS, DONE }
    public enum Category { DATA_EXTRACT, BATCH_ISSUE, ACCESS, REPORT, ETC }
    public enum Priority { LOW, MEDIUM, HIGH }

    private Long id;
    private String title;
    private String body;
    private LocalDate dueDate;
    private Status status = Status.RECEIVED;
    private Category category;     // 세션 15에서 AI 가 채움
    private Priority priority;
    private String summary;
    private String requesterId;
    private Instant createdAt = Instant.now();

    protected WorkRequest() { }

    public WorkRequest(String title, String body, LocalDate dueDate, String requesterId) {
        this.title = title;
        this.body = body;
        this.dueDate = dueDate;
        this.requesterId = requesterId;
    }

    public void changeStatus(Status next) { this.status = next; }

    public void applyAnalysis(String summary, Category category, Priority priority) {
        this.summary = summary;
        this.category = category;
        this.priority = priority;
    }

    void assignId(Long id) { this.id = id; }   // 메모리 저장소 전용 (세션 15에서 삭제)

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public LocalDate getDueDate() { return dueDate; }
    public Status getStatus() { return status; }
    public Category getCategory() { return category; }
    public Priority getPriority() { return priority; }
    public String getSummary() { return summary; }
    public String getRequesterId() { return requesterId; }
    public Instant getCreatedAt() { return createdAt; }
}
