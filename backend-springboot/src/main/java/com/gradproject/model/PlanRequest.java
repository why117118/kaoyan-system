package com.gradproject.model;

public class PlanRequest {
    private long userId;
    private String title;
    private String description;
    private String targetDate;
    private String status;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
