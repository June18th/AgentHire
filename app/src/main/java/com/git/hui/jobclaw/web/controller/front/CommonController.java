package com.git.hui.jobclaw.web.controller.front;

import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.core.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.web.model.res.CommonDictVo;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author YiHui
 * @date 2025/7/21
 */
@Permission(role = UserRoleEnum.ALL)
@RestController
@RequestMapping(path = "/api/common")
public class CommonController {
    private final CommonDictService commonDictService;

    public CommonController(CommonDictService commonDictService) {
        this.commonDictService = commonDictService;
    }

    @RequestMapping(path = "dict", produces = "application/json;charset=UTF-8")
    public List<CommonDictVo> dicts() {
        return commonDictService.queryPublicDictList();
    }

}
