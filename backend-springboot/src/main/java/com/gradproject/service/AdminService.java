package com.gradproject.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 首次启动时，若 admins 表为空则创建默认管理员 admin / admin123 */
    @PostConstruct
    public void initDefaultAdmin() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admins", Integer.class);
        if (count == null || count == 0) {
            String hash = passwordEncoder.encode("admin123");
            jdbcTemplate.update("INSERT INTO admins (username, password_hash) VALUES (?, ?)", "admin", hash);
        }
    }

    // ==================== 管理员登录 ====================

    public Optional<Map<String, Object>> login(String username, String password) {
        return jdbcTemplate.query(
            "SELECT id, username, password_hash FROM admins WHERE username = ?",
            rs -> {
                if (!rs.next()) return Optional.<Map<String, Object>>empty();
                String hash = rs.getString("password_hash");
                if (!passwordEncoder.matches(password, hash)) return Optional.empty();
                Map<String, Object> admin = new HashMap<>();
                admin.put("id", rs.getLong("id"));
                admin.put("username", rs.getString("username"));
                admin.put("role", "admin");
                return Optional.of(admin);
            }, username
        );
    }

    // ==================== 用户管理 ====================

    public Map<String, Object> listUsers(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE u.username LIKE ? ";
            params.add("%" + keyword + "%");
        }
        String countSql = "SELECT COUNT(*) FROM users u" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);

        String dataSql = """
            SELECT u.id, u.username, u.avatar, u.major_type_id, t.type_name, u.created_at
            FROM users u LEFT JOIN course_types t ON u.major_type_id = t.type_id
            """ + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("username", rs.getString("username"));
                m.put("avatar", rs.getString("avatar"));
                m.put("majorTypeId", rs.getObject("major_type_id"));
                m.put("typeName", rs.getString("type_name"));
                m.put("createdAt", rs.getString("created_at"));
                return m;
            }
        );
        return buildPage(list, total, page, size);
    }

    public void updateUser(long userId, String username, Integer majorTypeId) {
        jdbcTemplate.update("UPDATE users SET username = ?, major_type_id = ? WHERE id = ?",
            username, majorTypeId, userId);
    }

    public void deleteUser(long userId) {
        jdbcTemplate.update("DELETE FROM wrong_questions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM study_plans WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_student_map WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    // ==================== 题库管理 ====================

    public Map<String, Object> listAllQuestions(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE (q.question LIKE ? OR q.course_name LIKE ?) ";
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        String countSql = "SELECT COUNT(*) FROM course_questions q" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);

        String dataSql = """
            SELECT q.id, q.course_id, q.course_name, q.question, q.options, q.answer, q.explanation
            FROM course_questions q
            """ + where + " ORDER BY q.id DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("courseId", rs.getInt("course_id"));
                m.put("courseName", rs.getString("course_name"));
                m.put("question", rs.getString("question"));
                m.put("options", rs.getString("options"));
                m.put("answer", rs.getString("answer"));
                m.put("explanation", rs.getString("explanation"));
                return m;
            }
        );
        return buildPage(list, total, page, size);
    }

    public void addQuestion(int courseId, String courseName, String question,
                            String options, String answer, String explanation) {
        jdbcTemplate.update(
            "INSERT INTO course_questions (course_id, course_name, question, options, answer, explanation) VALUES (?, ?, ?, ?, ?, ?)",
            courseId, courseName, question, options, answer, explanation
        );
    }

    public void updateQuestion(long id, String question, String options, String answer, String explanation) {
        jdbcTemplate.update(
            "UPDATE course_questions SET question = ?, options = ?, answer = ?, explanation = ? WHERE id = ?",
            question, options, answer, explanation, id
        );
    }

    public void deleteQuestion(long id) {
        jdbcTemplate.update("DELETE FROM course_questions WHERE id = ?", id);
    }

    // ==================== 错题管理 ====================

    public Map<String, Object> listAllWrongQuestions(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE (w.question_text LIKE ? OR w.course_name LIKE ? OR u.username LIKE ?) ";
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        String countSql = "SELECT COUNT(*) FROM wrong_questions w LEFT JOIN users u ON w.user_id = u.id" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);

        String dataSql = """
            SELECT w.id, w.user_id, u.username, w.question_id, w.question_text,
                   w.course_name, w.your_answer, w.correct_answer, w.error_count, w.created_at
            FROM wrong_questions w LEFT JOIN users u ON w.user_id = u.id
            """ + where + " ORDER BY w.id DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("userId", rs.getLong("user_id"));
                m.put("username", rs.getString("username"));
                m.put("questionId", rs.getObject("question_id"));
                m.put("questionText", rs.getString("question_text"));
                m.put("courseName", rs.getString("course_name"));
                m.put("yourAnswer", rs.getString("your_answer"));
                m.put("correctAnswer", rs.getString("correct_answer"));
                m.put("errorCount", rs.getInt("error_count"));
                m.put("createdAt", rs.getString("created_at"));
                return m;
            }
        );
        return buildPage(list, total, page, size);
    }

    public void deleteWrongQuestion(long id) {
        jdbcTemplate.update("DELETE FROM wrong_questions WHERE id = ?", id);
    }

    // ==================== 学习计划管理 ====================

    public Map<String, Object> listAllPlans(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE (p.title LIKE ? OR u.username LIKE ?) ";
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        String countSql = "SELECT COUNT(*) FROM study_plans p LEFT JOIN users u ON p.user_id = u.id" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);

        String dataSql = """
            SELECT p.id, p.user_id, u.username, p.title, p.description,
                   p.target_date, p.status, p.created_at
            FROM study_plans p LEFT JOIN users u ON p.user_id = u.id
            """ + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("userId", rs.getLong("user_id"));
                m.put("username", rs.getString("username"));
                m.put("title", rs.getString("title"));
                m.put("description", rs.getString("description"));
                m.put("targetDate", rs.getDate("target_date") == null ? null
                    : rs.getDate("target_date").toLocalDate().toString());
                m.put("status", rs.getString("status"));
                m.put("createdAt", rs.getString("created_at"));
                return m;
            }
        );
        return buildPage(list, total, page, size);
    }

    public void updatePlan(long id, String title, String description, String targetDate, String status) {
        Date td = (targetDate == null || targetDate.isBlank()) ? null : Date.valueOf(LocalDate.parse(targetDate));
        jdbcTemplate.update(
            "UPDATE study_plans SET title = ?, description = ?, target_date = ?, status = ? WHERE id = ?",
            title, description, td, status, id
        );
    }

    public void deletePlan(long id) {
        jdbcTemplate.update("DELETE FROM study_plans WHERE id = ?", id);
    }

    // ==================== 课程链接管理 ====================

    public Map<String, Object> listCoursesAdmin(int page, int size, String keyword) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where = " WHERE c.name LIKE ? ";
            params.add("%" + keyword + "%");
        }
        String countSql = "SELECT COUNT(*) FROM courses c" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);

        String dataSql = """
            SELECT c.course_index, c.name, c.type, c.type_id, t.type_name, c.url
            FROM courses c LEFT JOIN course_types t ON c.type_id = t.type_id
            """ + where + " ORDER BY c.course_index LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add(offset);

        List<Map<String, Object>> list = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("courseIndex", rs.getInt("course_index"));
                m.put("name", rs.getString("name"));
                m.put("type", rs.getString("type"));
                m.put("typeId", rs.getInt("type_id"));
                m.put("typeName", rs.getString("type_name"));
                m.put("url", rs.getString("url"));
                return m;
            }
        );
        return buildPage(list, total, page, size);
    }

    public void updateCourseUrl(int courseIndex, String url) {
        jdbcTemplate.update("UPDATE courses SET url = ? WHERE course_index = ?", url, courseIndex);
    }

    // ==================== 辅助 ====================

    private Map<String, Object> buildPage(List<?> content, long total, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("total", total);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
