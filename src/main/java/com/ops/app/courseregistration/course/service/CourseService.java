package com.ops.app.courseregistration.course.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ops.app.courseregistration.course.dto.CourseResponseDto;
import com.ops.app.courseregistration.course.repository.CourseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    public Page<CourseResponseDto> searchCoursesByName(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return courseRepository.findAll(pageable)
                    .map(CourseResponseDto::from);
        }

        return courseRepository.findByCourseNameStartingWith(keyword, pageable)
                .map(CourseResponseDto::from);
    }
}