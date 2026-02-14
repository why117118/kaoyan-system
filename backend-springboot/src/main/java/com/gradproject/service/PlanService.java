package com.gradproject.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import com.gradproject.model.PlanRequest;
import com.gradproject.model.StudyPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlanService {
    private final JdbcTemplate jdbcTemplate;

    public PlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StudyPlan> listPlans(long userId, String status, String sort) {
        String orderBy = "ASC".equalsIgnoreCase(sort) ? "ASC" : "DESC";
        String sql = """
            SELECT id, user_id, title, description, target_date, status
            FROM study_plans WHERE user_id = ?
            """ + (status == null || status.isBlank() ? "" : " AND status = ? ")
            + " ORDER BY target_date IS NULL, target_date " + orderBy + ", id DESC";

        return jdbcTemplate.query(sql,
            ps -> {
                ps.setLong(1, userId);
                if (status != null && !status.isBlank()) ps.setString(2, status);
            },
            (rs, rowNum) -> new StudyPlan(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("title"),
                rs.getString("description"),
                rs.getDate("target_date") == null ? null : rs.getDate("target_date").toLocalDate(),
                rs.getString("status")
            )
        );
    }

    public void createPlan(PlanRequest request) {
        Date targetDate = request.getTargetDate() == null || request.getTargetDate().isBlank()
            ? null : Date.valueOf(LocalDate.parse(request.getTargetDate()));
        jdbcTemplate.update(
            "INSERT INTO study_plans (user_id, title, description, target_date, status) VALUES (?, ?, ?, ?, ?)",
            request.getUserId(), request.getTitle(), request.getDescription(), targetDate,
            request.getStatus() == null || request.getStatus().isBlank() ? "pending" : request.getStatus()
        );
    }

    public void updatePlan(long planId, long userId, PlanRequest request) {
        Date targetDate = request.getTargetDate() == null || request.getTargetDate().isBlank()
            ? null : Date.valueOf(LocalDate.parse(request.getTargetDate()));
        jdbcTemplate.update(
            "UPDATE study_plans SET title = ?, description = ?, target_date = ?, status = ? WHERE id = ? AND user_id = ?",
            request.getTitle(), request.getDescription(), targetDate, request.getStatus(), planId, userId
        );
    }

    public void deletePlan(long planId, long userId) {
        jdbcTemplate.update("DELETE FROM study_plans WHERE id = ? AND user_id = ?", planId, userId);
    }
}
