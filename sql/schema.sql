-- grad_project schema
-- 9 tables: students, course_types, courses, interactions,
--           users, user_student_map, study_plans, wrong_questions, course_questions

CREATE DATABASE IF NOT EXISTS grad_project
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE grad_project;

-- 学生表 (来自 data.csv)
CREATE TABLE IF NOT EXISTS students (
    stu_id VARCHAR(64) PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 课程类别表
CREATE TABLE IF NOT EXISTS course_types (
    type_id   INT PRIMARY KEY,
    type_name VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 课程表 (name 列，匹配 data.csv)
CREATE TABLE IF NOT EXISTS courses (
    course_index INT PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    type         VARCHAR(128) DEFAULT NULL,
    type_id      INT DEFAULT NULL,
    url          VARCHAR(512) DEFAULT NULL,
    FOREIGN KEY (type_id) REFERENCES course_types(type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 交互记录表
CREATE TABLE IF NOT EXISTS interactions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    stu_id       VARCHAR(64) NOT NULL,
    time         DATETIME    NOT NULL,
    course_index INT         NOT NULL,
    FOREIGN KEY (stu_id)       REFERENCES students(stu_id),
    FOREIGN KEY (course_index) REFERENCES courses(course_index),
    UNIQUE KEY uk_interaction (stu_id, time, course_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表 (前端注册)
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar        VARCHAR(512) DEFAULT NULL,
    major_type_id INT DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (major_type_id) REFERENCES course_types(type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-学生映射
CREATE TABLE IF NOT EXISTS user_student_map (
    user_id BIGINT      NOT NULL,
    stu_id  VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, stu_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (stu_id)  REFERENCES students(stu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学习计划
CREATE TABLE IF NOT EXISTS study_plans (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    target_date DATE DEFAULT NULL,
    status      VARCHAR(32) DEFAULT 'pending',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 错题本
CREATE TABLE IF NOT EXISTS wrong_questions (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    question_id    BIGINT DEFAULT NULL,
    question_text  TEXT,
    course_name    VARCHAR(255) DEFAULT NULL,
    your_answer    VARCHAR(255) DEFAULT NULL,
    correct_answer VARCHAR(255) DEFAULT NULL,
    error_count    INT DEFAULT 1,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理员表
CREATE TABLE IF NOT EXISTS admins (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 题库
CREATE TABLE IF NOT EXISTS course_questions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id    INT          NOT NULL,
    course_name  VARCHAR(255) DEFAULT NULL,
    question     TEXT         NOT NULL,
    options      JSON         DEFAULT NULL,
    answer       VARCHAR(255) NOT NULL,
    explanation  TEXT,
    FOREIGN KEY (course_id) REFERENCES courses(course_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
