package com.seedrank.ai.job;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

@Component
class AiCandidateResponseValidator {
    private static final int REQUIRED_CANDIDATE_COUNT = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    String validateAndNormalize(String rawResult) {
        try {
            JsonNode root = JSON.readTree(rawResult);
            if (root == null || !root.isObject() || root.size() != 2) throw invalid();
            String problemAnalysis = required(root, "problemAnalysis", 2000);
            JsonNode candidatesNode = root.get("candidates");
            if (!(candidatesNode instanceof ArrayNode candidates) || candidates.size() != REQUIRED_CANDIDATE_COUNT) {
                throw invalid();
            }

            ObjectNode normalized = JSON.createObjectNode();
            normalized.put("problemAnalysis", problemAnalysis);
            ArrayNode normalizedCandidates = normalized.putArray("candidates");
            Set<String> titles = new HashSet<>();
            for (JsonNode candidate : candidates) {
                if (!candidate.isObject() || candidate.size() != 7) throw invalid();
                String title = required(candidate, "title", 100);
                if (!titles.add(title.toLowerCase(Locale.ROOT))) throw invalid();
                ObjectNode value = normalizedCandidates.addObject();
                value.put("title", title);
                value.put("category", required(candidate, "category", 50));
                value.put("summary", required(candidate, "summary", 200));
                value.put("problem", required(candidate, "problem", 2000));
                value.put("targetCustomer", required(candidate, "targetCustomer", 1000));
                value.put("solution", required(candidate, "solution", 2000));
                value.put("businessModel", required(candidate, "businessModel", 2000));
            }
            return JSON.writeValueAsString(normalized);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private String required(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid();
        String normalized = value.textValue().strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) throw invalid();
        return normalized;
    }

    private InvalidAiCandidateResponseException invalid() {
        return new InvalidAiCandidateResponseException();
    }
}
