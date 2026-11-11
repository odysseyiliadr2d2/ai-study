package study.s06;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/**
 * 문의 메일에서 뽑을 필드 — 이 record 가 곧 JSON Schema 입니다.
 * SDK 가 record 구조에서 스키마를 만들어 output_config.format 으로 보냅니다.
 *  - 모든 필드가 required 가 되고, Optional 필드는 "type": ["string", "null"] 이 됩니다.
 *  - @Schema(allowableValues = ...) 는 enum 이 되어 분류를 닫아 둡니다 (한글 값을 그대로 쓰려고 Java enum 대신 사용).
 *  - @JsonPropertyDescription 의 설명이 스키마에 들어가 모델에게 지시 역할을 합니다.
 *  - @JsonProperty 로 JSON 이름을 Python 트랙과 같은 snake_case 로 맞춥니다.
 */
public record Inquiry(
        @JsonPropertyDescription("문의 분류")
        @Schema(allowableValues = {"계정·로그인", "요금·계약", "장애·오류", "기능 요청", "데이터·보고서", "기타"})
        String category,

        @JsonPropertyDescription("업무 중단이면 높음, 불편이면 보통, 문의만이면 낮음")
        @Schema(allowableValues = {"높음", "보통", "낮음"})
        String urgency,

        @JsonPropertyDescription("한 문장 요약, 80자 이내")
        String summary,

        @JsonProperty("system_name")
        @JsonPropertyDescription("언급된 시스템·화면 이름. 없으면 null")
        Optional<String> systemName,

        @JsonProperty("error_code")
        @JsonPropertyDescription("본문에 있는 오류 코드 그대로. 없으면 null")
        Optional<String> errorCode,

        @JsonProperty("requested_by_date")
        @JsonPropertyDescription("요청 기한 YYYY-MM-DD. 없으면 null")
        Optional<String> requestedByDate,

        @JsonPropertyDescription("분류 확신도 0.0~1.0")
        double confidence) {
}
