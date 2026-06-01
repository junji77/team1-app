package com.ops.app.courseregistration.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public String handleBadCredentials(BadCredentialsException e) {
        log.info("Login failed: {}", e.getMessage());
        return "redirect:/login?error=true";
    }

    /**
     * 비즈니스 예외(정원 마감/중복 신청/강의 없음/시간외 등)를 302 redirect로 처리.
     *
     * 목적: 정상적인 비즈니스 거절이 처리기 없이 500(5xx)으로 떨어지면
     *       "에러율(5xx)" 메트릭이 오염되고 HighErrorRate 알림이 오발화한다.
     *       302로 빼서 5xx 지표를 진짜 서버 장애 전용으로 유지한다.
     *
     * 비고(팀 TBD): 에러 표시 UX는 후순위 결정 사항.
     *   - 현재는 단순히 /courses로 redirect + flash attribute 전달.
     *   - 취소 실패는 /enrollments가 더 자연스러우므로, 추후 요청 경로/Referer 기반 분기 가능.
     *   - 화면 표시는 템플릿에서 ${errorMessage} 사용.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, RedirectAttributes ra) {
        log.info("Business rejection: {}", e.getErrorCode());
        ra.addFlashAttribute("errorCode", e.getErrorCode().name());
        ra.addFlashAttribute("errorMessage", e.getErrorCode().getMessage());
        return "redirect:/courses";
    }
}