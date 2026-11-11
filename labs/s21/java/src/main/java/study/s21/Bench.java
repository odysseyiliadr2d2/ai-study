package study.s21;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * 간단 처리량·지연 벤치마크 — 동시 요청 수를 바꿔 TTFT(첫 토큰까지 시간)와 출력 처리량을 잽니다.
 *
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s21.Bench -Dexec.args="--concurrency 4 --requests 20"
 *
 * 요청마다 가상 스레드 하나, 동시 실행 수는 Semaphore 로 제한합니다. 조각 수를 출력 토큰의 근사값으로 씁니다.
 * 정밀 측정은 vLLM 의 vllm bench serve 를 씁니다.
 */
public final class Bench {

    record Sample(double ttft, double total, int chunks) {}

    static final String PROMPT = "다음 장애 보고를 3줄로 요약하세요.\n"
            + "02:10 원장 일배치 지연. 원천 파일 수신이 40분 늦어 STG 적재가 대기했고, "
            + "03:05 수신 확인 후 재처리하여 04:20 정상 종료. 영향: 아침 보고서 20분 지연.";

    public static void main(String[] args) throws Exception {
        Map<String, String> o = new HashMap<>(Map.of("--concurrency", "4", "--requests", "20"));
        for (int i = 0; i + 1 < args.length; i += 2) o.put(args[i], args[i + 1]);
        int concurrency = Integer.parseInt(o.get("--concurrency"));
        int requests = Integer.parseInt(o.get("--requests"));

        LlmClient client = new LlmClient();
        Semaphore sem = new Semaphore(concurrency);
        long t0 = System.nanoTime();
        List<Future<Sample>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    sem.acquire();
                    try {
                        long start = System.nanoTime();
                        long[] first = {0};
                        ObjectNode body = LlmClient.body(LlmClient.MODEL, null, PROMPT, 256, 0, true);
                        int chunks = client.stream(body, t -> {
                            if (first[0] == 0) first[0] = System.nanoTime();
                        });
                        double ttft = first[0] == 0 ? 0 : (first[0] - start) / 1e9;
                        return new Sample(ttft, (System.nanoTime() - start) / 1e9, chunks);
                    } finally {
                        sem.release();
                    }
                }));
            }
        }   // close() 가 모든 요청이 끝날 때까지 기다립니다
        double wall = (System.nanoTime() - t0) / 1e9;

        List<Double> ttft = new ArrayList<>();
        double sumTotal = 0;
        long tokens = 0;
        for (Future<Sample> f : futures) {
            Sample s = f.get();
            ttft.add(s.ttft());
            sumTotal += s.total();
            tokens += s.chunks();
        }
        Collections.sort(ttft);
        int n = ttft.size();
        double median = n % 2 == 1 ? ttft.get(n / 2) : (ttft.get(n / 2 - 1) + ttft.get(n / 2)) / 2;
        double p95 = ttft.size() > 1 ? ttft.get((int) (ttft.size() * 0.95) - 1) : ttft.get(0);   // Python 트랙과 같은 계산
        System.out.printf("동시 %d · 요청 %d건 · 총 %.1f초%n", concurrency, requests, wall);
        System.out.printf("TTFT 중앙값 %.2fs · p95 %.2fs%n", median, p95);
        System.out.printf("요청당 평균 %.2fs%n", sumTotal / requests);
        System.out.printf("출력 처리량 약 %.0f 토큰/초 (조각 기준 근사)%n", tokens / wall);
    }
}
