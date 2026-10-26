# AI Tech 스터디 학습 사이트

AX 비즈니스 전환 스터디의 16주(4단계) AI 학습 사이트입니다.
https://odysseyiliadr2d2.github.io/ai-study/

- 노선도: 16주 과정을 4개 노선(기초·활용·심화·전문/사업화)으로 표시
- 주차 페이지: 학습 목표, 강의 노트, 실습 과제, 영상·자료, 관련 용어(실무 AI 용어집 연결), 확인 퀴즈
- 내 진도 / 대시보드: 이름과 숫자 PIN으로 등록하면 진도가 Supabase에 저장되어 멤버 전체 현황을 볼 수 있음

## 구조
| 파일 | 내용 |
| --- | --- |
| `index.html`, `style.css`, `app.js` | 화면과 동작 (빌드 없는 정적 사이트) |
| `data/stage1.js` ~ `stage4.js` | 주차별 강의 노트·실습·퀴즈 |
| `data/media.js` | 주차별 영상·강좌 링크 |
| `data/glossary.js` | 본문에서 연결하는 용어집 용어 목록 |
| `supabase/schema.sql` | 진도 저장 테이블과 함수 |

## 강의 노트 고치기
`data/stageN.js`의 `sections[].body`를 수정합니다. 문법: 빈 줄로 문단 구분, `- ` 목록, `1. ` 번호 목록, `> ` 핵심 정리, 코드 블록, `**굵게**`, `` `코드` ``, `[[용어집id|표시어]]`(용어집 링크).
새 용어 링크를 넣었다면 `data/glossary.js`에 해당 용어가 있는지 확인하세요.

## 진도 데이터
Supabase 프로젝트(ai-glossary)의 `study_members`, `study_progress` 테이블에 저장됩니다. 테이블은 직접 읽거나 쓸 수 없고, `study_register`, `study_login`, `study_save`, `study_dashboard` 함수로만 접근합니다. PIN은 bcrypt로 해시해 저장합니다.
