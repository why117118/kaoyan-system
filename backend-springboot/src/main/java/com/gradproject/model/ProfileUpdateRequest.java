package com.gradproject.model;

public class ProfileUpdateRequest {
    private long userId;
    private String username;
    private Integer majorTypeId;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getMajorTypeId() { return majorTypeId; }
    public void setMajorTypeId(Integer majorTypeId) { this.majorTypeId = majorTypeId; }
}
