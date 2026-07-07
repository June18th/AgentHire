package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.application.service.JobApplicationService;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.web.model.req.JobApplicationEventSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationFollowUpReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationSaveReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationSearchReq;
import com.git.hui.jobclaw.web.model.req.JobApplicationStatusUpdateReq;
import com.git.hui.jobclaw.web.model.res.JobApplicationBriefVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationEventVo;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping(path = "/api/user/applications")
public class JobApplicationController {
    private final JobApplicationService applicationService;

    public JobApplicationController(JobApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping(path = "list")
    public PageListVo<JobApplicationVo> list(JobApplicationSearchReq req) {
        return applicationService.list(currentUserId(), req);
    }

    @GetMapping(path = "detail")
    public JobApplicationVo detail(Long id) {
        return applicationService.detail(currentUserId(), id);
    }

    @PostMapping(path = "save")
    public JobApplicationVo save(@RequestBody JobApplicationSaveReq req) {
        return applicationService.save(currentUserId(), req);
    }

    @PostMapping(path = "status")
    public JobApplicationVo changeStatus(@RequestBody JobApplicationStatusUpdateReq req) {
        return applicationService.changeStatus(currentUserId(), req);
    }

    @PostMapping(path = "reopen")
    public JobApplicationVo reopen(@RequestBody JobApplicationStatusUpdateReq req) {
        return applicationService.reopen(currentUserId(), req);
    }

    @GetMapping(path = "by-jobs")
    public List<JobApplicationVo> byJobs(@RequestParam List<Long> jobIds) {
        return applicationService.listByJobIds(currentUserId(), jobIds);
    }

    @GetMapping(path = "action-items")
    public List<JobApplicationVo> actionItems(@RequestParam(required = false) Integer limit) {
        return applicationService.actionItems(currentUserId(), limit);
    }

    @GetMapping(path = "brief")
    public JobApplicationBriefVo brief(@RequestParam(required = false) Integer limit) {
        return applicationService.brief(currentUserId(), limit);
    }

    @PostMapping(path = "delete")
    public boolean delete(Long id) {
        return applicationService.delete(currentUserId(), id);
    }

    @GetMapping(path = "events")
    public List<JobApplicationEventVo> events(Long applicationId) {
        return applicationService.listEvents(currentUserId(), applicationId);
    }

    @GetMapping(path = "events/day")
    public List<JobApplicationEventVo> eventsByDay(@RequestParam Long start, @RequestParam Long end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end time are required");
        }
        return applicationService.listEventsByDay(currentUserId(), new java.util.Date(start), new java.util.Date(end));
    }

    @PostMapping(path = "events/save")
    public JobApplicationEventVo saveEvent(@RequestBody JobApplicationEventSaveReq req) {
        return applicationService.addEvent(currentUserId(), req);
    }

    @PostMapping(path = "follow-up/complete")
    public JobApplicationVo completeFollowUp(@RequestBody JobApplicationFollowUpReq req) {
        return applicationService.completeFollowUp(currentUserId(), req);
    }

    private Long currentUserId() {
        return ReqInfoContext.getReqInfo().getUserId();
    }
}
