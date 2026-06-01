package com.ops.app.courseregistration.enrollment.entity;

import com.ops.app.courseregistration.course.entity.Course;
import com.ops.app.courseregistration.student.entity.Student;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollments_student_course",
                columnNames = {"student_id", "course_id"}
        ) // student_id + course_id 조합 UNIQUE → 중복 신청 DB 레벨 차단
)
@Getter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long enrollmentId;

    // 수강신청한 학생
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // 신청된 강의
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수강신청 생성자
    public Enrollment(Student student, Course course) {
        this.student = student;
        this.course = course;
        this.createdAt = LocalDateTime.now();
    }

    // INSERT 직전 자동 호출 — createdAt 세팅
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}