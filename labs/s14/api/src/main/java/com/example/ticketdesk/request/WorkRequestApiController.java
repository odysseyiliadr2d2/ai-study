// src/main/java/com/example/ticketdesk/request/WorkRequestApiController.java
package com.example.ticketdesk.request;

import com.example.ticketdesk.request.WorkRequestDtos.CreateRequest;
import com.example.ticketdesk.request.WorkRequestDtos.Response;
import com.example.ticketdesk.request.WorkRequestDtos.StatusChange;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class WorkRequestApiController {

    private final WorkRequestService service;

    public WorkRequestApiController(WorkRequestService service) {
        this.service = service;
    }

    /** GET /api/requests?status=RECEIVED — 잘못된 status 값은 스프링이 400 으로 돌려줍니다 */
    @GetMapping
    public List<Response> list(@RequestParam(required = false) WorkRequest.Status status) {
        return service.list(status).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return Response.from(service.get(id));
    }

    /** POST /api/requests — @Valid 가 실패하면 컨트롤러에 들어오기 전에 400 */
    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest body) {
        WorkRequest created = service.create(body, "demo-user");   // 세션 15에서 로그인 사용자로
        return ResponseEntity.created(URI.create("/api/requests/" + created.getId()))
                .body(Response.from(created));
    }

    @PatchMapping("/{id}")
    public Response changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange body) {
        return Response.from(service.changeStatus(id, body.status()));
    }
}
