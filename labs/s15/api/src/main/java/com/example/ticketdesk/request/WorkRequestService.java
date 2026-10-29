// src/main/java/com/example/ticketdesk/request/WorkRequestService.java
// 세션 14의 메모리 저장소를 JPA 로 바꾸고, 마스킹 → AI 분석 → 저장 → 알림 정책을 한곳에 둡니다.
package com.example.ticketdesk.request;

import com.example.ticketdesk.ai.RequestAnalyzer;
import com.example.ticketdesk.request.WorkRequestDtos.CreateRequest;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WorkRequestService {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NotFound extends RuntimeException {
        public NotFound(Long id) { super("요청을 찾을 수 없습니다: " + id); }
    }

    /** n8n 으로 보내는 알림 내용 — 본문 전체는 보내지 않습니다 */
    public record Notice(Long id, String title, WorkRequest.Category category,
                  WorkRequest.Priority priority, String summary) { }

    private static final Logger log = LoggerFactory.getLogger(WorkRequestService.class);

    private final WorkRequestRepository repo;
    private final RequestAnalyzer analyzer;
    private final RestClient http;
    private final String webhookUrl;
    private final String webhookSecret;

    public WorkRequestService(WorkRequestRepository repo, RequestAnalyzer analyzer,
                              @Value("${app.n8n-webhook-url:}") String webhookUrl,
                              @Value("${app.n8n-webhook-secret:}") String webhookSecret) {
        this.repo = repo;
        this.analyzer = analyzer;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** 등록: LLM 호출은 트랜잭션 밖에서 (DB 커넥션을 수 초씩 잡고 있지 않게) */
    public WorkRequest create(CreateRequest in, String requesterId) {
        String title = in.title().strip();
        String body = in.body().strip();
        WorkRequest w = new WorkRequest(title, body, in.dueDate(), requesterId);

        analyzer.analyze(mask(title), mask(body))
                .ifPresent(a -> w.applyAnalysis(a.summary(), a.category(), a.priority()));
        log.info("요청 등록 requester={} analyzed={} category={}",
                requesterId, w.getCategory() != null, w.getCategory());

        WorkRequest saved = repo.save(w);
        notifyN8n(saved);
        return saved;
    }

    /** 목록: 담당자는 전체, 일반 사용자는 본인 것만 */
    @Transactional(readOnly = true)
    public List<WorkRequest> list(WorkRequest.Status status, Authentication auth) {
        if (isHandler(auth)) {
            return status == null ? repo.findTop100ByOrderByCreatedAtDesc()
                                  : repo.findTop100ByStatusOrderByCreatedAtDesc(status);
        }
        return status == null ? repo.findTop100ByRequesterIdOrderByCreatedAtDesc(auth.getName())
                              : repo.findTop100ByRequesterIdAndStatusOrderByCreatedAtDesc(auth.getName(), status);
    }

    /** 상세: 남의 요청은 '없음(404)'으로 — 존재 여부도 알려 주지 않습니다 */
    @Transactional(readOnly = true)
    public WorkRequest get(Long id, Authentication auth) {
        return repo.findById(id)
                .filter(w -> isHandler(auth) || w.getRequesterId().equals(auth.getName()))
                .orElseThrow(() -> new NotFound(id));
    }

    @Transactional
    public WorkRequest changeStatus(Long id, WorkRequest.Status next, Authentication auth) {
        WorkRequest w = get(id, auth);
        w.changeStatus(next);          // 변경 감지로 UPDATE
        return w;
    }

    static boolean isHandler(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_HANDLER".equals(a.getAuthority()));
    }

    /** LLM 으로 보내기 전 민감정보 형태 제거 (순서 중요: 전화번호 → 계좌번호) */
    static String mask(String text) {
        return text
                .replaceAll("\\b\\d{6}-?[1-4]\\d{6}\\b", "[주민번호]")
                .replaceAll("\\b01[016789]-?\\d{3,4}-?\\d{4}\\b", "[전화번호]")
                .replaceAll("\\b\\d{2,6}-\\d{2,6}-\\d{2,8}\\b", "[계좌번호]");
    }

    private void notifyN8n(WorkRequest w) {
        if (webhookUrl.isBlank()) return;
        try {
            http.post()
                .uri(webhookUrl)
                .header("X-Webhook-Secret", webhookSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Notice(w.getId(), w.getTitle(), w.getCategory(), w.getPriority(), w.getSummary()))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("n8n 알림 실패 id={}: {}", w.getId(), e.getMessage());   // 등록은 이미 끝남
        }
    }
}
