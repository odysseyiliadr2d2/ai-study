-- 01_schema.sql : 정보계 DW 스타일 실습 스키마 (Oracle 19c 이상 / 23ai Free)
-- 컨테이너 초기화 스크립트는 SYS 로 CDB 에 붙으므로 PDB 로 옮겨 갑니다.
-- 사내 개발 DB 에서 DBA 가 직접 실행할 때는 아래 ALTER SESSION 줄을 빼고 실행합니다.
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER dw IDENTIFIED BY "change_me_dw" QUOTA UNLIMITED ON users;
CREATE USER t2s_ro IDENTIFIED BY "change_me_ro";
GRANT CREATE SESSION TO t2s_ro;

CREATE TABLE dw.cmn_biz_day_d (
  base_dt      VARCHAR2(8)  NOT NULL,
  biz_day_yn   CHAR(1)      NOT NULL,
  weekday_nm   VARCHAR2(3 CHAR) NOT NULL,
  holiday_nm   VARCHAR2(50 CHAR),
  CONSTRAINT pk_cmn_biz_day_d PRIMARY KEY (base_dt)
);
COMMENT ON TABLE  dw.cmn_biz_day_d IS '영업일 달력. 일자별 영업일 여부와 휴일명';
COMMENT ON COLUMN dw.cmn_biz_day_d.base_dt    IS '기준일자(YYYYMMDD 문자열)';
COMMENT ON COLUMN dw.cmn_biz_day_d.biz_day_yn IS '영업일여부(Y=영업일, N=휴장일: 주말·공휴일)';
COMMENT ON COLUMN dw.cmn_biz_day_d.weekday_nm IS '요일명(월~일)';
COMMENT ON COLUMN dw.cmn_biz_day_d.holiday_nm IS '휴일명(영업일이면 NULL)';

CREATE TABLE dw.cmn_code_d (
  code_grp  VARCHAR2(20)  NOT NULL,
  code_val  VARCHAR2(10)  NOT NULL,
  code_nm   VARCHAR2(100) NOT NULL,
  CONSTRAINT pk_cmn_code_d PRIMARY KEY (code_grp, code_val)
);
COMMENT ON TABLE  dw.cmn_code_d IS '공통코드. 코드그룹별 코드값과 코드명';
COMMENT ON COLUMN dw.cmn_code_d.code_grp IS '코드그룹(CHNL=거래채널, PRD_CLS=상품분류, REGION=지역)';
COMMENT ON COLUMN dw.cmn_code_d.code_val IS '코드값';
COMMENT ON COLUMN dw.cmn_code_d.code_nm  IS '코드명';

CREATE TABLE dw.org_branch_d (
  brn_cd     VARCHAR2(4)  NOT NULL,
  brn_nm     VARCHAR2(50) NOT NULL,
  region_cd  VARCHAR2(2)  NOT NULL,
  CONSTRAINT pk_org_branch_d PRIMARY KEY (brn_cd)
);
COMMENT ON TABLE  dw.org_branch_d IS '지점 차원';
COMMENT ON COLUMN dw.org_branch_d.brn_cd    IS '지점코드';
COMMENT ON COLUMN dw.org_branch_d.brn_nm    IS '지점명';
COMMENT ON COLUMN dw.org_branch_d.region_cd IS '지역코드(공통코드 REGION)';

CREATE TABLE dw.prd_product_d (
  prd_cd      VARCHAR2(10)  NOT NULL,
  prd_nm      VARCHAR2(100) NOT NULL,
  prd_cls_cd  VARCHAR2(2)   NOT NULL,
  CONSTRAINT pk_prd_product_d PRIMARY KEY (prd_cd)
);
COMMENT ON TABLE  dw.prd_product_d IS '상품 차원';
COMMENT ON COLUMN dw.prd_product_d.prd_cd     IS '상품코드';
COMMENT ON COLUMN dw.prd_product_d.prd_nm     IS '상품명';
COMMENT ON COLUMN dw.prd_product_d.prd_cls_cd IS '상품분류코드(공통코드 PRD_CLS: 01 주식, 02 채권, 03 펀드, 04 ELS)';

