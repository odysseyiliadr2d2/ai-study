#!/usr/bin/env python3
"""ai-study 도식 생성기.

tools/diagrams/<name>.json 사양을 읽어 diagrams/<name>.svg 를 만듭니다.
ByteByteGo 풍(색 블록, 번호 배지, 점선 화살표, 작은 라인 아이콘)으로 통일합니다.

사용법:
    python3 tools/diagram.py                 # tools/diagrams/*.json 전부
    python3 tools/diagram.py s05-concept     # 하나만

사양(JSON) 형식 — README의 '도식 사양' 절 참고:
{
  "title": "제목(요점 한 문장)",
  "subtitle": "부제(선택)",
  "cols": 4, "rows": 2,            # 격자 크기
  "cell_h": 132,                   # 행 높이(선택, 기본 132)
  "nodes": [
    {"id": "a", "col": 0, "row": 0, "label": "이름", "sub": "설명(선택)",
     "icon": "user", "color": "blue", "badge": 1,
     "span": 1, "rspan": 1, "emph": false}
  ],
  "groups": [
    {"label": "묶음 이름", "col": 0, "row": 0, "cols": 2, "rows": 1, "color": "pink"}
  ],
  "edges": [
    {"from": "a", "to": "b", "label": "선택", "dashed": true, "both": false,
     "route": "hv"}               # 대각선 연결: hv=가로 먼저(기본), vh=세로 먼저
  ],
  "notes": ["그림 아래 한 줄 메모(선택)"]
}
"""
import json
import sys
import unicodedata
import xml.dom.minidom
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC_DIR = ROOT / "tools" / "diagrams"
OUT_DIR = ROOT / "diagrams"

WIDTH = 960
MARGIN = 32
TOP = 96          # 격자 시작 y (제목 영역 아래)
PAD_X = 18        # 셀 안 좌우 여백 (박스 사이 화살표 길이 = 2 × PAD_X)
PAD_Y = 20        # 셀 안 상하 여백

INK = "#1F1F1F"
MUTED = "#5C5A57"
LINE = "#DEDBD6"
EDGE = "#8A8680"
ACCENT = "#F26B1D"
FONT = "Pretendard, -apple-system, 'Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans CJK KR', sans-serif"

PALETTE = {
    "blue":   ("#2F6FDE", "#EAF2FF"),
    "orange": ("#E8661C", "#FFF1E7"),
    "green":  ("#13976A", "#E7F6EF"),
    "purple": ("#7656D6", "#F1EDFC"),
    "pink":   ("#D2457C", "#FCEBF2"),
    "teal":   ("#0E8F9C", "#E5F5F6"),
    "navy":   ("#2B3A67", "#ECEFF7"),
    "yellow": ("#C08A00", "#FFF6D9"),
    "red":    ("#C0282D", "#FDECEC"),
    "gray":   ("#6B7280", "#F3F4F6"),
}


# ---------------------------------------------------------------- 텍스트 폭
def text_width(s: str, size: float) -> float:
    w = 0.0
    for ch in s:
        if unicodedata.east_asian_width(ch) in ("W", "F"):
            w += 1.0
        elif ch in "mwMW@":
            w += 0.82
        elif ch.isupper() or ch.isdigit():
            w += 0.64
        elif ch in " .,:;'!|il·":
            w += 0.32
        else:
            w += 0.56
    return w * size


def wrap(s: str, size: float, max_w: float, max_lines: int = 3):
    """공백 기준 줄바꿈, 너무 긴 단어는 글자 단위로 자릅니다."""
    words = s.split(" ")
    lines, cur = [], ""
    for word in words:
        cand = word if not cur else cur + " " + word
        if text_width(cand, size) <= max_w:
            cur = cand
            continue
        if cur:
            lines.append(cur)
        cur = ""
        # 단어 자체가 길면 글자 단위
        piece = ""
        for ch in word:
            if text_width(piece + ch, size) <= max_w:
                piece += ch
            else:
                lines.append(piece)
                piece = ch
        cur = piece
    if cur:
        lines.append(cur)
    if len(lines) > max_lines:
        lines = lines[:max_lines]
        last = lines[-1]
        while last and text_width(last + "…", size) > max_w:
            last = last[:-1]
        lines[-1] = last + "…"
    return lines


