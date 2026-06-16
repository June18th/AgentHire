package com.git.hui.jobclaw.web.controller.admin;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.llm.dao.entity.LlmInvocationEntity;
import com.git.hui.jobclaw.llm.service.LlmUsageService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Permission(role = UserRoleEnum.ADMIN) @RestController @RequestMapping("/api/admin/llm-monitor")
public class AdminLlmMonitorController {
    private final LlmUsageService service;
    public AdminLlmMonitorController(LlmUsageService service) { this.service = service; }
    @GetMapping("/overview") public Map<String,Object> overview(){ return service.overview(null); }
    @GetMapping("/trends") public Map<String,Object> trends(){ return service.overview(null); }
    @GetMapping("/breakdown") public Map<String,Object> breakdown(){ return service.overview(null); }
    @GetMapping("/calls") public PageListVo<LlmInvocationEntity> calls(String userId,String agent,String operation,String outcome,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return service.calls(userId,agent,operation,outcome,page,size);}
    @GetMapping("/calls/{id}") public Map<String,Object> detail(@PathVariable String id){return service.adminDetail(id);}
}
