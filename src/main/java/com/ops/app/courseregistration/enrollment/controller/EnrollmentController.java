package com.ops.app.courseregistration.enrollment.controller;

import com.ops.app.courseregistration.enrollment.entity.Enrollment;
import com.ops.app.courseregistration.enrollment.service.EnrollmentService;
import com.ops.app.courseregistration.global.exception.BusinessException;
import com.ops.app.courseregistration.security.StudentPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * GET /enrollments — 내 수강 내역 페이지
     */
    @GetMapping("/enrollments")
    public String myEnrollments(@AuthenticationPrincipal StudentPrincipal principal,
                                Model model) {
        Long studentId = principal.getStudentId();

        List<Enrollment> enrollments = enrollmentService.getMyEnrollments(studentId);

        model.addAttribute("enrollments", enrollments);
        model.addAttribute("totalCredits", enrollmentService.getTotalCredits(studentId));
        model.addAttribute("studentName", principal.getName());

        return "enrollments";
    }

    /**
     * POST /enrollments — 수강신청
     * - 처리 후 redirect → 브라우저 새로고침 중복 신청 방지
     */
    @PostMapping("/enrollments")
    public String enroll(@RequestParam Long courseId,
                         @AuthenticationPrincipal StudentPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        try {
            enrollmentService.enroll(principal.getStudentId(), courseId);
            redirectAttributes.addFlashAttribute("successMessage", "수강신청이 완료되었습니다.");
        } catch (BusinessException e) {
            // COURSE_FULL, DUPLICATE_ENROLLMENT, COURSE_NOT_FOUND 모두 여기서 처리
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/enrollments";
    }

    /**
     * POST /enrollments/{enrollmentId}/cancel — 수강 취소
     * - 처리 후 redirect
     * - 본인 것이 아니면 Service에서 ENROLLMENT_NOT_FOUND 던짐
     */
    @PostMapping("/enrollments/{enrollmentId}/cancel")
    public String cancel(@PathVariable Long enrollmentId,
                         @AuthenticationPrincipal StudentPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        try {
            enrollmentService.cancel(enrollmentId, principal.getStudentId());
            redirectAttributes.addFlashAttribute("successMessage", "수강이 취소되었습니다.");
        } catch (BusinessException e) {
            // ENROLLMENT_NOT_FOUND 처리
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/enrollments";
    }
}