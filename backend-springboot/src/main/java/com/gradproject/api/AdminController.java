package com.gradproject.api;

import java.util.Map;

import com.gradproject.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ==================== 管理员登录 ====================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        return adminService.login(username, password)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(401).body(Map.of("error", "invalid_credentials")));
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        Integer majorTypeId = body.get("majorTypeId") == null ? null : ((Number) body.get("majorTypeId")).intValue();
        adminService.updateUser(id, username, majorTypeId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 题库管理 ====================

    @GetMapping("/questions")
    public ResponseEntity<?> listQuestions(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminService.listAllQuestions(page, size, keyword));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> addQuestion(@RequestBody Map<String, Object> body) {
        int courseId = ((Number) body.get("courseId")).intValue();
        String courseName = (String) body.get("courseName");
        String question = (String) body.get("question");
        String options = (String) body.get("options");
        String answer = (String) body.get("answer");
        String explanation = (String) body.get("explanation");
        adminService.addQuestion(courseId, courseName, question, options, answer, explanation);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String options = (String) body.get("options");
        String answer = (String) body.get("answer");
        String explanation = (String) body.get("explanation");
        adminService.updateQuestion(id, question, options, answer, explanation);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable("id") long id) {
        adminService.deleteQuestion(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 错题管理 ====================

    @GetMapping("/wrong-questions")
    public ResponseEntity<?> listWrongQuestions(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminService.listAllWrongQuestions(page, size, keyword));
    }

    @DeleteMapping("/wrong-questions/{id}")
    public ResponseEntity<?> deleteWrongQuestion(@PathVariable("id") long id) {
        adminService.deleteWrongQuestion(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 学习计划管理 ====================

    @GetMapping("/plans")
    public ResponseEntity<?> listPlans(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminService.listAllPlans(page, size, keyword));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String targetDate = (String) body.get("targetDate");
        String status = (String) body.get("status");
        adminService.updatePlan(id, title, description, targetDate, status);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable("id") long id) {
        adminService.deletePlan(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 课程链接管理 ====================

    @GetMapping("/courses")
    public ResponseEntity<?> listCourses(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ResponseEntity.ok(adminService.listCoursesAdmin(page, size, keyword));
    }

    @PutMapping("/courses/{courseIndex}/url")
    public ResponseEntity<?> updateCourseUrl(
        @PathVariable("courseIndex") int courseIndex,
        @RequestBody Map<String, String> body
    ) {
        adminService.updateCourseUrl(courseIndex, body.get("url"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
