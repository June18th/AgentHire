package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.configs.service.GlobalEnvConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局环境配置管理Controller
 *
 * @author YiHui
 * @date 2026/4/9
 */
@Tag(name = "全局环境配置管理")
@RestController
@RequestMapping("/api/admin/env-config")
public class AdminGlobalEnvConfigController {

    @Autowired
    private GlobalEnvConfigService configService;

}
