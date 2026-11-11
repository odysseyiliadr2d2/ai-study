// src/main/java/com/example/ticketdesk/request/WorkRequestPageController.java
// 화면용 컨트롤러는 HTML 만 돌려줍니다. 등록은 화면의 JavaScript 가 REST API 로 보냅니다.
package com.example.ticketdesk.request;

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
    public String list(Model model) {
        model.addAttribute("items", service.list(null));
        return "requests";   // src/main/resources/templates/requests.html
    }
}
