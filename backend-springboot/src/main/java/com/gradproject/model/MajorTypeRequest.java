package com.gradproject.model;

public class MajorTypeRequest {
    private long userId;
    private Integer majorTypeId;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public Integer getMajorTypeId() { return majorTypeId; }
    public void setMajorTypeId(Integer majorTypeId) { this.majorTypeId = majorTypeId; }
}
