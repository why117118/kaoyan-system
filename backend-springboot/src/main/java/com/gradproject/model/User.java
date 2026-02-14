package com.gradproject.model;

public class User {
    private long id;
    private String username;
    private String avatarPath;
    private Integer majorTypeId;

    public User(long id, String username, String avatarPath, Integer majorTypeId) {
        this.id = id;
        this.username = username;
        this.avatarPath = avatarPath;
        this.majorTypeId = majorTypeId;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getAvatarPath() { return avatarPath; }
    public Integer getMajorTypeId() { return majorTypeId; }
}
