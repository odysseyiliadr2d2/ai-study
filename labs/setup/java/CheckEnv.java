// 실습 환경 점검 (Java 트랙) — 의존성 없이 단일 파일로 실행합니다.
//   java labs/setup/java/CheckEnv.java
// JDK 버전, JAVA_HOME, Maven Wrapper, API 키 설정 여부를 보여 줍니다. 네트워크 호출은 하지 않습니다.
import java.nio.file.*;
import java.util.*;

public class CheckEnv {
    static void row(String name, String value, boolean need) {
        String mark = value != null ? "OK " : (need ? "없음" : "-- ");
        System.out.printf("[%s] %-22s %s%n", mark, name, value != null ? value : (need ? "설치 필요" : "선택"));
    }

    static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            p.waitFor();
            return out.isEmpty() ? null : out.lines().findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        int feature = Runtime.version().feature();
        System.out.println("OS: " + System.getProperty("os.name") + " / JDK " + Runtime.version()
                + " (" + System.getProperty("java.vendor") + ")");
        row("JDK 21 이상", feature >= 21 ? String.valueOf(Runtime.version()) : null, true);
        row("JAVA_HOME", System.getenv("JAVA_HOME"), false);
        Path wrapper = Path.of(win ? "mvnw.cmd" : "mvnw");
        row("Maven Wrapper(현재 폴더)", Files.exists(wrapper) ? wrapper.toAbsolutePath().toString() : null, false);
        row("Maven(전역)", run(win ? "mvn.cmd" : "mvn", "-v"), false);
        row("Git", run("git", "--version"), true);
        row("Python", run(win ? "python" : "python3", "--version"), false);
        row("Docker", run("docker", "--version"), false);
        for (String key : List.of("ANTHROPIC_API_KEY", "OPENAI_API_KEY")) {
            String v = System.getenv(key);
            row(key, v != null && !v.isBlank() ? "설정됨 (" + v.substring(0, Math.min(7, v.length())) + "…)" : null,
                    key.equals("ANTHROPIC_API_KEY"));
        }
    }
}
