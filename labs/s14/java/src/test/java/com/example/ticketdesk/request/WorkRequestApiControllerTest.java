package com.example.ticketdesk.request;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** API 규격(이론 1절) 회귀 시험 — ./mvnw -q test. 컨트롤러 · 오류 처리기만 띄우는 슬라이스 테스트입니다. */
@WebMvcTest(WorkRequestApiController.class)
@Import(WorkRequestService.class)          // 메모리 저장소 서비스는 실제 객체로
class WorkRequestApiControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 등록하면_201과_Location_헤더() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "월말 잔고 추출 요청", "body": "11월 말 기준 상품별 잔고를 엑셀로 받고 싶습니다"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/requests/")))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void 짧은_제목은_400과_필드별_메시지() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "짧"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQ-400-VALIDATION"))
                .andExpect(jsonPath("$.errors.title[0]").value("제목은 5~100자로 입력하세요"))
                .andExpect(jsonPath("$.errors.body[0]").value("내용을 입력하세요"));
    }

    @Test
    void 잘못된_상태는_400_없는_id는_404() throws Exception {
        mvc.perform(get("/api/requests").param("status", "WRONG")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/requests/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQ-404"));
    }
}
