package com.git.hui.offer.web.controller.admin;

import com.git.hui.offer.agents.AgentExecutor;
import com.git.hui.offer.agents.OcAgentState;
import com.git.hui.offer.components.async.AsyncUtil;
import com.git.hui.offer.components.context.ReqInfoContext;
import com.git.hui.offer.constants.gather.GatherModelEnum;
import com.git.hui.offer.constants.gather.GatherTargetTypeEnum;
import com.git.hui.offer.constants.gather.GatherTaskStateEnum;
import com.git.hui.offer.constants.user.LoginConstants;
import com.git.hui.offer.constants.user.permission.Permission;
import com.git.hui.offer.constants.user.permission.UserRoleEnum;
import com.git.hui.offer.gather.dao.entity.GatherTaskEntity;
import com.git.hui.offer.gather.model.GatherFileBo;
import com.git.hui.offer.gather.model.GatherTaskSaveBo;
import com.git.hui.offer.gather.service.GatherTaskService;
import com.git.hui.offer.gather.service.OfferGatherService;
import com.git.hui.offer.util.json.IntBaseEnum;
import com.git.hui.offer.util.json.StringBaseEnum;
import com.git.hui.offer.web.model.PageListVo;
import com.git.hui.offer.web.model.req.GatherReq;
import com.git.hui.offer.web.model.req.GatherTaskSearchReq;
import com.git.hui.offer.web.model.res.GatherVo;
import com.git.hui.offer.web.model.res.TaskVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
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

    private final AgentExecutor agentExecutor;


    @Autowired
    public AdminOfferGatherController(OfferGatherService offerGatherService, GatherTaskService gatherTaskService, AgentExecutor agentExecutor) {
        this.offerGatherService = offerGatherService;
        this.gatherTaskService = gatherTaskService;
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
    public Boolean asyncSubmit(GatherReq req, HttpServletRequest request) throws Exception {
        MultipartFile file = null;
        if (request instanceof MultipartHttpServletRequest) {
            file = ((MultipartHttpServletRequest) request).getFile("file");
        }
        GatherTargetTypeEnum type = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.type());
        GatherTaskSaveBo saveBo = new GatherTaskSaveBo(type, req.model(), req.content(), file);
        return gatherTaskService.addTask(saveBo) != null;
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
        GatherModelEnum model = StringBaseEnum.getEnumByCode(GatherModelEnum.class, req.getModel());
        if (model == null) {
            req.setModel(null);
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

    @GetMapping(path = "/agentRun")
    public OcAgentState agentRun(Long taskId) {
        GatherTaskEntity task = gatherTaskService.getTask(taskId);
        OcAgentState state = agentExecutor.invoke(task);
        return state;
    }

    /**
     * 校招派Agent运行
     *
     * @param req
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping(path = "agentSubmit", produces = {org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE})
    public SseEmitter agentSubmit(GatherReq req, HttpServletRequest request) throws Exception {
        MultipartFile file = null;
        if (request instanceof MultipartHttpServletRequest) {
            file = ((MultipartHttpServletRequest) request).getFile("file");
        }
        SseEmitter sseEmitter = new SseEmitter(LoginConstants.SSE_EXPIRE_TIME);
        ReqInfoContext.getReqInfo().addContextVar(ReqInfoContext.REQ_INFO_KEY, sseEmitter);

        GatherTargetTypeEnum type = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.type());
        GatherTaskSaveBo saveBo = new GatherTaskSaveBo(type, req.model(), req.content(), file);
        GatherTaskEntity task = gatherTaskService.directAddTask(saveBo);

        // 异步执行任务
//        AsyncUtil.submit(() -> agentExecutor.invoke(task));
        agentExecutor.invoke(task);
        return sseEmitter;
    }
}
