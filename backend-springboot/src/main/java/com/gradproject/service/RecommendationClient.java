package com.gradproject.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RecommendationClient {
    private final RestTemplate restTemplate;

    @Value("${recommender.base-url}")
    private String baseUrl;

    public RecommendationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<?, ?> getRecommendations(String stuId, int topN) {
        String url = String.format("%s/api/recommend?userId=%s&topN=%d", baseUrl, stuId, topN);
        return restTemplate.getForObject(url, Map.class);
    }

    public Map<?, ?> getEvaluation(int topK, int maxUsers) {
        String url = String.format("%s/api/evaluate?topK=%d&maxUsers=%d", baseUrl, topK, maxUsers);
        return restTemplate.getForObject(url, Map.class);
    }
}
