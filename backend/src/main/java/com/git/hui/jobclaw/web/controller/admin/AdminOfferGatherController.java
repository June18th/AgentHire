package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.agents.AgentExecutor;
import com.git.hui.jobclaw.agents.OcAgentState;
import com.git.hui.jobclaw.components.async.AsyncUtil;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.constants.gather.GatherTaskStateEnum;
import com.git.hui.jobclaw.constants.user.LoginConstants;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.model.GatherFileBo;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import com.git.hui.jobclaw.gather.service.GatherSourceService;
import com.git.hui.jobclaw.gather.service.GatherTaskNotifyService;
import com.git.hui.jobclaw.gather.service.GatherTaskService;
import com.git.hui.jobclaw.gather.service.OfferGatherService;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.GatherReq;
import com.git.hui.jobclaw.web.model.req.GatherSourceSearchReq;
import com.git.hui.jobclaw.web.model.req.GatherTaskSearchReq;
import com.git.hui.jobclaw.web.model.res.GatherSourceVo;
import com.git.hui.jobclaw.web.model.res.GatherVo;
import com.git.hui.jobclaw.web.model.res.TaskVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 获取offer信息的入口
 *
 * @author YiHui
 * @date 2025/7/14
 */
@Permission(role = UserRoleEnum.ADMIN)
@RestController
@RequestMapping(path = "/api/admin/gather")
public class AdminOfferGatherController {
    private final OfferGatherService offerGatherService;

    private final GatherTaskService gatherTaskService;

    private final GatherSourceService gatherSourceService;

    private final GatherTaskNotifyService gatherTaskNotifyService;

    private final AgentExecutor agentExecutor;


