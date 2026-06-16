package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import com.git.hui.jobclaw.llm.service.LlmUsageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/user/llm-usage")
public class UserLlmUsageController {
    private final LlmUsageService service;

    public UserLlmUsageController(LlmUsageService service) {
        this.service = service;
    }

    private String uid() {
        return String.valueOf(ReqInfoContext.getReqInfo().getUserId());
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview(uid());
    }

    @GetMapping("/trends")
    public Map<String, Object> trends() {
        return service.overview(uid());
    }

    @GetMapping("/calls")
    public PageListVo<LlmInvocationEntity> calls(String agent, String operation, String outcome, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.calls(uid(), agent, operation, outcome, page, size);
        result.getList().forEach(this::sanitize);
        return result;
    }

    @GetMapping("/calls/{id}")
    public LlmInvocationEntity detail(@PathVariable String id) {
        return sanitize(service.detail(id, uid()));
    }

    private LlmInvocationEntity sanitize(LlmInvocationEntity e) {
        e.setConversationId(null);
        e.setErrorMessage(null);
        e.setJobClawUserId(null);
        return e;
    }
}
