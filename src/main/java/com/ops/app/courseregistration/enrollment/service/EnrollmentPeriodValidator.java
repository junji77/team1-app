package com.ops.app.courseregistration.enrollment.service;

import com.ops.app.courseregistration.global.exception.BusinessException;
import com.ops.app.courseregistration.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class EnrollmentPeriodValidator {

    // 수강신청 가능 시간대
    private static final LocalTime[][] PERIODS = {
            { LocalTime.of(9, 30),  LocalTime.of(19, 0) }
           /* { LocalTime.of(10, 0),  LocalTime.of(10, 30) },
            { LocalTime.of(14, 0),  LocalTime.of(14, 30) },
            { LocalTime.of(16, 0),  LocalTime.of(16, 30) }*/
    };

    // 서버 운영 시간
    private static final LocalTime SERVER_OPEN  = LocalTime.of(9, 30);
    private static final LocalTime SERVER_CLOSE = LocalTime.of(19, 0);

    public void validate() {
        LocalTime now = LocalTime.now();

        // 서버 운영 시간 외
        if (now.isBefore(SERVER_OPEN) || now.isAfter(SERVER_CLOSE)) {
            throw new BusinessException(ErrorCode.SERVER_CLOSED);
        }

        // 수강신청 가능 시간대 체크
        for (LocalTime[] period : PERIODS) {
            if (!now.isBefore(period[0]) && now.isBefore(period[1])) {
                return; // 가능한 시간대 맞으면 통과
            }
        }

        throw new BusinessException(ErrorCode.ENROLLMENT_NOT_OPEN);
    }

    // CourseController에서 호출 — 버튼 활성화 여부 반환
    public boolean isOpen() {
        LocalTime now = LocalTime.now();
        for (LocalTime[] period : PERIODS) {
            if (!now.isBefore(period[0]) && now.isBefore(period[1])) {
                return true;
            }
        }
        return false;
    }
}
