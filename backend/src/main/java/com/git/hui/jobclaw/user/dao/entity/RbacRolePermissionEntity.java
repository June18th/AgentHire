package com.git.hui.jobclaw.user.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@Entity(name = "rbac_role_permission")
public class RbacRolePermissionEntity {
    @Id
    private Long id;

    @Column(name = "role_code")
    private String roleCode;

    @Column(name = "permission_code")
    private String permissionCode;

    @Column(name = "state")
    private Integer state;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "update_time")
    private Date updateTime;
}
