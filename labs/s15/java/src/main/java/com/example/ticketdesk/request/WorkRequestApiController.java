package com.example.ticketdesk.request;

import com.example.ticketdesk.request.WorkRequestDtos.CreateRequest;
import com.example.ticketdesk.request.WorkRequestDtos.Response;
import com.example.ticketdesk.request.WorkRequestDtos.StatusChange;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @GetMapping
    public List<Response> list(@RequestParam(required = false) WorkRequest.Status status, Authentication auth) {
        return service.list(status, auth).stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id, Authentication auth) {
        return Response.from(service.get(id, auth));
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest body, Authentication auth) {
        WorkRequest created = service.create(body, auth.getName());
        return ResponseEntity.created(URI.create("/api/requests/" + created.getId()))
                .body(Response.from(created));
    }

    @PatchMapping("/{id}")
    public Response changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChange body, Authentication auth) {
        return Response.from(service.changeStatus(id, body.status(), auth));
    }
}
