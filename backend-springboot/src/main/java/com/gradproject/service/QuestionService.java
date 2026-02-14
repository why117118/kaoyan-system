package com.gradproject.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {
    private final JdbcTemplate jdbcTemplate;

    public QuestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listQuestions(int courseId, int limit, boolean random) {
        String orderBy = random ? "RAND()" : "q.id DESC";
        String sql = "SELECT q.id, q.question, q.options, q.answer, q.explanation, q.course_name "
            + "FROM course_questions q WHERE q.course_id = ? ORDER BY " + orderBy + " LIMIT ?";
        return jdbcTemplate.query(sql,
            ps -> { ps.setInt(1, courseId); ps.setInt(2, limit); },
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("question", rs.getString("question"));
                map.put("options", rs.getString("options"));
                map.put("answer", rs.getString("answer"));
                map.put("explanation", rs.getString("explanation"));
                map.put("course_name", rs.getString("course_name"));
                return map;
            }
        );
    }

    public List<Map<String, Object>> listQuestionsByTypeIds(List<Integer> typeIds, int limit, boolean random) {
        if (typeIds == null || typeIds.isEmpty()) return Collections.emptyList();
        String orderBy = random ? "RAND()" : "q.id DESC";
        String placeholders = String.join(",", Collections.nCopies(typeIds.size(), "?"));
        String sql = "SELECT q.id, q.question, q.options, q.answer, q.explanation, q.course_name "
            + "FROM course_questions q "
            + "JOIN courses c ON q.course_id = c.course_index "
            + "WHERE c.type_id IN (" + placeholders + ") ORDER BY " + orderBy + " LIMIT ?";

        List<Object> params = new ArrayList<>(typeIds);
        params.add(limit);
        return jdbcTemplate.query(sql, params.toArray(),
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("question", rs.getString("question"));
                map.put("options", rs.getString("options"));
                map.put("answer", rs.getString("answer"));
                map.put("explanation", rs.getString("explanation"));
                map.put("course_name", rs.getString("course_name"));
                return map;
            }
        );
    }
}
