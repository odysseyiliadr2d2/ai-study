package study.s21;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * GPU 메모리 추정기 — 추정값입니다. 실제 사용량은 엔진·설정에 따라 다릅니다. (Python 트랙 estimate_vram.py 와 같은 식)
 *
 *   ./mvnw -q compile exec:java -Dexec.mainClass=study.s21.VramEstimator \
 *     -Dexec.args="--params 8 --bits 16 --ctx 8192 --concurrency 8 --gpu-gb 48"
 *   ... -Dexec.args="--config ./models/Qwen3.5-9B/config.json --params 9 --bits 4 --ctx 16384 --concurrency 4"
 */
public final class VramEstimator {

    record Arch(int layers, int kvHeads, int headDim) {}

    record Estimate(double weightsGb, long kvPerToken, double kvGb, double totalGb) {}

    /** Hugging Face config.json 에서 층 수 · KV 헤드 · 헤드 차원을 읽습니다. 멀티모달 모델은 text_config 아래에 있습니다. */
    static Arch fromConfig(Path path) throws IOException {
        JsonNode cfg = new ObjectMapper().readTree(path.toFile());
        if (cfg.has("text_config")) cfg = cfg.get("text_config");
        int layers = cfg.get("num_hidden_layers").asInt();
        int heads = cfg.get("num_attention_heads").asInt();
        int kvHeads = cfg.path("num_key_value_heads").asInt(heads);
        int headDim = cfg.hasNonNull("head_dim") ? cfg.get("head_dim").asInt() : cfg.get("hidden_size").asInt() / heads;
        return new Arch(layers, kvHeads, headDim);
    }

    static Estimate estimate(double paramsB, double bits, Arch a, int ctx, int concurrency, int kvBytes, double overhead) {
        double weightsGb = paramsB * 1e9 * bits / 8 / 1e9;
        // K 와 V 두 벌 × 층 × KV 헤드 × 헤드 차원 × 바이트 = 토큰 하나당 KV 캐시
        long kvPerToken = 2L * a.layers() * a.kvHeads() * a.headDim() * kvBytes;
        double kvGb = (double) kvPerToken * ctx * concurrency / 1e9;
        return new Estimate(weightsGb, kvPerToken, kvGb, (weightsGb + kvGb) * (1 + overhead));
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> o = new HashMap<>(Map.of("--bits", "16", "--layers", "32", "--kv-heads", "8", "--head-dim", "128",
                "--ctx", "8192", "--concurrency", "4", "--kv-bytes", "2", "--gpu-gb", "48"));
        for (int i = 0; i + 1 < args.length; i += 2) o.put(args[i], args[i + 1]);
        if (!o.containsKey("--params")) {
            System.err.println("--params(십억, B)는 필수입니다. 예: --params 8");
            System.exit(2);
        }
        Arch arch = o.containsKey("--config") ? fromConfig(Path.of(o.get("--config")))
                : new Arch(Integer.parseInt(o.get("--layers")), Integer.parseInt(o.get("--kv-heads")), Integer.parseInt(o.get("--head-dim")));
        double params = Double.parseDouble(o.get("--params"));
        double bits = Double.parseDouble(o.get("--bits"));
        int ctx = Integer.parseInt(o.get("--ctx"));
        int conc = Integer.parseInt(o.get("--concurrency"));
        Estimate e = estimate(params, bits, arch, ctx, conc, Integer.parseInt(o.get("--kv-bytes")), 0.15);

        System.out.printf("구조: 층 %d · KV 헤드 %d · 헤드 차원 %d%n", arch.layers(), arch.kvHeads(), arch.headDim());
        System.out.printf("가중치        %6.1f GB  (%sB × %sbit)%n", e.weightsGb(), o.get("--params"), o.get("--bits"));
        System.out.printf("KV 캐시/토큰  %6.1f KiB%n", e.kvPerToken() / 1024.0);
        System.out.printf("KV 캐시 합계  %6.1f GB  (%d 토큰 × 동시 %d)%n", e.kvGb(), ctx, conc);
        System.out.printf("여유분 포함   %6.1f GB  (추정)%n", e.totalGb());
        double gpu = Double.parseDouble(o.get("--gpu-gb"));
        double budget = gpu * 0.9;
        System.out.printf("GPU %.0fGB × 0.9 = %.1f GB → %s%n", gpu, budget,
                e.totalGb() <= budget ? "들어감" : "부족 — 비트·컨텍스트·동시 요청을 줄이거나 GPU 추가");
    }
}
