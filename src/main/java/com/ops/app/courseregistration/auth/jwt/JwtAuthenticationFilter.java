package com.ops.app.courseregistration.auth.jwt;

import com.ops.app.courseregistration.global.metrics.ActiveStudentTracker;
import com.ops.app.courseregistration.security.StudentPrincipal;
import com.ops.app.courseregistration.student.repository.StudentRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtCookieUtil jwtCookieUtil;
    private final StudentRepository studentRepository;
    private final ActiveStudentTracker activeStudentTracker;   // ★ 추가

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Optional<String> tokenOpt = jwtCookieUtil.extractToken(request);

        if (tokenOpt.isEmpty() || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Long studentId = jwtUtil.extractStudentId(tokenOpt.get());
            String name = studentRepository.findById(studentId)
                    .map(s -> s.getName())
                    .orElse(null);
            StudentPrincipal principal = new StudentPrincipal(studentId, name);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            // ★ 인증 통과한 요청 → 활성 학생 지표 갱신 (active_students gauge)
            activeStudentTracker.touch(studentId);
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}