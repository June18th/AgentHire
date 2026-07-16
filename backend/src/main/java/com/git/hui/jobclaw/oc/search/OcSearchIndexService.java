package com.git.hui.jobclaw.oc.search;

import tools.jackson.databind.JsonNode;
import com.git.hui.jobclaw.configs.SearchProperties;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.OcSearchReq;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位 Elasticsearch 索引服务。
 * <p>
 * AIDEV-NOTE: MySQL 为权威源；ES 双写失败仅记日志，不做自动补偿，需管理端 reindex 手动重建。
 * 岗位推荐（{@code ocRepository.recommend}）始终走 MySQL，不依赖 ES。
 */
@Slf4j
@Service
public class OcSearchIndexService {
    private final SearchProperties properties;
    private final RestClient restClient;
    private final OcRepository ocRepository;

    public OcSearchIndexService(SearchProperties properties, ObjectProvider<RestClient> restClientProvider,
                                OcRepository ocRepository) {
        this.properties = properties;
        this.restClient = restClientProvider.getIfAvailable();
        this.ocRepository = ocRepository;
    }

    public boolean enabled() {
        return properties.isEnabled() && restClient != null;
    }

    public PageListVo<OcInfoEntity> search(OcSearchReq req) {
        req.autoInitPage();
        if (!enabled()) {
            return null;
        }

        try {
            ensureIndex();
            Map<String, Object> query = buildSearchBody(req);
            Request request = jsonRequest("POST", "/" + properties.getIndexName() + "/_search", query);
            Response response = restClient.performRequest(request);
            return parseSearchResponse(response, req);
        } catch (Exception e) {
            log.warn("Elasticsearch job search failed, fallback to mysql. keyword={}", req.getKeyword(), e);
            return null;
        }
    }

    public boolean indexJob(OcInfoEntity entity) {
        if (!enabled() || entity == null || entity.getId() == null) {
            return false;
        }

        try {
            ensureIndex();
            if (!OcStateEnum.PUBLISHED.getValue().equals(entity.getState())) {
                deleteJob(entity.getId());
                return true;
            }
            Request request = jsonRequest("PUT", "/" + properties.getIndexName() + "/_doc/" + entity.getId(), toDocument(entity));
            restClient.performRequest(request);
            return true;
        } catch (Exception e) {
            log.warn("Index job failed. jobId={}", entity.getId(), e);
            return false;
        }
    }

