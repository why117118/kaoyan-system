package com.gradproject.model;

public class WrongQuestionRequest {
    private long userId;
    private Long questionId;
    private String questionText;
    private String courseName;
    private String yourAnswer;
    private String correctAnswer;

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getYourAnswer() { return yourAnswer; }
    public void setYourAnswer(String yourAnswer) { this.yourAnswer = yourAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
}
