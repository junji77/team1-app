package com.ops.app.courseregistration.auth.service;

import com.ops.app.courseregistration.auth.jwt.JwtUtil;
import com.ops.app.courseregistration.global.exception.BusinessException;
import com.ops.app.courseregistration.global.exception.ErrorCode;
import com.ops.app.courseregistration.student.entity.Student;
import com.ops.app.courseregistration.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public String login(String email, String password) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, student.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return jwtUtil.generateToken(student.getStudentId());
    }
}
