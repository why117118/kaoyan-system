package com.gradproject.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gradproject.model.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> register(String username, String password) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?",
            Integer.class, username
        );
        if (exists != null && exists > 0) return Optional.empty();

        String hash = passwordEncoder.encode(password);
        jdbcTemplate.update("INSERT INTO users (username, password_hash) VALUES (?, ?)", username, hash);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        if (id != null) {
            assignStudentId(id);
        }
        return id == null ? Optional.empty() : Optional.of(new User(id, username, null, null));
    }

    /**
     * Assign an unused stu_id to the given user.
     * Scans from stu_id=1 upward; finds the first integer NOT present in the students table,
     * inserts it into students, then maps it to the user.
     */
    public void assignStudentId(long userId) {
        // Check if already mapped
        Integer mapped = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_student_map WHERE user_id = ?", Integer.class, userId);
        if (mapped != null && mapped > 0) return;

        // Get all existing stu_ids as a set
        List<String> existingIds = jdbcTemplate.queryForList(
            "SELECT stu_id FROM students", String.class);
        java.util.Set<String> existingSet = new java.util.HashSet<>(existingIds);

        // Find first integer starting from 1 that is NOT in the students table
        String chosen = null;
        for (int n = 1; ; n++) {
            String candidate = String.valueOf(n);
            if (!existingSet.contains(candidate)) {
                chosen = candidate;
                break;
            }
        }

        // Insert the new stu_id into students table
        jdbcTemplate.update("INSERT INTO students (stu_id) VALUES (?)", chosen);
        // Map user to this stu_id
        jdbcTemplate.update("INSERT INTO user_student_map (user_id, stu_id) VALUES (?, ?)", userId, chosen);
    }

    /**
     * Get mapped stu_id for a user. Returns null if not mapped.
     */
    public String getStuId(long userId) {
        return jdbcTemplate.query(
            "SELECT stu_id FROM user_student_map WHERE user_id = ?",
            rs -> rs.next() ? rs.getString("stu_id") : null, userId);
    }

    /**
     * Ensure a mapping exists for the user; create one if missing.
     */
    public String ensureMapping(long userId) {
        String stuId = getStuId(userId);
        if (stuId == null) {
            assignStudentId(userId);
            stuId = getStuId(userId);
        }
        return stuId;
    }

    /**
     * Returns a map: {"status": "ok", "user": User} on success,
     * {"status": "user_not_found"} or {"status": "wrong_password"} on failure.
     */
    public Map<String, Object> loginDetailed(String username, String password) {
        return jdbcTemplate.query(
            "SELECT id, username, password_hash, avatar, major_type_id FROM users WHERE username = ?",
            rs -> {
                if (!rs.next()) return Map.<String, Object>of("status", "user_not_found");
                String hash = rs.getString("password_hash");
                if (!passwordEncoder.matches(password, hash)) return Map.<String, Object>of("status", "wrong_password");
                User user = new User(
                    rs.getLong("id"), rs.getString("username"),
                    rs.getString("avatar"), (Integer) rs.getObject("major_type_id")
                );
                return Map.<String, Object>of("status", "ok", "user", user);
            }, username
        );
    }

    @CacheEvict(value = "users", key = "#userId")
    public Optional<User> updateAvatar(long userId, String avatarPath) {
        jdbcTemplate.update("UPDATE users SET avatar = ? WHERE id = ?", avatarPath, userId);
        return getUserByIdDirect(userId);
    }

    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        return jdbcTemplate.query(
            "SELECT password_hash FROM users WHERE id = ?",
            rs -> {
                if (!rs.next()) return false;
                if (!passwordEncoder.matches(oldPassword, rs.getString("password_hash"))) return false;
                jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?",
                    passwordEncoder.encode(newPassword), userId);
                return true;
            }, userId
        );
    }

    @CacheEvict(value = "users", key = "#userId")
    public Optional<User> updateMajorType(long userId, Integer majorTypeId) {
        jdbcTemplate.update("UPDATE users SET major_type_id = ? WHERE id = ?", majorTypeId, userId);
        return getUserByIdDirect(userId);
    }

    @CacheEvict(value = "users", key = "#userId")
    public Optional<User> updateProfile(long userId, String username, Integer majorTypeId) {
        if (username != null && !username.isBlank()) {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ? AND id <> ?",
                Integer.class, username, userId
            );
            if (exists != null && exists > 0) return Optional.empty();
        }
        jdbcTemplate.update("UPDATE users SET username = ?, major_type_id = ? WHERE id = ?",
            username, majorTypeId, userId);
        return getUserByIdDirect(userId);
    }

    @Cacheable(value = "users", key = "#userId")
    public Optional<User> getUserById(long userId) {
        return getUserByIdDirect(userId);
    }

    private Optional<User> getUserByIdDirect(long userId) {
        return jdbcTemplate.query(
            "SELECT id, username, avatar, major_type_id FROM users WHERE id = ?",
            rs -> {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new User(
                    rs.getLong("id"), rs.getString("username"),
                    rs.getString("avatar"), (Integer) rs.getObject("major_type_id")
                ));
            }, userId
        );
    }
}
