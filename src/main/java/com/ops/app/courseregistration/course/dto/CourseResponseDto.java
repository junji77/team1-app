package com.ops.app.courseregistration.course.dto;

import com.ops.app.courseregistration.course.entity.Course;

public record CourseResponseDto(
        Long courseId,
        String courseCode,
        String courseName,
        String courseType,
        int credits,
        int capacity,
        int currentEnrollment
) {
    public static CourseResponseDto from(Course course) {
        return new CourseResponseDto(
                course.getCourseId(),
                course.getCourseCode(),
                course.getCourseName(),
                course.getCourseType(),
                course.getCredits(),
                course.getCapacity(),
                course.getCurrentEnrollment()
        );
    }
}
