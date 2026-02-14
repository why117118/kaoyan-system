package com.gradproject.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WrongQuestion {
    private long id;
    @JsonProperty("user_id")
    private long userId;
    @JsonProperty("question_id")
    private Long questionId;
    @JsonProperty("question_text")
    private String questionText;
    @JsonProperty("course_name")
    private String courseName;
    @JsonProperty("your_answer")
    private String yourAnswer;
    @JsonProperty("correct_answer")
    private String correctAnswer;
    @JsonProperty("error_count")
    private int errorCount;

    public WrongQuestion(long id, long userId, Long questionId, String questionText,
                         String courseName, String yourAnswer, String correctAnswer, int errorCount) {
        this.id = id;
        this.userId = userId;
        this.questionId = questionId;
        this.questionText = questionText;
        this.courseName = courseName;
        this.yourAnswer = yourAnswer;
        this.correctAnswer = correctAnswer;
        this.errorCount = errorCount;
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public Long getQuestionId() { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getCourseName() { return courseName; }
    public String getYourAnswer() { return yourAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public int getErrorCount() { return errorCount; }
}
