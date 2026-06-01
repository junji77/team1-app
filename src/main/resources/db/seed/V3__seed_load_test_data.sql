-- V3: 부하 테스트용 학생 시드 (student3 ~ student3000)
--   - V2 가 student1, student2 (student_number 20231001/20231002) 를 이미 넣었으므로 3번부터 이어서 생성.
--     → 합치면 student1 ~ student3000 연속. k6 는 student{__VU}@test.com 으로 로그인.
--   - 비밀번호: 전부 'password' (V2 와 동일한 BCrypt 해시로 통일).
--   - 강의는 V2 에 이미 있음 → 여기서 만들지 않음.
--     "100석 1000명" 시나리오는 V2 의 정원 100 강의(GEN1001 '대학 영어 1' 등)를 타깃으로 사용.
--
-- 배치 위치: src/main/resources/db/migration/  (db/seed 아님 — dev RDS 에도 적용되어야 k6 가 사용)
-- 주의: prod 복제(Step 14) 전에 이 데이터를 제외/정리할 방안 고려.

-- 재귀 CTE 기본 한도(1000)로는 ~3000행을 못 만든다 → 상향
SET SESSION cte_max_recursion_depth = 4000;

INSERT INTO students (student_number, name, grade, email, password)
WITH RECURSIVE seq (n) AS (
    SELECT 3                                 -- student1, student2 는 V2 가 보유
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 3000
)
SELECT
    CONCAT('2023', 1000 + n),                                        -- student_number: 20231003 .. (V2 의 1001/1002 이어서)
    CONCAT(
        ELT((n % 10) + 1, '김','이','박','최','정','강','조','윤','장','임'),
        ELT((n % 8)  + 1, '민준','서연','도윤','지우','하준','수아','지호','예은')
    ),                                                               -- name: 한국식 이름 (중복 허용)
    (n % 4) + 1,                                                     -- grade 1~4
    CONCAT('student', n, '@test.com'),                               -- email: student3@test.com ..
    '$2a$10$QYt6EqWzVmUJ577nwFsK.O6nHwj1774wZbsiYfAM6ER69SZ/NOyTK'   -- BCrypt('password'), V2 와 동일
FROM seq;