package com.ops.app.courseregistration.enrollment;

import com.ops.app.courseregistration.course.entity.Course;
import com.ops.app.courseregistration.course.repository.CourseRepository;
import com.ops.app.courseregistration.enrollment.repository.EnrollmentRepository;
import com.ops.app.courseregistration.enrollment.service.EnrollmentPeriodValidator;
import com.ops.app.courseregistration.enrollment.service.EnrollmentService;
import com.ops.app.courseregistration.global.exception.BusinessException;
import com.ops.app.courseregistration.global.exception.ErrorCode;
import com.ops.app.courseregistration.student.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EnrollmentConcurrencyTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    /**
     * EnrollmentPeriodValidator 를 Mock 으로 대체한다.
     *
     * EnrollmentService.enroll() 첫 줄에서 periodValidator.validate() 를 호출하는데,
     * 실제 구현은 10:00~10:30 / 14:00~14:30 / 16:00~16:30 시간대 외에는
     * ENROLLMENT_NOT_OPEN 또는 SERVER_CLOSED 예외를 던진다.
     * 동시성 테스트는 시간과 무관하게 항상 실행되어야 하므로,
     * Mockito 기본 동작(void 메서드 → 아무것도 하지 않음)을 이용해 시간 제약을 우회한다.
     */
    @MockBean
    EnrollmentPeriodValidator periodValidator;

    @Autowired EnrollmentService enrollmentService;
    @Autowired StudentRepository studentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    // ══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ══════════════════════════════════════════════════════════════════════════

    private static class TestMetrics {
        final String scenarioName;
        final int threadCount;
        final long totalElapsedMs;
        final List<Long> latenciesMs;          // 정렬 완료
        final int successCount;
        final Map<ErrorCode, Integer> failureByErrorCode;
        final int unexpectedExceptionCount;
        final int finalCurrentEnrollment;
        final int finalRowCount;

        TestMetrics(String scenarioName, int threadCount, long totalElapsedMs,
                    List<Long> rawLatencies, int successCount,
                    Map<ErrorCode, Integer> failureByErrorCode,
                    int unexpectedExceptionCount,
                    int finalCurrentEnrollment, int finalRowCount) {
            this.scenarioName            = scenarioName;
            this.threadCount             = threadCount;
            this.totalElapsedMs          = totalElapsedMs;
            this.latenciesMs             = new ArrayList<>(rawLatencies);
            Collections.sort(this.latenciesMs);
            this.successCount            = successCount;
            this.failureByErrorCode      = failureByErrorCode;
            this.unexpectedExceptionCount = unexpectedExceptionCount;
            this.finalCurrentEnrollment  = finalCurrentEnrollment;
            this.finalRowCount           = finalRowCount;
        }

        long min() { return latenciesMs.isEmpty() ? 0 : latenciesMs.get(0); }
        long max() { return latenciesMs.isEmpty() ? 0 : latenciesMs.get(latenciesMs.size() - 1); }
        long percentile(double p) {
            if (latenciesMs.isEmpty()) return 0;
            int idx = (int) Math.ceil(p * latenciesMs.size()) - 1;
            return latenciesMs.get(Math.max(0, Math.min(idx, latenciesMs.size() - 1)));
        }
        double avg() {
            return latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }
    }

    /**
     * 한글 등 2칸 너비 문자를 고려한 표시 너비 계산.
     * 한글 음절(AC00-D7A3), 한글 자모(1100-11FF), 한글 호환 자모(3130-318F),
     * CJK 통합 한자(4E00-9FFF), 전각 ASCII(FF01-FF60)를 2칸으로 셈.
     */
    private static int displayWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            if ((c >= 0xAC00 && c <= 0xD7A3)
                    || (c >= 0x1100 && c <= 0x11FF)
                    || (c >= 0x3130 && c <= 0x318F)
                    || (c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0xFF01 && c <= 0xFF60)) {
                w += 2;
            } else {
                w += 1;
            }
        }
        return w;
    }

    /** targetWidth 기준으로 표시 너비를 맞춰 스페이스 패딩한 문자열 반환. */
    private static String padLabel(String label, int targetWidth) {
        int spaces = Math.max(0, targetWidth - displayWidth(label));
        return label + " ".repeat(spaces);
    }

    private static void printMetrics(TestMetrics m) {
        final String LINE = "================================================================";
        final int LW = 21; // 레이블 표시 너비 기준

        System.out.println();
        System.out.println(LINE);
        System.out.printf("시나리오: %s%n", m.scenarioName);
        System.out.println(LINE);

        System.out.println("[전체]");
        System.out.printf("  %s: %d%n",    padLabel("스레드 수",      LW), m.threadCount);
        System.out.printf("  %s: %d ms%n", padLabel("전체 처리 시간", LW), m.totalElapsedMs);
        System.out.println();

        System.out.println("[응답 시간 분포 — 단위 ms]");
        System.out.printf("  %s: %4d%n",   padLabel("min", LW), m.min());
        System.out.printf("  %s: %4d%n",   padLabel("p50", LW), m.percentile(0.50));
        System.out.printf("  %s: %4d%n",   padLabel("p95", LW), m.percentile(0.95));
        System.out.printf("  %s: %4d%n",   padLabel("p99", LW), m.percentile(0.99));
        System.out.printf("  %s: %4d%n",   padLabel("max", LW), m.max());
        System.out.printf("  %s: %6.1f%n", padLabel("평균",  LW), m.avg());
        System.out.println();

        System.out.println("[성공/실패]");
        System.out.printf("  %s: %4d%n",   padLabel("성공",                    LW), m.successCount);
        System.out.printf("  %s: %4d%n",   padLabel("실패 — COURSE_FULL",      LW),
                m.failureByErrorCode.getOrDefault(ErrorCode.COURSE_FULL, 0));
        System.out.printf("  %s: %4d%n",   padLabel("실패 — DUPLICATE",        LW),
                m.failureByErrorCode.getOrDefault(ErrorCode.DUPLICATE_ENROLLMENT, 0));
        System.out.printf("  %s: %4d%n",   padLabel("실패 — NOT_OPEN",         LW),
                m.failureByErrorCode.getOrDefault(ErrorCode.ENROLLMENT_NOT_OPEN, 0));
        System.out.printf("  %s: %4d%n",   padLabel("실패 — SERVER_CLOSED",    LW),
                m.failureByErrorCode.getOrDefault(ErrorCode.SERVER_CLOSED, 0));
        System.out.printf("  %s: %4d%n",   padLabel("예상 외 예외",             LW), m.unexpectedExceptionCount);
        System.out.println();

        System.out.println("[DB 최종 상태]");
        System.out.printf("  %s: %d%n",    padLabel("current_enrollment", LW), m.finalCurrentEnrollment);
        System.out.printf("  %s: %d%n",    padLabel("enrollments row 수", LW), m.finalRowCount);
        boolean consistent = (m.successCount == m.finalRowCount);
        System.out.printf("  %s: %s%n",    padLabel("무결성 (카운터=row)", LW),
                consistent ? "[OK]   일치" : "[FAIL] 불일치");
        System.out.println(LINE);
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DB helpers
    // ══════════════════════════════════════════════════════════════════════════

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM enrollments");
        jdbcTemplate.execute("DELETE FROM courses");
        jdbcTemplate.execute("DELETE FROM students");
    }

    private List<Long> insertStudents(int count) {
        for (int i = 1; i <= count; i++) {
            jdbcTemplate.update(
                "INSERT INTO students (student_number, name, grade, email, password) VALUES (?, ?, ?, ?, ?)",
                "S" + i, "Student" + i, 1, "s" + i + "@t.com", "pw");
        }
        return jdbcTemplate.queryForList("SELECT student_id FROM students ORDER BY student_id", Long.class);
    }

    private Long insertCourse(String code, int capacity) {
        Course course = Course.builder()
                .courseCode(code)
                .courseName("Test Course")
                .courseType("전공")
                .credits(3)
                .capacity(capacity)
                .currentEnrollment(0)
                .build();
        return courseRepository.save(course).getCourseId();
    }

    private int countEnrollmentsForCourse(Long courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE course_id = ?", Integer.class, courseId);
    }

    private int getCurrentEnrollment(Long courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_enrollment FROM courses WHERE course_id = ?", Integer.class, courseId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tests
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 시나리오 1: 정원 1, 동시 20명 — 단 1명만 성공해야 한다.
     */
    @Test
    void 극단_경쟁_정원1_동시20명() throws InterruptedException {
        int threadCount = 20;
        List<Long> studentIds = insertStudents(threadCount);
        Long courseId = insertCourse("C001", 1);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentHashMap<ErrorCode, AtomicInteger> failureByCode = new ConcurrentHashMap<>();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(threadCount));
        AtomicInteger unexpectedExceptions = new AtomicInteger();

        for (Long studentId : studentIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long t0 = System.nanoTime();
                    try {
                        enrollmentService.enroll(studentId, courseId);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        failureByCode.computeIfAbsent(e.getErrorCode(), k -> new AtomicInteger())
                                .incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long testStartNanos = System.nanoTime();
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS))
                .as("모든 스레드가 30초 내에 완료되어야 한다").isTrue();
        long totalElapsedMs = (System.nanoTime() - testStartNanos) / 1_000_000;
        executor.shutdown();

        // ── assertions ──────────────────────────────────────────────────────
        assertThat(successCount.get())
                .as("정원 1이므로 성공은 정확히 1명이어야 한다").isEqualTo(1);

        int courseFull = failureByCode.getOrDefault(ErrorCode.COURSE_FULL, new AtomicInteger()).get();
        assertThat(courseFull)
                .as("나머지 19명은 COURSE_FULL 예외를 받아야 한다").isEqualTo(19);

        int totalFailures = failureByCode.values().stream().mapToInt(AtomicInteger::get).sum();
        assertThat(totalFailures)
                .as("실패 총합 == 19").isEqualTo(19);

        int finalCurrentEnrollment = getCurrentEnrollment(courseId);
        int finalRowCount          = countEnrollmentsForCourse(courseId);
        assertThat(finalCurrentEnrollment)
                .as("DB courses.current_enrollment == 1").isEqualTo(1);
        assertThat(finalRowCount)
                .as("DB enrollments row 수 == 1").isEqualTo(1);

        // ── metrics ─────────────────────────────────────────────────────────
        Map<ErrorCode, Integer> failureMap = new HashMap<>();
        failureByCode.forEach((k, v) -> failureMap.put(k, v.get()));
        printMetrics(new TestMetrics(
                "테스트 1 — 극단 경쟁 (정원 1, 동시 20명)",
                threadCount, totalElapsedMs, latencies,
                successCount.get(), failureMap,
                unexpectedExceptions.get(),
                finalCurrentEnrollment, finalRowCount
        ));
    }

    /**
     * 시나리오 2: 정원 10, 동시 50명 — 10명 성공, 카운터와 row 수가 일치해야 한다.
     */
    @Test
    void 스케일_분배_정원10_동시50명() throws InterruptedException {
        int threadCount = 50;
        List<Long> studentIds = insertStudents(threadCount);
        Long courseId = insertCourse("C010", 10);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentHashMap<ErrorCode, AtomicInteger> failureByCode = new ConcurrentHashMap<>();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(threadCount));
        AtomicInteger unexpectedExceptions = new AtomicInteger();

        for (Long studentId : studentIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long t0 = System.nanoTime();
                    try {
                        enrollmentService.enroll(studentId, courseId);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        failureByCode.computeIfAbsent(e.getErrorCode(), k -> new AtomicInteger())
                                .incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long testStartNanos = System.nanoTime();
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS))
                .as("모든 스레드가 30초 내에 완료되어야 한다").isTrue();
        long totalElapsedMs = (System.nanoTime() - testStartNanos) / 1_000_000;
        executor.shutdown();

        // ── assertions ──────────────────────────────────────────────────────
        assertThat(successCount.get())
                .as("정원 10이므로 성공은 정확히 10명이어야 한다").isEqualTo(10);

        int courseFull = failureByCode.getOrDefault(ErrorCode.COURSE_FULL, new AtomicInteger()).get();
        assertThat(courseFull)
                .as("나머지 40명은 COURSE_FULL 예외를 받아야 한다").isEqualTo(40);

        int totalFailures = failureByCode.values().stream().mapToInt(AtomicInteger::get).sum();
        assertThat(totalFailures)
                .as("실패 총합 == 40").isEqualTo(40);

        int finalCurrentEnrollment = getCurrentEnrollment(courseId);
        int finalRowCount          = countEnrollmentsForCourse(courseId);
        assertThat(finalCurrentEnrollment)
                .as("DB courses.current_enrollment == 10").isEqualTo(10);
        assertThat(finalRowCount)
                .as("DB enrollments row 수 == 10").isEqualTo(10);
        assertThat(successCount.get())
                .as("서비스 성공 카운터와 DB row 수가 정확히 일치해야 한다 (무결성 핵심 체크)")
                .isEqualTo(finalRowCount);

        // ── metrics ─────────────────────────────────────────────────────────
        Map<ErrorCode, Integer> failureMap = new HashMap<>();
        failureByCode.forEach((k, v) -> failureMap.put(k, v.get()));
        printMetrics(new TestMetrics(
                "테스트 2 — 스케일 분배 (정원 10, 동시 50명)",
                threadCount, totalElapsedMs, latencies,
                successCount.get(), failureMap,
                unexpectedExceptions.get(),
                finalCurrentEnrollment, finalRowCount
        ));
    }

    /**
     * 시나리오 3: 정원 5, 학생1이 동시 11회 + 학생2~10이 각 1회
     * — UNIQUE 제약과 정원 제약이 동시에 작동해야 한다.
     */
    @Test
    void 중복신청_정원경쟁_혼합_정원5_동시20명() throws InterruptedException {
        int totalStudents = 10;
        List<Long> studentIds = insertStudents(totalStudents);
        Long courseId = insertCourse("C005", 5);

        Long student1Id          = studentIds.get(0);
        List<Long> otherStudentIds = studentIds.subList(1, 10); // 학생 2~10

        int student1Attempts = 11;
        int otherAttempts    = otherStudentIds.size(); // 9
        int threadCount      = student1Attempts + otherAttempts; // 20

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentHashMap<ErrorCode, AtomicInteger> failureByCode = new ConcurrentHashMap<>();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(threadCount));
        AtomicInteger unexpectedExceptions = new AtomicInteger();

        // 학생 1: 11번 동시 신청
        for (int i = 0; i < student1Attempts; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long t0 = System.nanoTime();
                    try {
                        enrollmentService.enroll(student1Id, courseId);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        failureByCode.computeIfAbsent(e.getErrorCode(), k -> new AtomicInteger())
                                .incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 학생 2~10: 각 1번 신청
        for (Long studentId : otherStudentIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long t0 = System.nanoTime();
                    try {
                        enrollmentService.enroll(studentId, courseId);
                        successCount.incrementAndGet();
                    } catch (BusinessException e) {
                        failureByCode.computeIfAbsent(e.getErrorCode(), k -> new AtomicInteger())
                                .incrementAndGet();
                    } catch (Exception e) {
                        unexpectedExceptions.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long testStartNanos = System.nanoTime();
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS))
                .as("모든 스레드가 30초 내에 완료되어야 한다").isTrue();
        long totalElapsedMs = (System.nanoTime() - testStartNanos) / 1_000_000;
        executor.shutdown();

        // ── assertions ──────────────────────────────────────────────────────
        assertThat(successCount.get())
                .as("정원 5이므로 총 성공은 정확히 5명이어야 한다").isEqualTo(5);

        int student1EnrollmentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND course_id = ?",
                Integer.class, student1Id, courseId);
        assertThat(student1EnrollmentRows)
                .as("학생 1은 UNIQUE 제약으로 최대 1번만 등록되어야 한다").isEqualTo(1);

        int duplicateCount = failureByCode.getOrDefault(ErrorCode.DUPLICATE_ENROLLMENT, new AtomicInteger()).get();
        int fullCount      = failureByCode.getOrDefault(ErrorCode.COURSE_FULL, new AtomicInteger()).get();
        int totalFailures  = failureByCode.values().stream().mapToInt(AtomicInteger::get).sum();
        assertThat(duplicateCount + fullCount)
                .as("실패는 DUPLICATE_ENROLLMENT + COURSE_FULL 두 종류만 존재해야 한다")
                .isEqualTo(totalFailures)
                .isEqualTo(15);

        int finalCurrentEnrollment = getCurrentEnrollment(courseId);
        int finalRowCount          = countEnrollmentsForCourse(courseId);
        assertThat(finalCurrentEnrollment)
                .as("DB courses.current_enrollment == 5").isEqualTo(5);
        assertThat(finalRowCount)
                .as("DB enrollments row 수 == 5").isEqualTo(5);

        // ── metrics ─────────────────────────────────────────────────────────
        Map<ErrorCode, Integer> failureMap = new HashMap<>();
        failureByCode.forEach((k, v) -> failureMap.put(k, v.get()));
        printMetrics(new TestMetrics(
                "테스트 3 — 중복신청 + 정원경쟁 혼합 (정원 5, 동시 20명)",
                threadCount, totalElapsedMs, latencies,
                successCount.get(), failureMap,
                unexpectedExceptions.get(),
                finalCurrentEnrollment, finalRowCount
        ));
    }
}