    @Autowired
    public AdminOfferGatherController(OfferGatherService offerGatherService, GatherTaskService gatherTaskService,
                                      GatherSourceService gatherSourceService, GatherTaskNotifyService gatherTaskNotifyService,
                                      AgentExecutor agentExecutor) {
        this.offerGatherService = offerGatherService;
        this.gatherTaskService = gatherTaskService;
        this.gatherSourceService = gatherSourceService;
        this.gatherTaskNotifyService = gatherTaskNotifyService;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 同步执行
     *
     * @param req
     * @param request
     * @return
     * @throws IOException
     */
    @RequestMapping(path = "submit")
    public GatherVo submit(GatherReq req, HttpServletRequest request) throws IOException {
        GatherFileBo fileBo = null;
        if (request instanceof MultipartHttpServletRequest) {
            MultipartFile file = ((MultipartHttpServletRequest) request).getFile("file");
            fileBo = new GatherFileBo(file.getBytes(), file.getContentType(), file.getName());
        }
        GatherVo vo = offerGatherService.gatherInfo(req, fileBo);
        return vo;
    }


    /**
     * 提交任务，异步执行
     *
     * @param req
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping(path = "asyncSubmit")
    public Long asyncSubmit(GatherReq req, HttpServletRequest request) throws Exception {
        if (!StringUtils.hasText(req.model())) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "请先选择模型后再提交任务");
        }
        MultipartFile file = null;
        if (request instanceof MultipartHttpServletRequest) {
            file = ((MultipartHttpServletRequest) request).getFile("file");
        }
        GatherTargetTypeEnum type = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.type());
        GatherTaskSaveBo saveBo = new GatherTaskSaveBo(type, req.model(), req.content(), file);
        GatherTaskEntity task = gatherTaskService.addTask(saveBo, GatherSourceService.OWNER_ADMIN, GatherSourceService.RUNNER_DRAFT_ONLY);
        return task == null ? null : task.getId();
    }

    @RequestMapping(path = "list")
    public PageListVo<TaskVo> list(GatherTaskSearchReq req) {
        // 不匹配时，查询全部
        GatherTaskStateEnum state = IntBaseEnum.getEnumByCode(GatherTaskStateEnum.class, req.getState());
        if (state == null) {
            req.setState(null);
        }
        GatherTargetTypeEnum type = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.getType());
        if (type == null) {
            req.setType(null);
        }
        return gatherTaskService.searchList(req);
    }

    /**
     * 任务重跑
     *
     * @param taskId
     * @return
     */
    @RequestMapping("/reRun")
    public Boolean reRun(Long taskId) {
        Assert.notNull(taskId, "taskId can not be null");
        return gatherTaskService.resetTaskState(taskId);
    }

    /**
     * Agent 作业任务重跑：创建新任务并返回 taskId，前端需调用 autoInvoke 执行。
     */
    @RequestMapping("/agentReRun")
    public Long agentReRun(Long taskId) {
        Assert.notNull(taskId, "taskId can not be null");
        return gatherTaskService.reRunAgentTask(taskId);
    }

    @GetMapping(path = "/agentRun")
    public OcAgentState agentRun(Long taskId) {
        GatherTaskEntity task = gatherTaskService.getTask(taskId);
        OcAgentState state = agentExecutor.invoke(task);
        return state;
    }

    /**
     * 求职派Agent运行
     *
     * @param req
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping(path = "agentSubmit")
    public Long agentSubmit(GatherReq req, HttpServletRequest request) throws Exception {
        MultipartFile file = null;
        if (request instanceof MultipartHttpServletRequest) {
            file = ((MultipartHttpServletRequest) request).getFile("file");
        }

        GatherTargetTypeEnum type = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.type());
        GatherTaskSaveBo saveBo = new GatherTaskSaveBo(type, req.model(), req.content(), file);
        GatherTaskEntity task = gatherTaskService.directAddTask(saveBo, GatherSourceService.OWNER_AGENT, GatherSourceService.RUNNER_AGENT);
        return task.getId();
    }

    @RequestMapping(path = "source/list")
    public PageListVo<GatherSourceVo> sourceList(GatherSourceSearchReq req) {
        return gatherSourceService.searchList(req);
    }

    @RequestMapping(path = "source/reRun")
    public Long sourceReRun(Long sourceId, String model) throws Exception {
        Assert.notNull(sourceId, "sourceId can not be null");
        var source = gatherSourceService.getSource(sourceId);
        Assert.isTrue(GatherSourceService.STATUS_ACTIVE.equals(source.getStatus()), "source is not active");
        if (GatherSourceService.RUNNER_IM_FETCH.equals(source.getRunnerType())) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "IM 抓取来源由用户对话触发，不支持后台重新采集");
        }
        String taskModel = StringUtils.hasText(model) ? model : source.getLastModel();
        boolean triggerOfferGather = GatherSourceService.isOfferGatherRunnable(source.getRunnerType());
        GatherTaskEntity task = gatherTaskService.addTaskFromSource(source, taskModel, triggerOfferGather);
        return task.getId();
    }

    @RequestMapping(path = "source/status")
    public GatherSourceVo sourceStatus(Long sourceId, String status) {
        Assert.notNull(sourceId, "sourceId can not be null");
        var source = gatherSourceService.updateStatus(sourceId, status);
        GatherSourceSearchReq req = new GatherSourceSearchReq();
        req.setId(source.getId());
        return gatherSourceService.searchList(req).getList().stream().findFirst().orElse(null);
    }

    @GetMapping(path = "autoInvoke", produces = {org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE})
    public SseEmitter autoInvoke(Long taskId) {
        SseEmitter sseEmitter = new SseEmitter(LoginConstants.SSE_EXPIRE_TIME);
        ReqInfoContext.getReqInfo().addContextVar(ReqInfoContext.REQ_INFO_KEY, sseEmitter);
        // 异步执行任务
        GatherTaskEntity task = gatherTaskService.getTask(taskId);
        AsyncUtil.submit(() -> agentExecutor.invoke(task));
        return sseEmitter;
    }

    @GetMapping(path = "taskStream", produces = {org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE})
    public SseEmitter taskStream(Long taskId) {
        Assert.notNull(taskId, "taskId can not be null");
        gatherTaskService.getTask(taskId);
        return gatherTaskNotifyService.subscribe(taskId);
    }
}
