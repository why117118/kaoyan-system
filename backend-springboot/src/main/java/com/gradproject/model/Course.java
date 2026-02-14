package com.gradproject.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Course {
    @JsonProperty("course_index")
    private int courseIndex;
    private String name;
    @JsonProperty("type_id")
    private int typeId;
    @JsonProperty("type_name")
    private String typeName;
    private String url;

    public Course(int courseIndex, String name, int typeId, String typeName, String url) {
        this.courseIndex = courseIndex;
        this.name = name;
        this.typeId = typeId;
        this.typeName = typeName;
        this.url = url;
    }

    public int getCourseIndex() { return courseIndex; }
    public String getName() { return name; }
    public int getTypeId() { return typeId; }
    public String getTypeName() { return typeName; }
    public String getUrl() { return url; }
}
