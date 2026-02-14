package com.gradproject.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gradproject.model.WrongQuestion;
import com.gradproject.model.WrongQuestionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WrongQuestionService {
    private final JdbcTemplate jdbcTemplate;

    public WrongQuestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WrongQuestion> list(long userId, String keyword, Integer courseId) {
        StringBuilder sql = new StringBuilder("""
            SELECT w.id, w.user_id, w.question_id, w.question_text,
                   w.course_name, w.your_answer, w.correct_answer, w.error_count
            FROM wrong_questions w
            WHERE w.user_id = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (w.question_text LIKE ? OR w.course_name LIKE ?) ");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY w.id DESC");

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new WrongQuestion(
                rs.getLong("id"), rs.getLong("user_id"),
                (Long) rs.getObject("question_id"), rs.getString("question_text"),
                rs.getString("course_name"), rs.getString("your_answer"),
                rs.getString("correct_answer"), rs.getInt("error_count")
            )
        );
    }

    /**
     * Insert a wrong-question record. If the same question already exists for this user,
     * increment error_count instead. Returns the current error_count.
     */
    public int create(WrongQuestionRequest request) {
        // Check if already exists
        Integer existing = null;
        if (request.getQuestionId() != null) {
            existing = jdbcTemplate.query(
                "SELECT id, error_count FROM wrong_questions WHERE user_id = ? AND question_id = ? LIMIT 1",
                rs -> rs.next() ? rs.getInt("error_count") : null,
                request.getUserId(), request.getQuestionId()
            );
        }
        if (existing == null && request.getQuestionText() != null && !request.getQuestionText().isBlank()) {
            existing = jdbcTemplate.query(
                "SELECT id, error_count FROM wrong_questions WHERE user_id = ? AND question_text = ? LIMIT 1",
                rs -> {
                    if (!rs.next()) return null;
                    // Update error_count
                    long rowId = rs.getLong("id");
                    int ec = rs.getInt("error_count");
                    jdbcTemplate.update("UPDATE wrong_questions SET error_count = ? WHERE id = ?", ec + 1, rowId);
                    return ec + 1;
                },
                request.getUserId(), request.getQuestionText()
            );
        }
        if (existing != null) return existing;

        // New record
        jdbcTemplate.update(
            "INSERT INTO wrong_questions (user_id, question_id, question_text, course_name, your_answer, correct_answer, error_count) VALUES (?, ?, ?, ?, ?, ?, 1)",
            request.getUserId(), request.getQuestionId(), request.getQuestionText(),
            request.getCourseName(), request.getYourAnswer(), request.getCorrectAnswer()
        );
        return 1;
    }

    /**
     * Count how many times a user got a specific question wrong.
     */
    public int countForQuestion(long userId, Long questionId, String questionText) {
        if (questionId != null) {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COALESCE(error_count, 1) FROM wrong_questions WHERE user_id = ? AND question_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class, userId, questionId
            );
            return cnt == null ? 0 : cnt;
        }
        if (questionText != null && !questionText.isBlank()) {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COALESCE(error_count, 1) FROM wrong_questions WHERE user_id = ? AND question_text = ? ORDER BY id DESC LIMIT 1",
                Integer.class, userId, questionText
            );
            return cnt == null ? 0 : cnt;
        }
        return 0;
    }

    public void delete(long id, long userId) {
        jdbcTemplate.update("DELETE FROM wrong_questions WHERE id = ? AND user_id = ?", id, userId);
    }

    public Map<String, Object> listPaged(long userId, List<Integer> typeIds, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder("WHERE w.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (typeIds != null && !typeIds.isEmpty()) {
            String ph = String.join(",", Collections.nCopies(typeIds.size(), "?"));
            where.append(" AND c.type_id IN (").append(ph).append(")");
            params.addAll(typeIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (w.question_text LIKE ? OR w.course_name LIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
        }

        String joinClause = (typeIds != null && !typeIds.isEmpty())
            ? "JOIN courses c ON w.course_name = c.name" : "LEFT JOIN courses c ON w.course_name = c.name";

        String countSql = "SELECT COUNT(DISTINCT w.id) FROM wrong_questions w " + joinClause + " " + where;
        int total = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
        int totalPages = Math.max(1, (int) Math.ceil((double) total / size));

        String dataSql = "SELECT w.id, w.user_id, w.question_id, w.question_text, " +
            "w.course_name, w.your_answer, w.correct_answer, w.error_count " +
            "FROM wrong_questions w " + joinClause + " " + where +
            " GROUP BY w.id ORDER BY w.id DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add((page - 1) * size);

        List<Map<String, Object>> items = jdbcTemplate.query(dataSql, dataParams.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("user_id", rs.getLong("user_id"));
                m.put("question_id", rs.getObject("question_id"));
                m.put("question_text", rs.getString("question_text"));
                m.put("course_name", rs.getString("course_name"));
                m.put("your_answer", rs.getString("your_answer"));
                m.put("correct_answer", rs.getString("correct_answer"));
                m.put("error_count", rs.getInt("error_count"));
                return m;
            }
        );

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("page", page);
        return result;
    }

    public List<Map<String, Object>> listByTypeIds(long userId, List<Integer> typeIds, int limit) {
        if (typeIds == null || typeIds.isEmpty()) return Collections.emptyList();
        String placeholders = String.join(",", Collections.nCopies(typeIds.size(), "?"));
        String sql = """
            SELECT w.id, w.question_text, w.error_count, w.course_name
            FROM wrong_questions w
            JOIN courses c ON w.course_name = c.name
            WHERE w.user_id = ? AND c.type_id IN (%s)
            GROUP BY w.id
            ORDER BY RAND() LIMIT ?
            """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(typeIds);
        params.add(limit);
        return jdbcTemplate.query(sql, params.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("question_text", rs.getString("question_text"));
                map.put("error_count", rs.getInt("error_count"));
                map.put("course_name", rs.getString("course_name"));
                return map;
            }
        );
    }
}
