// src/main/java/com/example/ticketdesk/request/WorkRequestService.java
// 업무 규칙을 모으는 서비스 계층. 오늘은 메모리 Map, 세션 15에서 JPA 저장소로 바꿉니다.
package com.example.ticketdesk.request;

import com.example.ticketdesk.request.WorkRequestDtos.CreateRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

@Service
public class WorkRequestService {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFound extends RuntimeException {
        public NotFound(Long id) { super("요청을 찾을 수 없습니다: " + id); }
    }

    private final Map<Long, WorkRequest> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public WorkRequest create(CreateRequest in, String requesterId) {
        WorkRequest w = new WorkRequest(in.title().strip(), in.body().strip(), in.dueDate(), requesterId);
        w.assignId(seq.incrementAndGet());
        store.put(w.getId(), w);
        return w;
    }

    public List<WorkRequest> list(WorkRequest.Status status) {
        return store.values().stream()
                .filter(w -> status == null || w.getStatus() == status)
                .sorted(Comparator.comparing(WorkRequest::getCreatedAt).reversed())
                .toList();
    }

    public WorkRequest get(Long id) {
        WorkRequest w = store.get(id);
        if (w == null) throw new NotFound(id);
        return w;
    }

    public WorkRequest changeStatus(Long id, WorkRequest.Status next) {
        WorkRequest w = get(id);
        w.changeStatus(next);
        return w;
    }
}