# ---------------------------------------------------------------- 아이콘
def icon(name: str, x: float, y: float, c: str) -> str:
    """x, y = 중심. 약 24px 라인 아이콘."""
    f = f'fill="{c}" fill-opacity="0.18" stroke="{c}" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"'
    s = f'fill="none" stroke="{c}" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"'
    dot = f'fill="{c}"'
    s2 = f'fill="none" stroke="{c}" stroke-width="2.3" stroke-linejoin="round" stroke-linecap="round"'
    I = {
        "user": f'<circle cx="{x}" cy="{y-5}" r="5" {f}/><path d="M{x-10} {y+11}Q{x-10} {y+1} {x} {y+1}Q{x+10} {y+1} {x+10} {y+11}Z" {f}/>',
        "users": f'<circle cx="{x-5}" cy="{y-5}" r="4.5" {f}/><circle cx="{x+6}" cy="{y-4}" r="4" {f}/><path d="M{x-14} {y+10}Q{x-14} {y+1} {x-5} {y+1}Q{x+4} {y+1} {x+4} {y+10}Z" {f}/><path d="M{x+6} {y+2}Q{x+13} {y+2} {x+13} {y+10}" {s}/>',
        "mail": f'<rect x="{x-12}" y="{y-8}" width="24" height="17" rx="2.5" {f}/><path d="M{x-12} {y-7}L{x} {y+2}L{x+12} {y-7}" {s}/>',
        "db": f'<path d="M{x-10} {y-7}V{y+8}A10 4 0 0 0 {x+10} {y+8}V{y-7}" {f}/><ellipse cx="{x}" cy="{y-7}" rx="10" ry="4" {f}/><path d="M{x-10} {y+1}A10 4 0 0 0 {x+10} {y+1}" {s}/>',
        "clock": f'<circle cx="{x}" cy="{y}" r="11" {f}/><path d="M{x} {y-6}V{y}L{x+5} {y+3}" {s}/>',
        "inbox": f'<path d="M{x-12} {y+1}V{y+10}H{x+12}V{y+1}Z" {f}/><path d="M{x} {y-11}V{y+4}M{x-5} {y-1}L{x} {y+4}L{x+5} {y-1}" {s}/>',
        "spark": f'<path d="M{x} {y-12}Q{x+2} {y-2} {x+12} {y}Q{x+2} {y+2} {x} {y+12}Q{x-2} {y+2} {x-12} {y}Q{x-2} {y-2} {x} {y-12}Z" {f}/>',
        "sheet": f'<rect x="{x-11}" y="{y-11}" width="22" height="22" rx="2.5" {f}/><path d="M{x-11} {y-4}H{x+11}M{x-11} {y+4}H{x+11}M{x-3} {y-11}V{y+11}" {s}/>',
        "send": f'<path d="M{x-12} {y-1}L{x+12} {y-10}L{x+4} {y+11}L{x} {y+2}Z" {f}/><path d="M{x} {y+2}L{x+12} {y-10}" {s}/>',
        "check": f'<circle cx="{x}" cy="{y}" r="11" {f}/><path d="M{x-5} {y}L{x-1} {y+4}L{x+6} {y-4}" {s}/>',
        "doc": f'<path d="M{x-9} {y-12}H{x+4}L{x+10} {y-6}V{y+12}H{x-9}Z" {f}/><path d="M{x-4} {y-2}H{x+5}M{x-4} {y+3}H{x+5}M{x-4} {y+8}H{x+2}" {s}/>',
        "docs": f'<path d="M{x-6} {y-13}H{x+8}L{x+12} {y-9}V{y+7}" {s}/><path d="M{x-11} {y-9}H{x+3}L{x+7} {y-5}V{y+13}H{x-11}Z" {f}/>',
        "bot": f'<path d="M{x} {y-8}V{y-12}" {s}/><circle cx="{x}" cy="{y-13}" r="2" {dot}/><rect x="{x-11}" y="{y-8}" width="22" height="18" rx="5" {f}/><circle cx="{x-4.5}" cy="{y+1}" r="2" {dot}/><circle cx="{x+4.5}" cy="{y+1}" r="2" {dot}/>',
        "code": f'<path d="M{x-5} {y-8}L{x-12} {y}L{x-5} {y+8}M{x+5} {y-8}L{x+12} {y}L{x+5} {y+8}M{x+2} {y-10}L{x-2} {y+10}" {s2}/>',
        "flask": f'<path d="M{x-4} {y-11}V{y-3}L{x-11} {y+9}Q{x-12} {y+11} {x-9} {y+11}H{x+9}Q{x+12} {y+11} {x+11} {y+9}L{x+4} {y-3}V{y-11}" {f}/><path d="M{x-7} {y-11}H{x+7}" {s}/>',
        "git": f'<path d="M{x-13} {y}H{x-5}M{x+5} {y}H{x+13}" {s}/><circle cx="{x}" cy="{y}" r="5.5" {f}/>',
        "list": f'<circle cx="{x-9}" cy="{y-7}" r="2" {dot}/><circle cx="{x-9}" cy="{y}" r="2" {dot}/><circle cx="{x-9}" cy="{y+7}" r="2" {dot}/><path d="M{x-3} {y-7}H{x+11}M{x-3} {y}H{x+11}M{x-3} {y+7}H{x+7}" {s}/>',
        "web": f'<rect x="{x-12}" y="{y-10}" width="24" height="20" rx="2.5" {f}/><path d="M{x-12} {y-4}H{x+12}" {s}/>',
        "server": f'<rect x="{x-11}" y="{y-11}" width="22" height="10" rx="2" {f}/><rect x="{x-11}" y="{y+1}" width="22" height="10" rx="2" {f}/><circle cx="{x-6}" cy="{y-6}" r="1.5" {dot}/><circle cx="{x-6}" cy="{y+6}" r="1.5" {dot}/>',
        "plug": f'<path d="M{x-4} {y-7}V{y-12}M{x+4} {y-7}V{y-12}M{x} {y+6}V{y+12}" {s}/><rect x="{x-8}" y="{y-7}" width="16" height="13" rx="4" {f}/>',
        "eye": f'<path d="M{x-12} {y}Q{x} {y-11} {x+12} {y}Q{x} {y+11} {x-12} {y}Z" {f}/><circle cx="{x}" cy="{y}" r="3.2" {dot}/>',
        "cloud": f'<path d="M{x-9} {y+8}H{x+9}A6 6 0 0 0 {x+9} {y-4}A8 8 0 0 0 {x-6} {y-3}A5.5 5.5 0 0 0 {x-9} {y+8}Z" {f}/>',
        "cycle": f'<path d="M{x+10} {y-3}A10.5 10.5 0 1 0 {x+10} {y+4}" {s}/><path d="M{x+11} {y-11}V{y-3}H{x+3}" {s}/>',
        "bolt": f'<path d="M{x+2} {y-12}L{x-8} {y+1}H{x}L{x-2} {y+12}L{x+8} {y-1}H{x}Z" {f}/>',
        "chat": f'<path d="M{x-12} {y-10}H{x+12}V{y+5}H{x-2}L{x-8} {y+11}V{y+5}H{x-12}Z" {f}/>',
        "nodes": f'<path d="M{x} {y-7}L{x-9} {y+7}M{x} {y-7}L{x+9} {y+7}M{x-9} {y+7}H{x+9}" {s}/><circle cx="{x}" cy="{y-7}" r="5" {f}/><circle cx="{x-9}" cy="{y+7}" r="5" {f}/><circle cx="{x+9}" cy="{y+7}" r="5" {f}/>',
        "shield": f'<path d="M{x} {y-12}L{x+10} {y-8}V{y}Q{x+10} {y+9} {x} {y+12}Q{x-10} {y+9} {x-10} {y}V{y-8}Z" {f}/>',
        "stack": f'<path d="M{x} {y-11}L{x+12} {y-5}L{x} {y+1}L{x-12} {y-5}Z" {f}/><path d="M{x-12} {y+1}L{x} {y+7}L{x+12} {y+1}M{x-12} {y+6}L{x} {y+12}L{x+12} {y+6}" {s}/>',
        "cup": f'<path d="M{x-3} {y-13}Q{x-6} {y-10} {x-3} {y-7}M{x+3} {y-13}Q{x} {y-10} {x+3} {y-7}" {s}/><path d="M{x-11} {y-3}H{x+7}V{y+5}Q{x+7} {y+11} {x+1} {y+11}H{x-5}Q{x-11} {y+11} {x-11} {y+5}Z" {f}/><path d="M{x+7} {y-1}Q{x+13} {y-1} {x+13} {y+3}Q{x+13} {y+7} {x+7} {y+7}" {s}/>',
        "search": f'<circle cx="{x-3}" cy="{y-3}" r="8" {f}/><path d="M{x+3} {y+3}L{x+11} {y+11}" {s2}/>',
        "lock": f'<rect x="{x-10}" y="{y-3}" width="20" height="15" rx="3" {f}/><path d="M{x-6} {y-3}V{y-7}A6 6 0 0 1 {x+6} {y-7}V{y-3}" {s}/>',
        "chart": f'<path d="M{x-12} {y+11}H{x+12}" {s}/><rect x="{x-10}" y="{y-1}" width="5" height="11" {f}/><rect x="{x-2.5}" y="{y-8}" width="5" height="18" {f}/><rect x="{x+5}" y="{y-4}" width="5" height="14" {f}/>',
        "chip": f'<rect x="{x-9}" y="{y-9}" width="18" height="18" rx="3" {f}/><path d="M{x-4} {y-13}V{y-9}M{x+4} {y-13}V{y-9}M{x-4} {y+9}V{y+13}M{x+4} {y+9}V{y+13}M{x-13} {y-4}H{x-9}M{x-13} {y+4}H{x-9}M{x+9} {y-4}H{x+13}M{x+9} {y+4}H{x+13}" {s}/>',
        "brain": f'<path d="M{x} {y-11}Q{x-11} {y-12} {x-11} {y-2}Q{x-13} {y+6} {x-6} {y+10}Q{x-1} {y+13} {x} {y+8}Q{x+1} {y+13} {x+6} {y+10}Q{x+13} {y+6} {x+11} {y-2}Q{x+11} {y-12} {x} {y-11}Z" {f}/><path d="M{x} {y-11}V{y+8}M{x-6} {y-3}Q{x-3} {y} {x} {y-3}M{x+6} {y+2}Q{x+3} {y+5} {x} {y+2}" {s}/>',
        "gear": f'<circle cx="{x}" cy="{y}" r="5" {s}/><path d="M{x} {y-12}V{y-8}M{x} {y+8}V{y+12}M{x-12} {y}H{x-8}M{x+8} {y}H{x+12}M{x-8.5} {y-8.5}L{x-5.7} {y-5.7}M{x+5.7} {y+5.7}L{x+8.5} {y+8.5}M{x-8.5} {y+8.5}L{x-5.7} {y+5.7}M{x+5.7} {y-5.7}L{x+8.5} {y-8.5}" {s2}/><circle cx="{x}" cy="{y}" r="8" {f}/>',
        "money": f'<rect x="{x-13}" y="{y-8}" width="26" height="16" rx="3" {f}/><circle cx="{x}" cy="{y}" r="4" {s}/>',
        "building": f'<rect x="{x-10}" y="{y-12}" width="20" height="24" rx="1.5" {f}/><path d="M{x-5} {y-7}H{x-2}M{x+2} {y-7}H{x+5}M{x-5} {y-2}H{x-2}M{x+2} {y-2}H{x+5}M{x-5} {y+3}H{x-2}M{x+2} {y+3}H{x+5}M{x-2} {y+12}V{y+8}H{x+2}V{y+12}" {s}/>',
        "target": f'<circle cx="{x}" cy="{y}" r="11" {f}/><circle cx="{x}" cy="{y}" r="6" {s}/><circle cx="{x}" cy="{y}" r="2" {dot}/>',
        "key": f'<circle cx="{x-6}" cy="{y}" r="6" {f}/><path d="M{x} {y}H{x+12}M{x+8} {y}V{y+5}M{x+12} {y}V{y+4}" {s}/>',
        "terminal": f'<rect x="{x-12}" y="{y-10}" width="24" height="20" rx="3" {f}/><path d="M{x-7} {y-4}L{x-3} {y}L{x-7} {y+4}M{x-1} {y+5}H{x+6}" {s}/>',
        "warn": f'<path d="M{x} {y-12}L{x+12} {y+10}H{x-12}Z" {f}/><path d="M{x} {y-4}V{y+3}" {s}/><circle cx="{x}" cy="{y+7}" r="1.4" {dot}/>',
        "layers": f'<path d="M{x} {y-11}L{x+12} {y-5}L{x} {y+1}L{x-12} {y-5}Z" {f}/><path d="M{x-12} {y+1}L{x} {y+7}L{x+12} {y+1}M{x-12} {y+6}L{x} {y+12}L{x+12} {y+6}" {s}/>',
        "token": f'<rect x="{x-12}" y="{y-7}" width="7" height="14" rx="2" {f}/><rect x="{x-3.5}" y="{y-7}" width="7" height="14" rx="2" {f}/><rect x="{x+5}" y="{y-7}" width="7" height="14" rx="2" {f}/>',
        "vector": f'<path d="M{x-11} {y+10}L{x+9} {y-8}M{x-11} {y+10}L{x+11} {y+6}M{x-11} {y+10}L{x-3} {y-11}" {s}/><circle cx="{x-11}" cy="{y+10}" r="2" {dot}/><path d="M{x+5} {y-8}H{x+9}V{y-4}" {s}/>',
        "rocket": f'<path d="M{x} {y-13}Q{x+8} {y-6} {x+6} {y+6}H{x-6}Q{x-8} {y-6} {x} {y-13}Z" {f}/><circle cx="{x}" cy="{y-3}" r="2.5" {s}/><path d="M{x-6} {y+3}L{x-10} {y+9}M{x+6} {y+3}L{x+10} {y+9}M{x-2} {y+8}V{y+12}M{x+2} {y+8}V{y+12}" {s}/>',
    }
    return I.get(name, "")


