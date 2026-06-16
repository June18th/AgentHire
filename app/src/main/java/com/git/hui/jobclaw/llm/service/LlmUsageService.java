package com.git.hui.jobclaw.llm.service;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import com.git.hui.jobclaw.llm.dao.repository.LlmInvocationRepository;
import com.git.hui.jobclaw.llm.dao.repository.LlmRequestRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class LlmUsageService {
    private final LlmInvocationRepository repository;
    private final LlmRequestRepository requestRepository;

    public LlmUsageService(LlmInvocationRepository repository, LlmRequestRepository requestRepository) {
        this.repository = repository;
        this.requestRepository = requestRepository;
    }

    public Map<String, Object> overview(String userId) {
        List<LlmInvocationEntity> rows = repository.findAll(spec(userId, null, null, null));
        long success = rows.stream().filter(x -> "SUCCESS".equals(x.getOutcome())).count();
        long duration = rows.stream().map(LlmInvocationEntity::getDurationMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long tokens = rows.stream().map(LlmInvocationEntity::getTotalTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        BigDecimal cost = rows.stream().map(LlmInvocationEntity::getEstimatedCost).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("calls", rows.size(), "successRate", rows.isEmpty() ? 0D : success * 100D / rows.size(),
                "averageDurationMs", rows.isEmpty() ? 0D : duration * 1D / rows.size(), "totalTokens", tokens, "estimatedCost", cost);
    }

    public PageListVo<LlmInvocationEntity> calls(String userId, String agent, String operation, String outcome, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.findAll(spec(userId, agent, operation, outcome),
                PageRequest.of(Math.max(0, page - 1), safeSize).withSort(Sort.by(Sort.Order.desc("createTime"))));
        return PageListVo.of(result.getContent(), result.getTotalElements(), page, safeSize);
    }

    public LlmInvocationEntity detail(String id, String userId) {
        return repository.findOne(spec(userId, null, null, null).and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new NoSuchElementException("LLM call not found"));
    }

    public Map<String, Object> adminDetail(String id) {
        return Map.of("invocation", detail(id, null), "requests", requestRepository.findByInvocationIdOrderByCreateTimeAsc(id));
    }

    private Specification<LlmInvocationEntity> spec(String userId, String agent, String operation, String outcome) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (userId != null) p.add(cb.equal(root.get("jobClawUserId"), userId));
            if (agent != null && !agent.isBlank()) p.add(cb.equal(root.get("agent"), agent));
            if (operation != null && !operation.isBlank()) p.add(cb.equal(root.get("operation"), operation));
            if (outcome != null && !outcome.isBlank()) p.add(cb.equal(root.get("outcome"), outcome));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
