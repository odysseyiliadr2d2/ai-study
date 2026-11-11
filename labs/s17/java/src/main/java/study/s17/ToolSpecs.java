package study.s17;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import java.util.List;
import java.util.Map;

/** 도구 설명서 — 모델이 읽는 유일한 사용 설명서. Python 트랙 tool_specs.py 와 문장까지 같게 둡니다. */
public final class ToolSpecs {

    private ToolSpecs() { }

    public static final List<Tool> TOOLS = List.of(
            tool("get_batch_log",
                    "정보계 야간 배치 작업의 실행 로그를 시간순으로 조회합니다. 읽기 전용입니다. "
                    + "배치가 실패했거나 지연된 이유를 찾을 때 가장 먼저 사용합니다. "
                    + "job_name 은 배치 작업 ID(영문 대문자·숫자·밑줄), run_date 는 처리 기준일자(YYYYMMDD)입니다. "
                    + "'어젯밤 배치'는 보통 어제 날짜가 기준일자입니다. level 로 ERROR 만 골라 볼 수 있습니다. "
                    + "작업 ID를 모르면 아무 ID나 넣지 말고, 결과에 나오는 '이 날짜에 로그가 있는 작업' 목록을 참고하세요.",
                    Map.of(
                            "job_name", Map.of("type", "string", "description", "배치 작업 ID. 예: ADW_ACCT_TXN_DAILY"),
                            "run_date", Map.of("type", "string", "description", "처리 기준일자 YYYYMMDD. 예: 20261028"),
                            "level", Map.of("type", "string", "enum", List.of("ALL", "ERROR", "WARN", "INFO"),
                                    "description", "로그 수준 필터. 기본 ALL"),
                            "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100,
                                    "description", "최대 행 수. 기본 20")),
                    List.of("job_name", "run_date")),
            tool("get_row_count",
                    "DW 테이블의 기준일자별 적재 건수와 직전 기준일 건수를 함께 돌려줍니다. 읽기 전용입니다. "
                    + "적재가 됐는지, 평소보다 건수가 크게 줄거나 늘었는지 확인할 때 씁니다. "
                    + "table_name 은 반드시 '스키마.테이블' 형식(예: STG.STG_ACCT_TXN)입니다. "
                    + "테이블명이 확실하지 않으면 먼저 search_table_spec 으로 확인하세요.",
                    Map.of(
                            "table_name", Map.of("type", "string", "description", "스키마.테이블. 예: ADW.ADW_ACCT_TXN_D"),
                            "base_date", Map.of("type", "string", "description", "기준일자 YYYYMMDD")),
                    List.of("table_name", "base_date")),
            tool("search_table_spec",
                    "테이블 정의서를 키워드로 검색해 테이블 설명, 컬럼 타입·길이, 적재 작업, 원천, 비고를 돌려줍니다. 읽기 전용입니다. "
                    + "ORA-12899(값이 컬럼보다 큼) 같은 오류에서 컬럼 정의를 확인하거나, 테이블명·작업 관계를 찾을 때 씁니다. "
                    + "keyword 에는 테이블명, 컬럼명, 한글 업무 용어 중 하나를 넣습니다(2자 이상). 결과는 최대 5개입니다.",
                    Map.of("keyword", Map.of("type", "string", "description", "예: STG_ACCT_TXN, BRNCH_CD, 지점코드")),
                    List.of("keyword")),
            tool("request_rerun",
                    "배치 재실행 요청을 운영 요청함에 등록합니다. 쓰기 작업이며 배치를 직접 실행하지는 않습니다. "
                    + "원인을 확인하고 조치 방안이 정해진 뒤에만 호출합니다. 호출하면 사람이 승인해야 실제로 등록됩니다. "
                    + "from_step 은 다시 시작할 블록(예: STG_LOAD), reason 에는 원인과 선행 조치를 한두 문장으로 씁니다. "
                    + "선행 조치(예: 컬럼 길이 변경)가 끝나지 않았다면 이 도구를 호출하지 말고 보고서에 조치 필요 사항으로 적으세요.",
                    Map.of(
                            "job_name", Map.of("type", "string"),
                            "run_date", Map.of("type", "string", "description", "YYYYMMDD"),
                            "from_step", Map.of("type", "string", "description", "재시작 블록. 예: STG_LOAD"),
                            "reason", Map.of("type", "string", "description", "원인과 선행 조치 요약")),
                    List.of("job_name", "run_date", "from_step", "reason")));

    /** 이름 · 설명 · JSON Schema(properties + required)로 Tool 하나를 만듭니다. type 은 기본값 "object". */
    private static Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(JsonValue.from(properties))
                        .required(required)
                        .build())
                .build();
    }
}