# ---------------------------------------------------------------- 그리기
WARNINGS = []


def build(spec: dict) -> str:
    cols = spec.get("cols", 4)
    rows = spec.get("rows", 1)
    ch = spec.get("cell_h", 132)
    cw = (WIDTH - 2 * MARGIN) / cols
    notes = spec.get("notes", [])
    grid_h = rows * ch
    height = TOP + grid_h + 20 + len(notes) * 22 + (16 if notes else 0) + 12

    def cell_box(col, row, span=1, rspan=1):
        x = MARGIN + col * cw + PAD_X
        y = TOP + row * ch + PAD_Y
        w = span * cw - 2 * PAD_X
        h = rspan * ch - 2 * PAD_Y
        return x, y, w, h

    parts = []
    parts.append(f'<rect x="1" y="1" width="{WIDTH-2}" height="{height-2}" rx="18" fill="#FFFFFF" stroke="{LINE}" stroke-width="1.5"/>')
    # 제목
    parts.append(f'<rect x="{MARGIN}" y="26" width="5" height="26" rx="2.5" fill="{ACCENT}"/>')
    parts.append(f'<text x="{MARGIN+16}" y="46" font-size="21" font-weight="700" fill="{INK}">{escape(spec["title"])}</text>')
    if spec.get("subtitle"):
        parts.append(f'<text x="{MARGIN+16}" y="72" font-size="13.5" fill="{MUTED}">{escape(spec["subtitle"])}</text>')

    # 그룹 (노드 뒤)
    for g in spec.get("groups", []):
        stroke, fill = PALETTE.get(g.get("color", "gray"), PALETTE["gray"])
        x = MARGIN + g["col"] * cw + 6
        y = TOP + g["row"] * ch + 7
        w = g.get("cols", 1) * cw - 12
        h = g.get("rows", 1) * ch - 12
        parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="14" fill="{fill}" fill-opacity="0.45" stroke="{stroke}" stroke-width="1.6" stroke-dasharray="6 5"/>')
        if g.get("label"):
            lw = text_width(g["label"], 12) + 18
            parts.append(f'<rect x="{x+12:.1f}" y="{y-10:.1f}" width="{lw:.1f}" height="20" rx="10" fill="#FFFFFF" stroke="{stroke}" stroke-width="1.4"/>')
            parts.append(f'<text x="{x+21:.1f}" y="{y+4:.1f}" font-size="12" font-weight="700" fill="{stroke}">{escape(g["label"])}</text>')

    nodes = {}
    for n in spec.get("nodes", []):
        x, y, w, h = cell_box(n["col"], n["row"], n.get("span", 1), n.get("rspan", 1))
        nodes[n["id"]] = (x, y, w, h)

    # 화살표 (노드 아래에 깔리도록 먼저)
    edge_parts, label_parts = [], []
    for e in spec.get("edges", []):
        if e["from"] not in nodes or e["to"] not in nodes:
            raise ValueError(f"edge {e} refers to missing node")
        ax, ay, aw, ah = nodes[e["from"]]
        bx, by, bw, bh = nodes[e["to"]]
        acx, acy = ax + aw / 2, ay + ah / 2
        bcx, bcy = bx + bw / 2, by + bh / 2
        same_row = abs(acy - bcy) < 2
        same_col = abs(acx - bcx) < 2
        lx = ly = None
        if same_row:
            if bcx > acx:
                p1, p2 = (ax + aw, acy), (bx, bcy)
            else:
                p1, p2 = (ax, acy), (bx + bw, bcy)
            d = f"M{p1[0]:.1f} {p1[1]:.1f}H{p2[0]:.1f}"
            segs = [(p1, p2)]
            lx, ly = (p1[0] + p2[0]) / 2, p1[1] - 9
        elif same_col or (bx < ax + aw and bx + bw > ax):
            # 세로 연결 (겹치는 열 구간 가운데)
            cx_ = (max(ax, bx) + min(ax + aw, bx + bw)) / 2
            if bcy > acy:
                p1, p2 = (cx_, ay + ah), (cx_, by)
            else:
                p1, p2 = (cx_, ay), (cx_, by + bh)
            d = f"M{p1[0]:.1f} {p1[1]:.1f}V{p2[1]:.1f}"
            segs = [(p1, p2)]
            lx, ly = p1[0] + 8, (p1[1] + p2[1]) / 2 + 4
        elif e.get("route", "hv") == "vh":
            # 세로로 나가서 대상 중심 y 에서 가로로 들어감
            sy_ = ay + ah if bcy > acy else ay
            tx_ = bx if bcx > acx else bx + bw
            d = f"M{acx:.1f} {sy_:.1f}V{bcy:.1f}H{tx_:.1f}"
            segs = [((acx, sy_), (acx, bcy)), ((acx, bcy), (tx_, bcy))]
            lx, ly = (acx + tx_) / 2, bcy - 9
        else:
            # 가로로 나가서 대상 중심 x 에서 세로로 들어감
            if bcx > acx:
                sx_ = ax + aw
            else:
                sx_ = ax
            ty = by if bcy > acy else by + bh
            d = f"M{sx_:.1f} {acy:.1f}H{bcx:.1f}V{ty:.1f}"
            segs = [((sx_, acy), (bcx, acy)), ((bcx, acy), (bcx, ty))]
            lx, ly = (sx_ + bcx) / 2, acy - 9
        for (sx1, sy1), (sx2, sy2) in segs:
            for nid, (nx, ny, nw, nh) in nodes.items():
                if nid in (e["from"], e["to"]):
                    continue
                lo_x, hi_x = min(sx1, sx2), max(sx1, sx2)
                lo_y, hi_y = min(sy1, sy2), max(sy1, sy2)
                if lo_x < nx + nw - 2 and hi_x > nx + 2 and lo_y < ny + nh - 2 and hi_y > ny + 2:
                    WARNINGS.append(f"{e['from']}→{e['to']} 연결선이 '{nid}' 박스를 지나갑니다 (route 바꾸거나 배치 조정)")
        dash = ' stroke-dasharray="6 5"' if e.get("dashed", True) else ""
        start = ' marker-start="url(#arw)"' if e.get("both") else ""
        edge_parts.append(f'<path d="{d}" fill="none" stroke="{EDGE}" stroke-width="2"{dash}{start} marker-end="url(#arw)"/>')
        if e.get("label"):
            anchor = "start" if not same_row and (same_col or (bx < ax + aw and bx + bw > ax)) else "middle"
            lw = text_width(e["label"], 11.5)
            bgx = lx - (lw / 2 if anchor == "middle" else 0) - 4
            label_parts.append(f'<rect x="{bgx:.1f}" y="{ly-12:.1f}" width="{lw+8:.1f}" height="16" rx="4" fill="#FFFFFF" fill-opacity="0.92"/>')
            label_parts.append(f'<text x="{lx:.1f}" y="{ly:.1f}" font-size="11.5" font-weight="600" fill="{MUTED}" text-anchor="{anchor}">{escape(e["label"])}</text>')

    parts += edge_parts

    # 노드
    for n in spec.get("nodes", []):
        x, y, w, h = nodes[n["id"]]
        stroke, fill = PALETTE.get(n.get("color", "gray"), PALETTE["gray"])
        sw = 2.6 if n.get("emph") else 2
        parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')
        cx_ = x + w / 2
        has_icon = bool(n.get("icon"))
        label_lines = wrap(n["label"], 14.5, w - 16, 2)
        sub_lines = wrap(n.get("sub", ""), 12, w - 16, 3) if n.get("sub") else []
        block_h = (30 if has_icon else 0) + len(label_lines) * 19 + (4 + len(sub_lines) * 16 if sub_lines else 0)
        ty = y + (h - block_h) / 2
        if has_icon:
            parts.append(icon(n["icon"], round(cx_, 1), round(ty + 12, 1), stroke))
            ty += 30
        for i, ln in enumerate(label_lines):
            parts.append(f'<text x="{cx_:.1f}" y="{ty + 14 + i*19:.1f}" font-size="14.5" font-weight="700" fill="{INK}" text-anchor="middle">{escape(ln)}</text>')
        ty += len(label_lines) * 19
        if sub_lines:
            ty += 4
            for i, ln in enumerate(sub_lines):
                parts.append(f'<text x="{cx_:.1f}" y="{ty + 12 + i*16:.1f}" font-size="12" fill="{MUTED}" text-anchor="middle">{escape(ln)}</text>')
        if n.get("badge") is not None:
            parts.append(f'<circle cx="{x+2:.1f}" cy="{y+2:.1f}" r="12" fill="{stroke}"/>')
            parts.append(f'<text x="{x+2:.1f}" y="{y+6.5:.1f}" font-size="12.5" font-weight="700" fill="#FFFFFF" text-anchor="middle">{escape(str(n["badge"]))}</text>')

    parts += label_parts

    # 메모
    ny = TOP + grid_h + 30
    for note in notes:
        parts.append(f'<text x="{MARGIN+4}" y="{ny:.1f}" font-size="12.5" fill="{MUTED}">· {escape(note)}</text>')
        ny += 22

    title = escape(spec["title"])
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {WIDTH} {height:.0f}" width="{WIDTH}" height="{height:.0f}" '
        f'role="img" aria-label="{title}" font-family="{FONT}">'
        f'<title>{title}</title>'
        f'<defs><marker id="arw" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
        f'<path d="M0 0L10 5L0 10z" fill="{EDGE}"/></marker></defs>'
        + "".join(parts) + "</svg>\n"
    )
    return svg


def main(argv):
    OUT_DIR.mkdir(exist_ok=True)
    names = argv[1:] or sorted(p.stem for p in SPEC_DIR.glob("*.json"))
    ok = 0
    for name in names:
        spec_path = SPEC_DIR / f"{name}.json"
        spec = json.loads(spec_path.read_text(encoding="utf-8"))
        WARNINGS.clear()
        svg = build(spec)
        xml.dom.minidom.parseString(svg.encode("utf-8"))   # <img> 로 쓰려면 엄격한 XML 이어야 함
        (OUT_DIR / f"{name}.svg").write_text(svg, encoding="utf-8")
        for w in WARNINGS:
            print(f"경고 [{name}] {w}")
        ok += 1
    print(f"{ok}개 도식 생성 → {OUT_DIR.relative_to(ROOT)}/")


if __name__ == "__main__":
    main(sys.argv)
