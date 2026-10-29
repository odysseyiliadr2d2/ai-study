// src/main/java/com/example/ticketdesk/request/WorkRequest.java
// 세션 14의 도메인 클래스에 JPA 어노테이션을 붙였습니다. assignId() 는 지웠습니다.
package com.example.ticketdesk.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "WORK_REQUEST")
public class WorkRequest {

    public enum Status { RECEIVED, IN_PROGRESS, DONE }
    public enum Category { DATA_EXTRACT, BATCH_ISSUE, ACCESS, REPORT, ETC }
    public enum Priority { LOW, MEDIUM, HIGH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // Oracle 12c+ IDENTITY 컬럼
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Lob
    @Column(nullable = false)
    private String body;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)                           // 숫자(ORDINAL) 말고 이름으로 저장
    @Column(nullable = false, length = 20)
    private Status status = Status.RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false, length = 50, updatable = false)
    private String requesterId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WorkRequest() { }   // JPA 용

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
