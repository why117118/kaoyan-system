package com.gradproject.model;

import java.time.LocalDate;

public class StudyPlan {
    private long id;
    private long userId;
    private String title;
    private String description;
    private LocalDate targetDate;
    private String status;

    public StudyPlan(long id, long userId, String title, String description, LocalDate targetDate, String status) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.targetDate = targetDate;
        this.status = status;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getStatus() { return status; }
}