    public boolean deleteJob(Long id) {
        if (!enabled() || id == null) {
            return false;
        }
        try {
            restClient.performRequest(new Request("DELETE", "/" + properties.getIndexName() + "/_doc/" + id));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return true;
            }
            log.warn("Delete job index failed. jobId={}", id, e);
            return false;
        } catch (Exception e) {
            log.warn("Delete job index failed. jobId={}", id, e);
            return false;
        }
    }

    public int reindexPublishedJobs() {
        if (!enabled()) {
            return 0;
        }

        try {
            ensureIndex();
            List<OcInfoEntity> jobs = ocRepository.findByState(OcStateEnum.PUBLISHED.getValue());
            if (jobs.isEmpty()) {
                return 0;
            }

            StringBuilder body = new StringBuilder();
            for (OcInfoEntity job : jobs) {
                body.append(JsonUtil.toStr(Map.of("index", Map.of("_index", properties.getIndexName(), "_id", String.valueOf(job.getId())))))
                        .append('\n');
                body.append(JsonUtil.toStr(toDocument(job))).append('\n');
            }
            Request request = new Request("POST", "/_bulk");
            request.setEntity(new StringEntity(body.toString(), ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
            Response response = restClient.performRequest(request);
            JsonNode node = responseNode(response);
            if (node.path("errors").asBoolean(false)) {
                log.warn("Reindex jobs completed with partial errors: {}", node.path("items"));
            }
            return jobs.size();
        } catch (Exception e) {
            log.warn("Reindex jobs failed", e);
            return 0;
        }
    }

    private void ensureIndex() throws IOException {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/" + properties.getIndexName()));
            if (response.getStatusLine().getStatusCode() == 200) {
                return;
            }
            Request request = jsonRequest("PUT", "/" + properties.getIndexName(), indexDefinition());
            restClient.performRequest(request);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
            Request request = jsonRequest("PUT", "/" + properties.getIndexName(), indexDefinition());
            restClient.performRequest(request);
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> keyword = Map.of("type", "keyword");
        Map<String, Object> textWithKeyword = Map.of("type", "text", "fields", Map.of("keyword", keyword));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "long"));
        props.put("draftId", Map.of("type", "long"));
        props.put("companyName", textWithKeyword);
        props.put("companyType", textWithKeyword);
        props.put("companyIndustry", textWithKeyword);
        props.put("jobLocation", textWithKeyword);
        props.put("recruitmentType", textWithKeyword);
        props.put("recruitmentTarget", textWithKeyword);
        props.put("position", textWithKeyword);
        props.put("deliveryProgress", textWithKeyword);
        props.put("deadline", textWithKeyword);
        props.put("relatedLink", keyword);
        props.put("jobAnnouncement", textWithKeyword);
        props.put("internalReferralCode", keyword);
        props.put("remarks", textWithKeyword);
        props.put("state", Map.of("type", "integer"));
        props.put("lastUpdatedTime", Map.of("type", "long"));
        props.put("createTime", Map.of("type", "long"));
        props.put("updateTime", Map.of("type", "long"));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("settings", settings);
        definition.put("mappings", Map.of("properties", props));
        return definition;
    }

    private Map<String, Object> buildSearchBody(OcSearchReq req) {
        List<Object> must = new ArrayList<>();
        List<Object> filter = new ArrayList<>();
        List<Object> mustNot = new ArrayList<>();

        addTerm(filter, "id", req.getId());
        addMatch(must, "companyName", req.getCompanyName());
        addMatch(must, "companyIndustry", req.getCompanyIndustry());
        addMatch(must, "jobLocation", req.getJobLocation());
        addMatch(must, "recruitmentType", req.getRecruitmentType());
        addMatch(must, "recruitmentTarget", req.getRecruitmentTarget());
        addMatch(must, "position", req.getPosition());
        addTerm(filter, "companyType.keyword", req.getCompanyType());
        addTerm(filter, "deliveryProgress.keyword", req.getDeliveryProgress());
        addTerm(filter, "state", req.getState());
        addTerm(mustNot, "state", req.getNotState());

        if (StringUtils.isNotBlank(req.getRecruitmentTypeExcept())) {
            addMatch(mustNot, "recruitmentType", req.getRecruitmentTypeExcept());
        }

        Map<String, Object> range = new LinkedHashMap<>();
        if (req.getLastUpdatedTimeAfter() != null) {
            range.put("gte", req.getLastUpdatedTimeAfter());
        }
        if (req.getLastUpdatedTimeBefore() != null) {
            range.put("lte", req.getLastUpdatedTimeBefore());
        }
        if (!range.isEmpty()) {
            filter.add(Map.of("range", Map.of("lastUpdatedTime", range)));
        }

        if (StringUtils.isNotBlank(req.getKeyword())) {
            must.add(Map.of("multi_match", Map.of(
                    "query", req.getKeyword(),
                    "fields", List.of("companyName^3", "position^4", "jobLocation^2", "companyIndustry^2",
                            "companyType", "recruitmentType", "recruitmentTarget", "jobAnnouncement", "remarks"),
                    "type", "best_fields"
            )));
        }

        if (must.isEmpty()) {
            must.add(Map.of("match_all", Map.of()));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", must);
        if (!filter.isEmpty()) {
            bool.put("filter", filter);
        }
        if (!mustNot.isEmpty()) {
            bool.put("must_not", mustNot);
        }

        int from = (req.getPage() - 1) * req.getSize();
        return Map.of(
                "from", from,
                "size", req.getSize(),
                "query", Map.of("bool", bool),
                "sort", List.of(Map.of("id", Map.of("order", "desc")))
        );
    }

    private void addMatch(List<Object> clauses, String field, String value) {
        if (StringUtils.isNotBlank(value)) {
            clauses.add(Map.of("match_phrase", Map.of(field, value)));
        }
    }

    private void addTerm(List<Object> clauses, String field, Object value) {
        if (value != null && (!(value instanceof String text) || StringUtils.isNotBlank(text))) {
            clauses.add(Map.of("term", Map.of(field, value)));
        }
    }

    private PageListVo<OcInfoEntity> parseSearchResponse(Response response, OcSearchReq req) throws IOException {
        JsonNode root = responseNode(response);
        JsonNode hits = root.path("hits").path("hits");
        List<OcInfoEntity> list = new ArrayList<>();
        for (JsonNode hit : hits) {
            list.add(toEntity(hit.path("_source")));
        }
        long total = root.path("hits").path("total").path("value").asLong(list.size());
        return PageListVo.of(list, total, req.getPage(), req.getSize());
    }

    private JsonNode responseNode(Response response) throws IOException {
        HttpEntity entity = response.getEntity();
        String body = entity == null ? "{}" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
        return JsonUtil.toJsonNode(body);
    }

    private Request jsonRequest(String method, String endpoint, Object body) {
        Request request = new Request(method, endpoint);
        request.setEntity(new StringEntity(JsonUtil.toStr(body), ContentType.APPLICATION_JSON));
        return request;
    }

    private Map<String, Object> toDocument(OcInfoEntity entity) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", entity.getId());
        doc.put("draftId", entity.getDraftId());
        doc.put("companyName", safe(entity.getCompanyName()));
        doc.put("companyType", safe(entity.getCompanyType()));
        doc.put("companyIndustry", safe(entity.getCompanyIndustry()));
        doc.put("jobLocation", safe(entity.getJobLocation()));
        doc.put("recruitmentType", safe(entity.getRecruitmentType()));
        doc.put("recruitmentTarget", safe(entity.getRecruitmentTarget()));
        doc.put("position", safe(entity.getPosition()));
        doc.put("deliveryProgress", safe(entity.getDeliveryProgress()));
        doc.put("deadline", safe(entity.getDeadline()));
        doc.put("relatedLink", safe(entity.getRelatedLink()));
        doc.put("jobAnnouncement", safe(entity.getJobAnnouncement()));
        doc.put("internalReferralCode", safe(entity.getInternalReferralCode()));
        doc.put("remarks", safe(entity.getRemarks()));
        doc.put("state", entity.getState());
        doc.put("lastUpdatedTime", time(entity.getLastUpdatedTime()));
        doc.put("createTime", time(entity.getCreateTime()));
        doc.put("updateTime", time(entity.getUpdateTime()));
        return doc;
    }

    private OcInfoEntity toEntity(JsonNode node) {
        return new OcInfoEntity()
                .setId(node.path("id").asLong())
                .setDraftId(nullableLong(node, "draftId"))
                .setCompanyName(node.path("companyName").asText(""))
                .setCompanyType(node.path("companyType").asText(""))
                .setCompanyIndustry(node.path("companyIndustry").asText(""))
                .setJobLocation(node.path("jobLocation").asText(""))
                .setRecruitmentType(node.path("recruitmentType").asText(""))
                .setRecruitmentTarget(node.path("recruitmentTarget").asText(""))
                .setPosition(node.path("position").asText(""))
                .setDeliveryProgress(node.path("deliveryProgress").asText(""))
                .setDeadline(node.path("deadline").asText(""))
                .setRelatedLink(node.path("relatedLink").asText(""))
                .setJobAnnouncement(node.path("jobAnnouncement").asText(""))
                .setInternalReferralCode(node.path("internalReferralCode").asText(""))
                .setRemarks(node.path("remarks").asText(""))
                .setState(node.path("state").asInt())
                .setLastUpdatedTime(date(node, "lastUpdatedTime"))
                .setCreateTime(date(node, "createTime"))
                .setUpdateTime(date(node, "updateTime"));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Long time(Date date) {
        return date == null ? null : date.getTime();
    }

    private Date date(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asLong(0) <= 0 ? new Date(0) : new Date(value.asLong());
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }
}
