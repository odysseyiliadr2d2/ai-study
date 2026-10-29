"""간단 처리량·지연 벤치마크 — python bench.py --concurrency 4 --requests 20

각 요청을 스트리밍으로 받아 TTFT(첫 토큰까지 시간)와 전체 시간을 재고,
청크 수를 출력 토큰의 근사값으로 씁니다(정밀 측정은 vllm bench serve 사용).
"""
import argparse
import asyncio
import os
import statistics
import time

from openai import AsyncOpenAI

client = AsyncOpenAI(base_url=os.getenv("LLM_BASE_URL", "http://localhost:8000/v1"),
                     api_key=os.getenv("LLM_API_KEY", "change-me"))
MODEL = os.getenv("LLM_MODEL", "qwen3.5-9b")

PROMPT = ("다음 장애 보고를 3줄로 요약하세요.\n"
          "02:10 원장 일배치 지연. 원천 파일 수신이 40분 늦어 STG 적재가 대기했고, "
          "03:05 수신 확인 후 재처리하여 04:20 정상 종료. 영향: 아침 보고서 20분 지연.")


async def one(sem: asyncio.Semaphore):
    async with sem:
        start = time.perf_counter()
        first, chunks = None, 0
        stream = await client.chat.completions.create(
            model=MODEL, stream=True, max_tokens=256, temperature=0,
            messages=[{"role": "user", "content": PROMPT}])
        async for ev in stream:
            if ev.choices and ev.choices[0].delta.content:
                if first is None:
                    first = time.perf_counter() - start
                chunks += 1
        return first or 0.0, time.perf_counter() - start, chunks


async def main(concurrency: int, requests: int):
    sem = asyncio.Semaphore(concurrency)
    t0 = time.perf_counter()
    results = await asyncio.gather(*[one(sem) for _ in range(requests)])
    wall = time.perf_counter() - t0
    ttft = sorted(r[0] for r in results)
    total = [r[1] for r in results]
    toks = sum(r[2] for r in results)
    p95 = ttft[int(len(ttft) * 0.95) - 1] if len(ttft) > 1 else ttft[0]
    print(f"동시 {concurrency} · 요청 {requests}건 · 총 {wall:.1f}초")
    print(f"TTFT 중앙값 {statistics.median(ttft):.2f}s · p95 {p95:.2f}s")
    print(f"요청당 평균 {statistics.mean(total):.2f}s")
    print(f"출력 처리량 약 {toks / wall:.0f} 토큰/초 (청크 기준 근사)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--concurrency", type=int, default=4)
    ap.add_argument("--requests", type=int, default=20)
    a = ap.parse_args()
    asyncio.run(main(a.concurrency, a.requests))
