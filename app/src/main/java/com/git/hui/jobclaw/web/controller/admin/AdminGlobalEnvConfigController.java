package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.configs.dao.entity.GlobalEnvConfigEntity;
import com.git.hui.jobclaw.configs.service.GlobalEnvConfigService;
import com.git.hui.jobclaw.web.model.PageListVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 查询配置列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 配置列表
     */
    @Operation(summary = "查询配置列表")
    @GetMapping("/list")
    public PageListVo<GlobalEnvConfigEntity> listConfigs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return configService.listConfigsPage(page, size);
    }

    /**
     * 根据ID查询配置
     *
     * @param id 配置ID
     * @return 配置实体
     */
    @Operation(summary = "根据ID查询配置")
    @GetMapping("/{id}")
    public GlobalEnvConfigEntity getConfig(@PathVariable Long id) {
        return configService.getByKey(String.valueOf(id))
                .orElseThrow(() -> new RuntimeException("配置不存在"));
    }

    /**
     * 保存或更新配置
     *
     * @param entity 配置实体
     * @return 保存后的实体
     */
    @Operation(summary = "保存或更新配置")
    @PostMapping("/save")
    public GlobalEnvConfigEntity saveConfig(@RequestBody GlobalEnvConfigEntity entity) {
        return configService.saveConfig(entity);
    }

    /**
     * 删除配置
     *
     * @param id 配置ID
     * @return 是否成功
     */
    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public boolean deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return true;
    }

    /**
     * 启用/禁用配置
     *
     * @param id      配置ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    @Operation(summary = "启用/禁用配置")
    @PutMapping("/{id}/enabled")
    public boolean updateEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        configService.updateEnabled(id, enabled);
        return true;
    }

    /**
     * 批量保存配置
     *
     * @param entities 配置实体列表
     * @return 保存后的实体列表
     */
    @Operation(summary = "批量保存配置")
    @PostMapping("/batch-save")
    public List<GlobalEnvConfigEntity> batchSaveConfigs(@RequestBody List<GlobalEnvConfigEntity> entities) {
        return configService.batchSaveConfigs(entities);
    }
}
