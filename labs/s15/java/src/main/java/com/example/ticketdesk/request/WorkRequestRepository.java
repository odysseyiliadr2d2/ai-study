// src/main/java/com/example/ticketdesk/request/WorkRequestRepository.java
// 메서드 이름이 곧 쿼리입니다. Top100 은 Oracle 에서 FETCH FIRST 100 ROWS ONLY 로 바뀝니다.
package com.example.ticketdesk.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, Long> {

    List<WorkRequest> findTop100ByOrderByCreatedAtDesc();

    List<WorkRequest> findTop100ByStatusOrderByCreatedAtDesc(WorkRequest.Status status);

    List<WorkRequest> findTop100ByRequesterIdOrderByCreatedAtDesc(String requesterId);

    List<WorkRequest> findTop100ByRequesterIdAndStatusOrderByCreatedAtDesc(
            String requesterId, WorkRequest.Status status);
}