CREATE TABLE dw.trd_day_f (
  base_dt   VARCHAR2(8)   NOT NULL,
  brn_cd    VARCHAR2(4)   NOT NULL,
  prd_cd    VARCHAR2(10)  NOT NULL,
  chnl_cd   VARCHAR2(2)   NOT NULL,
  trd_cnt   NUMBER(12)    NOT NULL,
  trd_amt   NUMBER(18)    NOT NULL,
  fee_amt   NUMBER(15)    NOT NULL,
  CONSTRAINT pk_trd_day_f PRIMARY KEY (base_dt, brn_cd, prd_cd, chnl_cd)
);
COMMENT ON TABLE  dw.trd_day_f IS '일별 거래 집계 팩트. 영업일·지점·상품·채널 단위 거래건수와 금액';
COMMENT ON COLUMN dw.trd_day_f.base_dt IS '거래일자(YYYYMMDD). 영업일만 적재';
COMMENT ON COLUMN dw.trd_day_f.brn_cd  IS '관리지점코드(ORG_BRANCH_D.BRN_CD)';
COMMENT ON COLUMN dw.trd_day_f.prd_cd  IS '상품코드(PRD_PRODUCT_D.PRD_CD)';
COMMENT ON COLUMN dw.trd_day_f.chnl_cd IS '거래채널코드(공통코드 CHNL: 01 영업점, 02 HTS, 03 MTS)';
COMMENT ON COLUMN dw.trd_day_f.trd_cnt IS '거래건수';
COMMENT ON COLUMN dw.trd_day_f.trd_amt IS '거래금액(원). 매수+매도 약정금액 합계';
COMMENT ON COLUMN dw.trd_day_f.fee_amt IS '수수료금액(원)';

-- 읽기 전용 계정: SELECT 대신 READ 를 주면 SELECT ... FOR UPDATE 같은 잠금도 할 수 없습니다
GRANT READ ON dw.cmn_biz_day_d TO t2s_ro;
GRANT READ ON dw.cmn_code_d    TO t2s_ro;
GRANT READ ON dw.org_branch_d  TO t2s_ro;
GRANT READ ON dw.prd_product_d TO t2s_ro;
GRANT READ ON dw.trd_day_f     TO t2s_ro;

-- ── 실습용 가상 데이터 (실제 고객·계좌 정보 없음)

-- 영업일 달력: 2026-09-01 ~ 2026-10-31. 주말과 공휴일은 휴장
INSERT INTO dw.cmn_biz_day_d (base_dt, biz_day_yn, weekday_nm, holiday_nm)
SELECT TO_CHAR(d, 'YYYYMMDD'),
       CASE WHEN TO_CHAR(d, 'DY', 'NLS_DATE_LANGUAGE=AMERICAN') IN ('SAT', 'SUN')
              OR h.nm IS NOT NULL THEN 'N' ELSE 'Y' END,
       DECODE(TO_CHAR(d, 'DY', 'NLS_DATE_LANGUAGE=AMERICAN'),
              'MON', '월', 'TUE', '화', 'WED', '수', 'THU', '목', 'FRI', '금', 'SAT', '토', '일'),
       h.nm
FROM (SELECT DATE '2026-09-01' + LEVEL - 1 AS d FROM dual CONNECT BY LEVEL <= 61) c
LEFT JOIN (
  SELECT DATE '2026-09-24' AS hd, '추석 연휴' AS nm FROM dual UNION ALL
  SELECT DATE '2026-09-25', '추석' FROM dual UNION ALL
  SELECT DATE '2026-09-26', '추석 연휴' FROM dual UNION ALL
  SELECT DATE '2026-10-03', '개천절' FROM dual UNION ALL
  SELECT DATE '2026-10-05', '대체공휴일(개천절)' FROM dual UNION ALL
  SELECT DATE '2026-10-09', '한글날' FROM dual
) h ON h.hd = c.d;
-- 요일은 'D'(NLS_TERRITORY 에 따라 1 이 바뀜) 대신 언어를 고정한 'DY' 로 판단합니다

