package com.ops.app.courseregistration.course.metrics;

import com.ops.app.courseregistration.course.entity.Course;
import com.ops.app.courseregistration.course.repository.CourseRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 강의별 정원 상태를 주기적으로 읽어 Micrometer Gauge로 노출.
 *
 * 노출 메트릭:
 *   course_enrolled{course_id,course_name}  — 현재 신청 인원
 *   course_capacity{course_id,course_name}  — 정원
 *
 * 활용:
 *   - 정원 초과(오버부킹) 감지 : course_enrolled > course_capacity 가 0이어야 함 (동시성 제어 라이브 증명)
 *   - 전체 충원율 / 마감 강의 수 / 강의별 충원율 추이
 *
 * ⚠️ Pod마다 자기 DB 스냅샷을 보고하므로, 합산/카운트 시 PromQL에서 먼저
 *    max by (course_id)(...) 로 Pod 차원을 접어야 중복 합산을 피한다.
 *
 * 전제: 메인 클래스에 @EnableScheduling, Course에 getCourseId()/getCourseName()/
 *       getCurrentEnrollment()/getCapacity() 게터.
 */
@Component
@RequiredArgsConstructor
public class CourseStateMetrics {

    private final CourseRepository courseRepository;
    private final MeterRegistry meterRegistry;

    private final Map<Long, AtomicInteger> enrolledHolders = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> capacityHolders = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 15000)
    @Transactional(readOnly = true)
    public void refresh() {
        for (Course course : courseRepository.findAll()) {
            Long id = course.getCourseId();
            String name = course.getCourseName();

            enrolledHolders.computeIfAbsent(id, key -> {
                AtomicInteger holder = new AtomicInteger();
                Gauge.builder("course.enrolled", holder, AtomicInteger::get)
                        .tag("course_id", String.valueOf(key))
                        .tag("course_name", name)
                        .description("현재 신청 인원")
                        .register(meterRegistry);
                return holder;
            }).set(course.getCurrentEnrollment());

            capacityHolders.computeIfAbsent(id, key -> {
                AtomicInteger holder = new AtomicInteger();
                Gauge.builder("course.capacity", holder, AtomicInteger::get)
                        .tag("course_id", String.valueOf(key))
                        .tag("course_name", name)
                        .description("정원")
                        .register(meterRegistry);
                return holder;
            }).set(course.getCapacity());
        }
    }
}