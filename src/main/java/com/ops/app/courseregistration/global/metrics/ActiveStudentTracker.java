package com.ops.app.courseregistration.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "현재 활성 학생 수" 근사 지표.
 *
 * 노출 메트릭:
 *   active_students  — 최근 5분 내 인증된 요청을 보낸 고유 학생 수
 *
 * 동작:
 *   - 인증 통과 시점(JwtAuthenticationFilter)에서 touch(studentId) 호출 → 마지막 활동 시각 기록
 *   - 30초마다 5분 지난 학생 제거(evict)
 *   - gauge는 맵 크기를 노출
 *
 * ⚠️ 이 앱은 stateless JWT라 "로그인 세션 수"가 존재하지 않으므로 이건 정확한 세션 수가 아니라
 *    "최근 활동 학생 수" 근사다. 또한 Pod-로컬 집계이므로 한 학생이 5분 내 여러 Pod에 걸치면
 *    중복 집계된다. replicas=1이면 정확, HPA로 scale-out되면 근사(PromQL은 sum(active_students) 사용).
 *    정확한 전역 distinct 카운트가 필요하면 Redis 등 공유 저장소가 필요.
 *
 * 전제: 메인 클래스에 @EnableScheduling.
 */
@Component
public class ActiveStudentTracker {

    private static final long WINDOW_MS = 5 * 60_000L;

    // studentId -> 마지막 활동 시각(epoch millis)
    private final Map<Long, Long> lastSeen = new ConcurrentHashMap<>();

    public ActiveStudentTracker(MeterRegistry registry) {
        Gauge.builder("active.students", lastSeen, Map::size)
                .description("최근 5분 내 인증된 요청을 보낸 고유 학생 수 (Pod 로컬)")
                .register(registry);
    }

    /** 인증 통과한 요청마다 호출 (JwtAuthenticationFilter에서). */
    public void touch(Long studentId) {
        if (studentId != null) {
            lastSeen.put(studentId, System.currentTimeMillis());
        }
    }

    @Scheduled(fixedRate = 30_000L)
    public void evict() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        lastSeen.values().removeIf(ts -> ts < cutoff);
    }
}