INSERT INTO dw.cmn_code_d VALUES ('CHNL', '01', '영업점');
INSERT INTO dw.cmn_code_d VALUES ('CHNL', '02', 'HTS');
INSERT INTO dw.cmn_code_d VALUES ('CHNL', '03', 'MTS');
INSERT INTO dw.cmn_code_d VALUES ('PRD_CLS', '01', '주식');
INSERT INTO dw.cmn_code_d VALUES ('PRD_CLS', '02', '채권');
INSERT INTO dw.cmn_code_d VALUES ('PRD_CLS', '03', '펀드');
INSERT INTO dw.cmn_code_d VALUES ('PRD_CLS', '04', 'ELS');
INSERT INTO dw.cmn_code_d VALUES ('REGION', '01', '서울');
INSERT INTO dw.cmn_code_d VALUES ('REGION', '02', '경기');
INSERT INTO dw.cmn_code_d VALUES ('REGION', '03', '부산');

INSERT INTO dw.org_branch_d VALUES ('B001', '본점영업부', '01');
INSERT INTO dw.org_branch_d VALUES ('B002', '강남지점', '01');
INSERT INTO dw.org_branch_d VALUES ('B003', '분당지점', '02');
INSERT INTO dw.org_branch_d VALUES ('B004', '부산지점', '03');

INSERT INTO dw.prd_product_d VALUES ('P01', '국내주식', '01');
INSERT INTO dw.prd_product_d VALUES ('P02', '해외주식', '01');
INSERT INTO dw.prd_product_d VALUES ('P03', '국고채', '02');
INSERT INTO dw.prd_product_d VALUES ('P04', '회사채', '02');
INSERT INTO dw.prd_product_d VALUES ('P05', '공모펀드', '03');
INSERT INTO dw.prd_product_d VALUES ('P06', 'ELS', '04');

-- 거래 팩트: 영업일 × 지점 × 상품 × 채널. 값은 규칙으로 만든 가짜 숫자
INSERT INTO dw.trd_day_f (base_dt, brn_cd, prd_cd, chnl_cd, trd_cnt, trd_amt, fee_amt)
SELECT d.base_dt, b.brn_cd, p.prd_cd, c.code_val,
       10 + MOD(TO_NUMBER(SUBSTR(d.base_dt, 7, 2)) * 7 + TO_NUMBER(SUBSTR(b.brn_cd, 2)) * 13
                + TO_NUMBER(SUBSTR(p.prd_cd, 2)) * 5 + TO_NUMBER(c.code_val) * 11, 90),
       (10 + MOD(TO_NUMBER(SUBSTR(d.base_dt, 7, 2)) * 7 + TO_NUMBER(SUBSTR(b.brn_cd, 2)) * 13
                + TO_NUMBER(SUBSTR(p.prd_cd, 2)) * 5 + TO_NUMBER(c.code_val) * 11, 90)) * 1500000,
       (10 + MOD(TO_NUMBER(SUBSTR(d.base_dt, 7, 2)) * 7 + TO_NUMBER(SUBSTR(b.brn_cd, 2)) * 13
                + TO_NUMBER(SUBSTR(p.prd_cd, 2)) * 5 + TO_NUMBER(c.code_val) * 11, 90)) * 2250
FROM dw.cmn_biz_day_d d
CROSS JOIN dw.org_branch_d b
CROSS JOIN dw.prd_product_d p
CROSS JOIN (SELECT code_val FROM dw.cmn_code_d WHERE code_grp = 'CHNL') c
WHERE d.biz_day_yn = 'Y';

COMMIT;
