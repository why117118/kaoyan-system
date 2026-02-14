package com.gradproject.service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import com.gradproject.model.Course;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseService {
    private final JdbcTemplate jdbcTemplate;

    public CourseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Course> listCourses(int limit) {
        String sql = """
            SELECT c.course_index, c.name, c.type_id, t.type_name, c.url
            FROM courses c
            LEFT JOIN course_types t ON c.type_id = t.type_id
            ORDER BY c.course_index LIMIT ?
            """;
        return jdbcTemplate.query(sql,
            ps -> ps.setInt(1, limit),
            (rs, rowNum) -> new Course(
                rs.getInt("course_index"), rs.getString("name"),
                rs.getInt("type_id"), rs.getString("type_name"),
                rs.getString("url")
            )
        );
    }

    public Map<String, Object> listCoursesPaged(int page, int size, String keyword, String mode) {
        int offset = (page - 1) * size;
        String where = "";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            if ("type".equalsIgnoreCase(mode)) {
                where = " WHERE t.type_name LIKE ? ";
            } else {
                where = " WHERE c.name LIKE ? ";
            }
            params.add("%" + keyword + "%");
        }

        String countSql = "SELECT COUNT(*) FROM courses c LEFT JOIN course_types t ON c.type_id = t.type_id" + where;
        long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
        int totalPages = (int) Math.ceil((double) total / size);

        String dataSql = """
            SELECT c.course_index, c.name, c.type_id, t.type_name, c.url
            FROM courses c
            LEFT JOIN course_types t ON c.type_id = t.type_id
            """ + where + " ORDER BY c.course_index LIMIT ? OFFSET ?";
        params.add(size);
        params.add(offset);
        List<Course> content = jdbcTemplate.query(dataSql, params.toArray(),
            (rs, rowNum) -> new Course(
                rs.getInt("course_index"), rs.getString("name"),
                rs.getInt("type_id"), rs.getString("type_name"),
                rs.getString("url")
            )
        );

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalPages", totalPages);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public String findRecentTypeName(long userId) {
        String sql = """
            SELECT t.type_name
            FROM interactions i
            JOIN courses c ON i.course_index = c.course_index
            LEFT JOIN course_types t ON c.type_id = t.type_id
            JOIN user_student_map m ON m.stu_id = i.stu_id
            WHERE m.user_id = ?
            ORDER BY i.time DESC LIMIT 1
            """;
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString("type_name") : null, userId);
    }

    @Cacheable(value = "courseTypes", key = "#excludeKeywords != null ? #excludeKeywords.toString() : 'all'")
    public List<Map<String, Object>> listCourseTypes(List<String> excludeKeywords) {
        List<Map<String, Object>> types = jdbcTemplate.query(
            "SELECT type_id, type_name FROM course_types ORDER BY type_id",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("type_id", rs.getInt("type_id"));
                map.put("type_name", rs.getString("type_name"));
                return map;
            }
        );
        if (excludeKeywords == null || excludeKeywords.isEmpty()) return types;
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> type : types) {
            String name = String.valueOf(type.get("type_name"));
            boolean excluded = false;
            for (String kw : excludeKeywords) {
                if (name.contains(kw)) { excluded = true; break; }
            }
            if (!excluded) filtered.add(type);
        }
        return filtered;
    }

    @Cacheable(value = "typeIdsByKeyword", key = "#keyword")
    public List<Integer> findTypeIdsByKeyword(String keyword) {
        return jdbcTemplate.query(
            "SELECT type_id FROM course_types WHERE type_name LIKE ?",
            ps -> ps.setString(1, "%" + keyword + "%"),
            (rs, rowNum) -> rs.getInt("type_id")
        );
    }

    @Cacheable(value = "typeNameById", key = "#typeId", condition = "#typeId != null")
    public String findTypeNameById(Integer typeId) {
        if (typeId == null) return null;
        return jdbcTemplate.query(
            "SELECT type_name FROM course_types WHERE type_id = ?",
            rs -> rs.next() ? rs.getString("type_name") : null, typeId
        );
    }

    /**
     * Record a user's click on a course into the interactions table.
     * Uses INSERT IGNORE to avoid duplicate key errors.
     */
    public void recordInteraction(String stuId, int courseIndex) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO interactions (stu_id, time, course_index) VALUES (?, NOW(), ?)",
            stuId, courseIndex
        );
    }

    /**
     * Return most popular courses (by interaction count) limited to given type_ids.
     * Used as a fallback when the recommender returns no courses in allowed categories.
     */
    public List<Map<String, Object>> findPopularByTypeIds(java.util.Set<Integer> typeIds, int limit) {
        if (typeIds == null || typeIds.isEmpty()) return new ArrayList<>();
        String placeholders = String.join(",", typeIds.stream().map(id -> "?").toList());
        String sql = """
            SELECT c.course_index, c.name, t.type_name, c.type_id,
                   COUNT(i.id) AS popularity
            FROM courses c
            JOIN course_types t ON c.type_id = t.type_id
            LEFT JOIN interactions i ON c.course_index = i.course_index
            WHERE c.type_id IN (%s)
            GROUP BY c.course_index, c.name, t.type_name, c.type_id
            ORDER BY popularity DESC
            LIMIT ?
            """.formatted(placeholders);
        List<Object> params = new ArrayList<>(typeIds.stream().map(id -> (Object) id).toList());
        params.add(limit);
        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("course_index", rs.getInt("course_index"));
            m.put("name", rs.getString("name"));
            m.put("type_name", rs.getString("type_name"));
            m.put("type_id", rs.getInt("type_id"));
            m.put("predicted_score", 0.5);
            m.put("reason", "热门课程推荐");
            return m;
        });
    }
}
