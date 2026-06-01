package com.ops.app.courseregistration.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),

    // Course
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 강의입니다."),
    COURSE_FULL(HttpStatus.CONFLICT, "수강 정원이 초과되었습니다."),

    // Enrollment
    DUPLICATE_ENROLLMENT(HttpStatus.CONFLICT, "이미 신청한 강의입니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 내역을 찾을 수 없습니다."),
    ENROLLMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "본인의 수강 내역만 취소할 수 있습니다."),
    ENROLLMENT_NOT_OPEN(HttpStatus.FORBIDDEN, "수강신청 가능 시간이 아닙니다."),
    SERVER_CLOSED(HttpStatus.FORBIDDEN, "서버 운영 시간이 아닙니다. (9:30 ~ 16:30)");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}