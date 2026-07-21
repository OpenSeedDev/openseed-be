package com.seedrank.ai.job;

interface AiCandidateProvider {
    String generate(String inputSnapshot, String promptVersion);
}
