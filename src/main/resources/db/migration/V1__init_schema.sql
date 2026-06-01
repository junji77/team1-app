CREATE TABLE students (
                          student_id     BIGINT       NOT NULL AUTO_INCREMENT,
                          student_number VARCHAR(20)  NOT NULL,
                          name           VARCHAR(50)  NOT NULL,
                          grade          INT          NOT NULL,
                          email          VARCHAR(100) NOT NULL,
                          password       VARCHAR(255) NOT NULL,
                          created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
                          PRIMARY KEY (student_id),
                          UNIQUE KEY uk_students_student_number (student_number),
                          UNIQUE KEY uk_students_email          (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE courses (
                         course_id          BIGINT       NOT NULL AUTO_INCREMENT,
                         course_code        VARCHAR(20)  NOT NULL,
                         course_name        VARCHAR(100) NOT NULL,
                         course_type        VARCHAR(20)  NOT NULL,
                         credits            INT          NOT NULL,
                         capacity           INT          NOT NULL,
                         current_enrollment INT          NOT NULL DEFAULT 0,
                         created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,
                         PRIMARY KEY (course_id),
                         UNIQUE KEY uk_courses_course_code  (course_code),
                         KEY        idx_courses_course_name (course_name)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE enrollments (
                             enrollment_id BIGINT   NOT NULL AUTO_INCREMENT,
                             student_id    BIGINT   NOT NULL,
                             course_id     BIGINT   NOT NULL,
                             created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             PRIMARY KEY (enrollment_id),
                             UNIQUE KEY uk_enrollments_student_course (student_id, course_id),
                             KEY        idx_enrollments_course        (course_id),
                             CONSTRAINT fk_enrollments_student
                                 FOREIGN KEY (student_id) REFERENCES students(student_id),
                             CONSTRAINT fk_enrollments_course
                                 FOREIGN KEY (course_id)  REFERENCES courses(course_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;