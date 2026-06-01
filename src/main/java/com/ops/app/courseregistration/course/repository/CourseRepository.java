package com.ops.app.courseregistration.course.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ops.app.courseregistration.course.entity.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findByCourseNameStartingWith(String courseName, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Course c SET c.currentEnrollment = c.currentEnrollment + 1 " +
           "WHERE c.courseId = :courseId AND c.currentEnrollment < c.capacity")
    int incrementEnrollmentCount(@Param("courseId") Long courseId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Course c SET c.currentEnrollment = c.currentEnrollment - 1 " +
           "WHERE c.courseId = :courseId AND c.currentEnrollment > 0")
    void decrementEnrollmentCount(@Param("courseId") Long courseId);
}
