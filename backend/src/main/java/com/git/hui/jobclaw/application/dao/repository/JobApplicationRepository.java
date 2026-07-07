package com.git.hui.jobclaw.application.dao.repository;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.constants.application.JobApplicationStatusEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.JobApplicationSearchReq;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Arrays;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplicationEntity, Long>, JpaSpecificationExecutor<JobApplicationEntity> {
    List<String> TERMINAL_STATUS_CODES = Arrays.stream(JobApplicationStatusEnum.values())
            .filter(JobApplicationStatusEnum::isTerminal)
            .map(JobApplicationStatusEnum::getCode)
            .toList();

    Optional<JobApplicationEntity> findFirstByUserIdAndJobIdAndStateNotOrderByIdDesc(Long userId, Long jobId, Integer state);

    List<JobApplicationEntity> findByUserIdAndStateNot(Long userId, Integer state);

    List<JobApplicationEntity> findByUserIdAndJobIdInAndStateNot(Long userId, List<Long> jobIds, Integer state);

    List<JobApplicationEntity> findByUserIdAndIdInAndStateNot(Long userId, List<Long> ids, Integer state);

    List<JobApplicationEntity> findByUserIdAndStateNotAndCurrentStatusNotIn(Long userId, Integer state, List<String> currentStatuses);

    default PageListVo<JobApplicationEntity> findList(Long userId, JobApplicationSearchReq req) {
        Specification<JobApplicationEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            predicates.add(criteriaBuilder.notEqual(root.get("state"), -1));

            if (req.getJobId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("jobId"), req.getJobId()));
            }
            if (req.getCurrentStatus() != null && !req.getCurrentStatus().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("currentStatus"), req.getCurrentStatus()));
            }
            if (req.getCompanyName() != null && !req.getCompanyName().isBlank()) {
                predicates.add(criteriaBuilder.like(root.get("companyName"), "%" + req.getCompanyName() + "%"));
            }
            if (req.getPosition() != null && !req.getPosition().isBlank()) {
                predicates.add(criteriaBuilder.like(root.get("position"), "%" + req.getPosition() + "%"));
            }
            if (req.getCompanyType() != null && !req.getCompanyType().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("companyType"), req.getCompanyType()));
            }
            if (req.getFollowUpScope() != null && !req.getFollowUpScope().isBlank()) {
                predicates.add(criteriaBuilder.isNotNull(root.get("nextFollowUpAt")));
                predicates.add(root.get("currentStatus").in(TERMINAL_STATUS_CODES).not());
                if ("OVERDUE".equalsIgnoreCase(req.getFollowUpScope())) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("nextFollowUpAt"), new Date()));
                }
            }
            if (req.getPriority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), req.getPriority()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<JobApplicationEntity> page = findAll(spec, PageRequest.of(req.getPage() - 1, req.getSize())
                .withSort(Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("id"))));
        return PageListVo.of(page.getContent(), page.getTotalElements(), req.getPage(), req.getSize());
    }
}
