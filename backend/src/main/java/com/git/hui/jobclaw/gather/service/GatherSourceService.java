package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.dao.repository.GatherSourceRepository;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import com.git.hui.jobclaw.web.model.req.GatherSourceSearchReq;
import com.git.hui.jobclaw.web.model.res.GatherSourceVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@Service
public class GatherSourceService {
    public static final String OWNER_ADMIN = "admin";
    public static final String OWNER_AGENT = "agent";
    public static final String OWNER_IM = "im";
    public static final String RUNNER_DRAFT_ONLY = "draft_only";
    public static final String RUNNER_AGENT = "agent";
    public static final String RUNNER_IM_FETCH = "im_fetch";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_INVALID = "invalid";

    /**
     * 仅 Admin 投料任务可被 OfferGather 调度；IM/Agent 外部执行器任务需排除。
     */
    public static boolean isOfferGatherRunnable(String runnerType) {
        return runnerType == null || runnerType.isBlank() || RUNNER_DRAFT_ONLY.equals(runnerType);
    }

    public static boolean isExternalRunner(String runnerType) {
        return RUNNER_AGENT.equals(runnerType) || RUNNER_IM_FETCH.equals(runnerType);
    }

    private static final Set<String> SOURCE_STATUSES = Set.of(STATUS_ACTIVE, STATUS_PAUSED, STATUS_ARCHIVED, STATUS_INVALID);

    private final GatherSourceRepository gatherSourceRepository;

    @Autowired
    public GatherSourceService(GatherSourceRepository gatherSourceRepository) {
        this.gatherSourceRepository = gatherSourceRepository;
    }

    @Transactional
    public GatherSourceEntity resolveSource(GatherTargetTypeEnum type, String content, String model, String ownerType, String runnerType) {
        String normalized = normalizeContent(content);
        String sourceHash = hash((type == null ? "0" : type.getValue()) + ":" + normalized);
        String contentHash = hash(normalized);
        Date now = new Date();

        GatherSourceEntity source = gatherSourceRepository.findFirstBySourceHash(sourceHash).orElse(null);
        if (source == null) {
            source = new GatherSourceEntity()
                    .setType(type == null ? 0 : type.getValue())
                    .setTitle(buildTitle(type, content))
                    .setContent(content == null ? "" : content)
                    .setSourceHash(sourceHash)
                    .setContentHash(contentHash)
                    .setVersion(1)
                    .setOwnerType(ownerType)
                    .setRunnerType(runnerType)
                    .setLastModel(model == null ? "" : model)
                    .setStatus(STATUS_ACTIVE)
                    .setCreateTime(now)
                    .setUpdateTime(now);
        } else {
            if (!contentHash.equals(source.getContentHash())) {
                source.setContent(content == null ? "" : content)
                        .setContentHash(contentHash)
                        .setVersion(source.getVersion() == null ? 1 : source.getVersion() + 1);
            }
            source.setTitle(buildTitle(type, content))
                    .setOwnerType(ownerType)
                    .setRunnerType(runnerType)
                    .setStatus(STATUS_ACTIVE)
                    .setUpdateTime(now);
            if (model != null && !model.isBlank()) {
                source.setLastModel(model);
            }
        }
        return gatherSourceRepository.saveAndFlush(source);
    }

    @Transactional
    public void markTaskCreated(GatherSourceEntity source, GatherTaskEntity task) {
        if (source == null || task == null) {
            return;
        }
        source.setLastTaskId(task.getId())
                .setLastModel(task.getModel() == null ? "" : task.getModel())
                .setLastRunTime(task.getCreateTime())
                .setUpdateTime(new Date());
        gatherSourceRepository.save(source);
    }

    @Transactional
    public void updateLastResult(Long sourceId, Long taskId, String resultSummary) {
        if (sourceId == null) {
            return;
        }
        gatherSourceRepository.findById(sourceId).ifPresent(source -> {
            source.setLastTaskId(taskId)
                    .setLastResultSummary(resultSummary)
                    .setLastRunTime(new Date())
                    .setUpdateTime(new Date());
            gatherSourceRepository.save(source);
        });
    }

    public PageListVo<GatherSourceVo> searchList(GatherSourceSearchReq req) {
        req.autoInitPage();
        PageListVo<GatherSourceEntity> page = gatherSourceRepository.findList(req);
        return PageListVo.of(page.getList().stream().map(this::toVo).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public GatherTaskSaveBo toTaskSaveBo(Long sourceId, String model) {
        GatherSourceEntity source = getSource(sourceId);
        GatherTargetTypeEnum type = com.git.hui.jobclaw.core.utils.json.IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, source.getType());
        return new GatherTaskSaveBo(type, model, source.getContent(), null);
    }

    public GatherSourceEntity getSource(Long sourceId) {
        GatherSourceEntity source = gatherSourceRepository.findById(sourceId).orElseThrow(() -> new IllegalArgumentException(sourceId + "非法"));
        return source;
    }

    @Transactional
    public GatherSourceEntity updateStatus(Long sourceId, String status) {
        if (status == null || !SOURCE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("unsupported source status: " + status);
        }
        GatherSourceEntity source = getSource(sourceId);
        source.setStatus(status)
                .setUpdateTime(new Date());
        return gatherSourceRepository.saveAndFlush(source);
    }

    private GatherSourceVo toVo(GatherSourceEntity source) {
        return new GatherSourceVo()
                .setId(source.getId())
                .setType(source.getType())
                .setTitle(source.getTitle())
                .setContent(source.getContent())
                .setVersion(source.getVersion())
                .setOwnerType(source.getOwnerType())
                .setRunnerType(source.getRunnerType())
                .setLastModel(source.getLastModel())
                .setStatus(source.getStatus())
                .setLastTaskId(source.getLastTaskId())
                .setLastResultSummary(source.getLastResultSummary())
                .setLastRunTime(source.getLastRunTime() == null ? null : source.getLastRunTime().getTime())
                .setCreateTime(source.getCreateTime() == null ? null : source.getCreateTime().getTime())
                .setUpdateTime(source.getUpdateTime() == null ? null : source.getUpdateTime().getTime());
    }

    private String buildTitle(GatherTargetTypeEnum type, String content) {
        String prefix = type == null ? "采集来源" : type.getDesc();
        String normalized = normalizeContent(content);
        if (normalized.isBlank()) {
            return prefix;
        }
        return prefix + " · " + normalized.substring(0, Math.min(48, normalized.length()));
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.trim().replaceAll("\\s+", " ");
    }

    private String hash(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
