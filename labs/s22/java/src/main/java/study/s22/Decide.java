package study.s22;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 파인튜닝 vs RAG 판단 체크리스트 — Python 트랙 decide.py 와 같은 규칙, 같은 answers.yaml.
 *   ./mvnw -q compile exec:java -Dexec.args=answers.yaml
 */
public final class Decide {

    static List<String> decide(Map<String, Object> a) {
        List<String> out = new ArrayList<>();
        if (!bool(a, "prompt_tried")) {
            return List.of("먼저 프롬프트(규칙 + 예시 3~5개)로 평가셋을 돌려 보세요. 대부분 여기서 해결됩니다.");
        }
        if (num(a, "prompt_score", 0) >= num(a, "target_score", 0.9)) {
            return List.of("프롬프트로 목표를 넘었습니다. 추가 작업 없이 운영하고 평가셋만 주기적으로 다시 돌립니다.");
        }
        if (bool(a, "needs_new_facts") || bool(a, "facts_change_often") || bool(a, "needs_citation")) {
            out.add("RAG: 모르는 사실·자주 바뀌는 문서·출처 요구는 검색으로 해결합니다 (세션 9~10).");
        }
        if (bool(a, "format_or_tone_issue")) {
            int n = (int) num(a, "labeled_examples", 0);
            if (n < 200) {
                out.add("파인튜닝 보류: 검수된 예시가 " + n + "건뿐입니다. 200건 이상(권장 500~1,000건) 모을 때까지 "
                        + "RAG 로 비슷한 예시를 찾아 프롬프트에 붙이는 방식(동적 few-shot)을 씁니다.");
            } else if (!bool(a, "has_gpu")) {
                out.add("파인튜닝 후보: 데이터는 충분하지만 GPU 가 없습니다. 학습용 GPU 확보 계획부터 세웁니다.");
            } else {
                out.add("LoRA 파인튜닝: 형식·말투 문제이고 예시 " + n + "건이 있으므로 작은 LoRA 로 시도합니다.");
            }
        }
        if (out.isEmpty()) {
            out.add("원인 분석 필요: 실패 사례 20건을 직접 읽고 '사실 부족'인지 '형식 문제'인지 분류해 보세요.");
        }
        return out;
    }

    static boolean bool(Map<String, Object> a, String k) {
        return Boolean.TRUE.equals(a.get(k));
    }

    static double num(Map<String, Object> a, String k, double def) {
        return a.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "answers.yaml");
        Map<String, Object> answers;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            answers = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
        }
        System.out.println("[" + answers.getOrDefault("name", "유스케이스") + "]");
        decide(answers).forEach(line -> System.out.println("- " + line));
    }
}
