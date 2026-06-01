package com.ops.app.courseregistration.enrollment.repository;

import com.ops.app.courseregistration.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // 내 수강 내역 조회 — course를 JOIN FETCH로 한 번에 가져와 N+1 방지
    // WHERE student_id = ? ORDER BY created_at DESC
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course " +
            "WHERE e.student.studentId = :studentId " +
            "ORDER BY e.createdAt DESC")
    List<Enrollment> findByStudentIdWithCourse(@Param("studentId") Long studentId);

    // 수강 취소 시 소유자 검증용 — enrollment_id AND student_id 동시 조건
    // 결과 없으면 → 존재하지 않거나 다른 학생 것 → 둘 다 404로 처리 (정보 노출 방지)
    Optional<Enrollment> findByEnrollmentIdAndStudentStudentId(Long enrollmentId, Long studentId);

    boolean existsByStudentStudentIdAndCourseCourseId(Long studentId, Long courseId);
}