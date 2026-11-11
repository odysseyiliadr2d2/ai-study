package study.s03;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 나쁜 프롬프트 vs 고친 프롬프트를 나란히 비교합니다.
 * 같은 로그를 두 프롬프트로 3번씩 보내 결과 길이와 모양이 얼마나 흔들리는지 봅니다.
 * ./mvnw -q compile exec:java -Dexec.mainClass=study.s03.Compare
 */
public class Compare {
    static final int RUNS = 3;

    /** 결과의 모양을 숫자로: 글자 수, 줄 수, 표 줄 수, 'ORA-01653' 언급 여부 */
    static String shape(String text) {
        List<String> lines = text.lines().toList();
        long table = lines.stream().filter(l -> l.strip().startsWith("|")).count();
        return String.format("%5d자 %3d줄 표%2d줄 ORA코드=%s",
                text.length(), lines.size(), table, text.contains("ORA-01653") ? "O" : "X");
    }

    public static void main(String[] args) throws Exception {
        String log = Files.readString(Path.of(System.getProperty("log", "../sample_log.txt")));   // Python 트랙과 같은 파일

        Templates.Rendered bad = new Templates.Rendered("", "이 로그 요약해줘\n" + log);
        Templates.Rendered good = Templates.render("log_summary", Map.of("base_date", "2026-10-28", "log", log));

        for (var pair : List.of(Map.entry("나쁜 프롬프트", bad), Map.entry("고친 프롬프트", good))) {
            System.out.println("\n========== " + pair.getKey() + " ==========");
            List<String> outputs = new ArrayList<>();
            for (int i = 1; i <= RUNS; i++) {
                String out = Claude.ask(pair.getValue().system(), pair.getValue().user(), 1500).text();
                outputs.add(out);
                System.out.printf("[%d회] %s%n", i, shape(out));
            }
            System.out.println("----- 1회차 원문 -----");
            System.out.println(outputs.get(0));
        }
    }
}
