package com.ops.app.courseregistration.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class StudentPrincipal {
    private final Long studentId;
    private final String name;
}
