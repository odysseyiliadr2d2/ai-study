package com.example.ticketdesk.request;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkRequestPageController {

    private final WorkRequestService service;

    public WorkRequestPageController(WorkRequestService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public String list(Model model, Authentication auth) {
        model.addAttribute("items", service.list(null, auth));
        return "requests";
    }
}
