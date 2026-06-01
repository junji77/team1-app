package com.ops.app.courseregistration.course.controller;

import com.ops.app.courseregistration.enrollment.service.EnrollmentPeriodValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ops.app.courseregistration.course.dto.CourseResponseDto;
import com.ops.app.courseregistration.course.service.CourseService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentPeriodValidator periodValidator;

    @GetMapping("/courses")
    public String searchCourses(
            @RequestParam(name = "name", required = false) String keyword,
            @PageableDefault(page = 0, size = 20, sort = "courseCode", direction = Sort.Direction.ASC) Pageable pageable,
            Model model
    ) {
        Page<CourseResponseDto> courses = courseService.searchCoursesByName(keyword, pageable);
      
        model.addAttribute("courses", courses.getContent());
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", courses.getNumber()); 
        model.addAttribute("totalPages", courses.getTotalPages());
        model.addAttribute("totalElements", courses.getTotalElements());
        model.addAttribute("enrollmentOpen", periodValidator.isOpen());
        
        return "courses";
    }
}