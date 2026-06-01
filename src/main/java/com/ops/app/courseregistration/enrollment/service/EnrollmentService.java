package com.ops.app.courseregistration.enrollment.service;

import com.ops.app.courseregistration.course.entity.Course;
import com.ops.app.courseregistration.course.repository.CourseRepository;
import com.ops.app.courseregistration.enrollment.entity.Enrollment;
import com.ops.app.courseregistration.enrollment.repository.EnrollmentRepository;
import com.ops.app.courseregistration.global.exception.BusinessException;
import com.ops.app.courseregistration.global.exception.ErrorCode;
import com.ops.app.courseregistration.student.entity.Student;
import com.ops.app.courseregistration.student.repository.StudentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentPeriodValidator periodValidator;
    private final MeterRegistry meterRegistry;


    // 내 수강 내역 조회
    @Transactional(readOnly = true)
    public List<Enrollment> getMyEnrollments(Long studentId) {
        return enrollmentRepository.findByStudentIdWithCourse(studentId);
    }


    // 총 신청 학점 계산
    @Transactional(readOnly = true)
    public int getTotalCredits(Long studentId) {
        return getMyEnrollments(studentId).stream()
                .mapToInt(e -> e.getCourse().getCredits())
                .sum();
    }

    /**
     * 수강신청
     *
     * [트랜잭션 흐름]
     * Step 1. UPDATE courses SET current_enrollment+1 WHERE current_enrollment < capacity
     *         → 0 rows affected : 정원 초과 → COURSE_FULL (롤백)
     *         → 1 rows affected : 자리 확보, 다음 단계 진행
     * Step 2. INSERT enrollments
     *         → UNIQUE 위반 : 중복 신청 → DUPLICATE_ENROLLMENT (롤백 → Step1 UPDATE도 취소)
     *         → 성공 : COMMIT
     *
     * [동시성] InnoDB row-level lock이 Step1 UPDATE를 직렬화.
     *
     * [도메인 메트릭]
     * enrollment.attempts{result}                    : 결과별 시도 횟수
     * enrollment.attempts.by_course{course_id,course_name} : 강의별 시도 횟수 (동시신청 Top-N용)
     * enrollment.duration{result}                    : 결과별 처리 시간 (p95는 percentiles-histogram 활성화 시)
     * 실패는 모두 BusinessException(ErrorCode)이므로 ErrorCode 이름을 result 라벨로 사용.
     * 메트릭은 in-memory라 롤백과 무관하며, 예외는 그대로 재던져 롤백 의미를 유지.
     */
    @Transactional
    public void enroll(Long studentId, Long courseId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        String courseName = "unknown";   // 메트릭 라벨용 (존재하지 않는 강의/시간외 거절 시 unknown 유지)
        try {
            periodValidator.validate();

            // 강의 존재 여부 확인 — 없으면 COURSE_NOT_FOUND. 여기서 이름도 확보.
            Course found = courseRepository.findById(courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
            courseName = found.getCourseName();

            // Step 1. 정원 체크 + 카운터 원자적 증가
            int updated = courseRepository.incrementEnrollmentCount(courseId);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.COURSE_FULL);
            }

            // Step 2. 프록시 참조로 불필요한 SELECT 없이 INSERT
            Student student = studentRepository.getReferenceById(studentId);
            Course course   = courseRepository.getReferenceById(courseId);

            try {
                enrollmentRepository.saveAndFlush(new Enrollment(student, course));
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
            }
        } catch (BusinessException e) {
            result = e.getErrorCode().name().toLowerCase();
            throw e;
        } catch (RuntimeException e) {
            result = "error";
            throw e;
        } finally {
            meterRegistry.counter("enrollment.attempts", "result", result).increment();
            meterRegistry.counter("enrollment.attempts.by_course",
                    "course_id", String.valueOf(courseId),
                    "course_name", courseName).increment();
            sample.stop(meterRegistry.timer("enrollment.duration", "result", result));
        }
    }

    /**
     * 수강 취소
     * Step 1. enrollment_id + student_id 조회 (소유자 검증 + 존재 확인) → 없으면 ENROLLMENT_NOT_FOUND
     * Step 2. DELETE → flush
     * Step 3. UPDATE courses SET current_enrollment-1 WHERE current_enrollment > 0 (음수 방어)
     */
    @Transactional
    public void cancel(Long enrollmentId, Long studentId) {
        Enrollment enrollment = enrollmentRepository
                .findByEnrollmentIdAndStudentStudentId(enrollmentId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        Long courseId = enrollment.getCourse().getCourseId();

        enrollmentRepository.delete(enrollment);
        enrollmentRepository.flush();

        courseRepository.decrementEnrollmentCount(courseId);
    }
}