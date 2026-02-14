package com.gradproject.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gradproject.model.AuthRequest;
import com.gradproject.model.MajorTypeRequest;
import com.gradproject.model.PasswordChangeRequest;
import com.gradproject.model.ProfileUpdateRequest;
import com.gradproject.model.PlanRequest;
import com.gradproject.model.User;
import com.gradproject.model.WrongQuestionRequest;
import com.gradproject.service.AuthService;
import com.gradproject.service.CourseService;
import com.gradproject.service.PlanService;
import com.gradproject.service.QuestionService;
import com.gradproject.service.RecommendationClient;
import com.gradproject.service.WrongQuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class RecommendationController {
    private final RecommendationClient recommendationClient;
    private final CourseService courseService;
    private final AuthService authService;
    private final PlanService planService;
    private final WrongQuestionService wrongQuestionService;
    private final QuestionService questionService;

    public RecommendationController(
        RecommendationClient recommendationClient,
        CourseService courseService,
        AuthService authService,
        PlanService planService,
        WrongQuestionService wrongQuestionService,
        QuestionService questionService
    ) {
        this.recommendationClient = recommendationClient;
        this.courseService = courseService;
        this.authService = authService;
        this.planService = planService;
        this.wrongQuestionService = wrongQuestionService;
        this.questionService = questionService;
    }

    // ==================== 推荐接口 ====================

    @GetMapping("/recommendations")
    public ResponseEntity<?> recommendations(
        @RequestParam("userId") long userId,
        @RequestParam(value = "topN", defaultValue = "10") int topN
    ) {
      try {
        String stuId = authService.ensureMapping(userId);
        if (stuId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_student_mapping",
                "recommendations", java.util.Collections.emptyList()));
        }
        Set<Integer> allowedTypeIds = buildAllowedTypeIds(userId);
        Set<String> allowedTypeNames = buildAllowedTypeNames(userId);
        String recentType = courseService.findRecentTypeName(userId);

        // Request many more from Flask to compensate for category filtering
        int fetchN = topN * 10;
        Map<?, ?> result = recommendationClient.getRecommendations(stuId, fetchN);
        Object recObj = result.get("recommendations");
        if (recObj instanceof java.util.List<?> list) {
            java.util.Iterator<?> iterator = list.iterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item instanceof java.util.Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) raw;
                    Object typeIdObj = map.get("type_id");
                    boolean allowed = false;
                    if (!allowedTypeIds.isEmpty() && typeIdObj instanceof Number typeId) {
                        allowed = allowedTypeIds.contains(typeId.intValue());
                    }
                    if (!allowed && !allowedTypeNames.isEmpty()) {
                        Object typeNameObj = map.get("type_name");
                        if (typeNameObj != null) {
                            String typeName = String.valueOf(typeNameObj);
                            for (String keyword : allowedTypeNames) {
                                if (typeName.contains(keyword)) { allowed = true; break; }
                            }
                        }
                    }
                    if (!allowed && (!allowedTypeIds.isEmpty() || !allowedTypeNames.isEmpty())) {
                        iterator.remove();
                        continue;
                    }
                    if (recentType != null) {
                        Object typeName = map.get("type_name");
                        String reason = recentType.equals(typeName)
                            ? "与你近期学习的科目相同" : "基于相似用户兴趣推荐";
                        map.put("reason", reason);
                    }
                }
            }
            // Trim to requested topN after filtering
            while (list.size() > topN) {
                list.remove(list.size() - 1);
            }

            // Fallback: if filtering removed everything, query popular courses from allowed categories
            if (list.isEmpty()) {
                List<Map<String, Object>> fallback = courseService.findPopularByTypeIds(allowedTypeIds, topN);
                return ResponseEntity.ok(Map.of("recommendations", fallback));
            }
        }
        return ResponseEntity.ok(result);
      } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of(
            "error", e.getClass().getSimpleName() + ": " + e.getMessage(),
            "recommendations", java.util.Collections.emptyList()));
      }
    }

    @GetMapping("/courses")
    public ResponseEntity<?> courses(
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "mode", defaultValue = "name") String mode
    ) {
        if (page != null && size != null) {
            return ResponseEntity.ok(courseService.listCoursesPaged(page, size, keyword, mode));
        }
        int effectiveLimit = limit == null ? 50 : limit;
        return ResponseEntity.ok(courseService.listCourses(effectiveLimit));
    }

    @PostMapping("/interactions")
    public ResponseEntity<?> recordInteraction(@RequestBody Map<String, Object> body) {
        long userId = ((Number) body.get("userId")).longValue();
        int courseIndex = ((Number) body.get("courseIndex")).intValue();
        String stuId = authService.ensureMapping(userId);
        if (stuId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_student_mapping"));
        }
        courseService.recordInteraction(stuId, courseIndex);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/evaluation")
    public ResponseEntity<Map<?, ?>> evaluation(
        @RequestParam(value = "topK", defaultValue = "10") int topK,
        @RequestParam(value = "maxUsers", defaultValue = "500") int maxUsers
    ) {
        return ResponseEntity.ok(recommendationClient.getEvaluation(topK, maxUsers));
    }

    // ==================== 认证接口 ====================

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        return authService.register(request.getUsername(), request.getPassword())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.badRequest().body(Map.of("error", "username_exists")));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Map<String, Object> result = authService.loginDetailed(request.getUsername(), request.getPassword());
        String status = (String) result.get("status");
        if ("ok".equals(status)) {
            return ResponseEntity.ok(result.get("user"));
        }
        return ResponseEntity.status(401).body(Map.of("error", status));
    }

    // ==================== 用户接口 ====================

    @PutMapping("/user/major")
    public ResponseEntity<?> updateMajor(@RequestBody MajorTypeRequest request) {
        return authService.updateMajorType(request.getUserId(), request.getMajorTypeId())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(404).body(Map.of("error", "user_not_found")));
    }

    @PutMapping("/user/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return authService.updateProfile(request.getUserId(), request.getUsername(), request.getMajorTypeId())
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.badRequest().body(Map.of("error", "username_exists")));
    }

    @PutMapping("/user/password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordChangeRequest request) {
        boolean updated = authService.changePassword(request.getUserId(), request.getOldPassword(), request.getNewPassword());
        if (!updated) return ResponseEntity.status(400).body(Map.of("error", "invalid_password"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/user/avatar")
    public ResponseEntity<?> uploadAvatar(
        @RequestParam("userId") long userId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "empty_file"));
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf(".");
        if (dot > -1) ext = original.substring(dot);
        Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
        Files.createDirectories(uploadDir);
        String fileName = "user_" + userId + "_" + System.currentTimeMillis() + ext;
        Path target = uploadDir.resolve(fileName);
        file.transferTo(target.toFile());
        String avatarPath = "/uploads/" + fileName;
        return authService.updateAvatar(userId, avatarPath)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(404).body(Map.of("error", "user_not_found")));
    }

    // ==================== 课程类别接口 ====================

    @GetMapping("/course-types")
    public ResponseEntity<?> courseTypes(@RequestParam(value = "exclude", required = false) String exclude) {
        java.util.List<String> keywords = null;
        if (exclude != null && !exclude.isBlank()) {
            keywords = java.util.Arrays.asList(exclude.split(","));
        }
        return ResponseEntity.ok(courseService.listCourseTypes(keywords));
    }

    // ==================== 题目接口 ====================

    @GetMapping("/questions")
    public ResponseEntity<?> questions(
        @RequestParam("courseId") int courseId,
        @RequestParam(value = "limit", defaultValue = "5") int limit,
        @RequestParam(value = "random", defaultValue = "true") boolean random
    ) {
        return ResponseEntity.ok(questionService.listQuestions(courseId, limit, random));
    }

    @GetMapping("/questions/by-category")
    public ResponseEntity<?> questionsByCategory(
        @RequestParam("category") String category,
        @RequestParam("userId") long userId,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        List<Integer> typeIdList = new java.util.ArrayList<>(resolveTypeIds(category, userId));
        return ResponseEntity.ok(questionService.listQuestionsByTypeIds(typeIdList, limit, true));
    }

    // ==================== 学习计划接口 ====================

    @GetMapping("/plans")
    public ResponseEntity<?> listPlans(
        @RequestParam("userId") long userId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sort", defaultValue = "desc") String sort
    ) {
        return ResponseEntity.ok(planService.listPlans(userId, status, sort));
    }

    @PostMapping("/plans")
    public ResponseEntity<?> createPlan(@RequestBody PlanRequest request) {
        planService.createPlan(request);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<?> updatePlan(
        @PathVariable("id") long id,
        @RequestParam("userId") long userId,
        @RequestBody PlanRequest request
    ) {
        planService.updatePlan(id, userId, request);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(@PathVariable("id") long id, @RequestParam("userId") long userId) {
        planService.deletePlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==================== 错题接口 ====================

    @GetMapping("/wrong-questions")
    public ResponseEntity<?> listWrongQuestions(
        @RequestParam("userId") long userId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "courseId", required = false) Integer courseId,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        if (page != null && size != null) {
            List<Integer> typeIds = null;
            if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
                typeIds = new java.util.ArrayList<>(resolveTypeIds(category, userId));
            }
            return ResponseEntity.ok(wrongQuestionService.listPaged(userId, typeIds, keyword, page, size));
        }
        return ResponseEntity.ok(wrongQuestionService.list(userId, keyword, courseId));
    }

    @PostMapping("/wrong-questions")
    public ResponseEntity<?> createWrongQuestion(@RequestBody WrongQuestionRequest request) {
        int errorCount = wrongQuestionService.create(request);
        return ResponseEntity.ok(Map.of("status", "ok", "error_count", errorCount));
    }

    @GetMapping("/wrong-questions/count")
    public ResponseEntity<?> countWrongQuestion(
        @RequestParam("userId") long userId,
        @RequestParam(value = "questionId", required = false) Long questionId,
        @RequestParam(value = "questionText", required = false) String questionText
    ) {
        int cnt = wrongQuestionService.countForQuestion(userId, questionId, questionText);
        return ResponseEntity.ok(Map.of("error_count", cnt));
    }

    @DeleteMapping("/wrong-questions/{id}")
    public ResponseEntity<?> deleteWrongQuestion(@PathVariable("id") long id, @RequestParam("userId") long userId) {
        wrongQuestionService.delete(id, userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/wrong-questions/by-category")
    public ResponseEntity<?> wrongQuestionsByCategory(
        @RequestParam("category") String category,
        @RequestParam("userId") long userId,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        List<Integer> typeIdList = new java.util.ArrayList<>(resolveTypeIds(category, userId));
        if (typeIdList.isEmpty()) return ResponseEntity.ok(java.util.Collections.emptyList());
        return ResponseEntity.ok(wrongQuestionService.listByTypeIds(userId, typeIdList, limit));
    }

    // ==================== 辅助方法 ====================

    private Set<Integer> buildAllowedTypeIds(long userId) {
        Set<Integer> typeIds = new HashSet<>();
        typeIds.addAll(courseService.findTypeIdsByKeyword("数学"));
        typeIds.addAll(courseService.findTypeIdsByKeyword("英语"));
        typeIds.addAll(courseService.findTypeIdsByKeyword("外语"));
        typeIds.addAll(courseService.findTypeIdsByKeyword("政治"));
        typeIds.addAll(courseService.findTypeIdsByKeyword("哲学"));
        authService.getUserById(userId).ifPresent(user -> {
            if (user.getMajorTypeId() != null) typeIds.add(user.getMajorTypeId());
        });
        return typeIds;
    }

    private Set<Integer> resolveTypeIds(String category, long userId) {
        Set<Integer> typeIds = new HashSet<>();
        if ("major".equalsIgnoreCase(category)) {
            authService.getUserById(userId).ifPresent(user -> {
                if (user.getMajorTypeId() != null) typeIds.add(user.getMajorTypeId());
            });
        } else if ("math".equalsIgnoreCase(category)) {
            typeIds.addAll(courseService.findTypeIdsByKeyword("数学"));
        } else if ("english".equalsIgnoreCase(category)) {
            typeIds.addAll(courseService.findTypeIdsByKeyword("英语"));
            typeIds.addAll(courseService.findTypeIdsByKeyword("外语"));
        } else if ("politics".equalsIgnoreCase(category)) {
            typeIds.addAll(courseService.findTypeIdsByKeyword("政治"));
            typeIds.addAll(courseService.findTypeIdsByKeyword("哲学"));
        }
        return typeIds;
    }

    private Set<String> buildAllowedTypeNames(long userId) {
        Set<String> names = new HashSet<>();
        names.add("数学");
        names.add("英语");
        names.add("外语");
        names.add("政治");
        names.add("哲学");
        authService.getUserById(userId).ifPresent(user -> {
            if (user.getMajorTypeId() != null) {
                String majorName = courseService.findTypeNameById(user.getMajorTypeId());
                if (majorName != null && !majorName.isBlank()) names.add(majorName);
            }
        });
        return names;
    }
}
