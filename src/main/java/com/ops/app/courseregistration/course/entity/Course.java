package com.ops.app.courseregistration.course.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 기본 생성자 생성 방지 (JPA 필수 사양)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_code", nullable = false, unique = true, length = 20)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    @Column(name = "course_type", nullable = false)
    private String courseType;

    @Column(name = "credits", nullable = false)
    private Integer credits;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_enrollment", nullable = false)
    private Integer currentEnrollment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Course(String courseCode, String courseName, String courseType, Integer credits, Integer capacity, Integer currentEnrollment) {
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.courseType = courseType;
        this.credits = credits;
        this.capacity = capacity;
        this.currentEnrollment = currentEnrollment;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
