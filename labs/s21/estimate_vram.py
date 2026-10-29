"""GPU 메모리 추정기 — 추정값입니다. 실제 사용량은 엔진·설정에 따라 다릅니다.

사용 예:
  python estimate_vram.py --params 8 --bits 16 --layers 32 --kv-heads 8 --head-dim 128 --ctx 8192 --concurrency 8
  python estimate_vram.py --config ./Qwen3.5-9B/config.json --params 9 --bits 4 --ctx 16384 --concurrency 4
"""
import argparse
import json


def from_config(path: str):
    """Hugging Face config.json 에서 층 수·KV 헤드·헤드 차원을 읽습니다.
    멀티모달 모델은 text_config 아래에 값이 있는 경우가 많습니다."""
    cfg = json.load(open(path, encoding="utf-8"))
    cfg = cfg.get("text_config", cfg)
    layers = cfg["num_hidden_layers"]
    heads = cfg["num_attention_heads"]
    kv_heads = cfg.get("num_key_value_heads", heads)
    head_dim = cfg.get("head_dim") or cfg["hidden_size"] // heads
    return layers, kv_heads, head_dim


def estimate(params_b, bits, layers, kv_heads, head_dim, ctx, concurrency, kv_bytes=2, overhead=0.15):
    weights_gb = params_b * 1e9 * bits / 8 / 1e9
    # K 와 V 두 벌 × 층 × KV 헤드 × 헤드 차원 × 바이트 = 토큰 하나당 KV 캐시
    kv_per_token = 2 * layers * kv_heads * head_dim * kv_bytes
    kv_gb = kv_per_token * ctx * concurrency / 1e9
    total = (weights_gb + kv_gb) * (1 + overhead)
    return weights_gb, kv_per_token, kv_gb, total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--params", type=float, required=True, help="파라미터 수(십억, B)")
    ap.add_argument("--bits", type=float, default=16, help="가중치 비트: 16, 8, 4")
    ap.add_argument("--config", help="config.json 경로(있으면 아래 세 값을 자동으로 읽음)")
    ap.add_argument("--layers", type=int, default=32)
    ap.add_argument("--kv-heads", type=int, default=8)
    ap.add_argument("--head-dim", type=int, default=128)
    ap.add_argument("--ctx", type=int, default=8192, help="요청당 최대 토큰(입력+출력)")
    ap.add_argument("--concurrency", type=int, default=4, help="동시에 처리할 요청 수")
    ap.add_argument("--kv-bytes", type=int, default=2, help="KV 캐시 바이트(FP16=2, FP8=1)")
    ap.add_argument("--gpu-gb", type=float, default=48, help="GPU 한 장의 VRAM(GB)")
    a = ap.parse_args()

    layers, kv_heads, head_dim = (from_config(a.config) if a.config
                                  else (a.layers, a.kv_heads, a.head_dim))
    w, per_tok, kv, total = estimate(a.params, a.bits, layers, kv_heads, head_dim,
                                     a.ctx, a.concurrency, a.kv_bytes)
    print(f"구조: 층 {layers} · KV 헤드 {kv_heads} · 헤드 차원 {head_dim}")
    print(f"가중치        {w:6.1f} GB  ({a.params}B × {a.bits}bit)")
    print(f"KV 캐시/토큰  {per_tok / 1024:6.1f} KiB")
    print(f"KV 캐시 합계  {kv:6.1f} GB  ({a.ctx} 토큰 × 동시 {a.concurrency})")
    print(f"여유분 포함   {total:6.1f} GB  (추정)")
    budget = a.gpu_gb * 0.9
    verdict = "들어감" if total <= budget else "부족 — 비트·컨텍스트·동시 요청을 줄이거나 GPU 추가"
    print(f"GPU {a.gpu_gb:.0f}GB × 0.9 = {budget:.1f} GB → {verdict}")


if __name__ == "__main__":
    main()
