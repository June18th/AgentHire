package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.user.dao.entity.RbacRolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbacRolePermissionRepository extends JpaRepository<RbacRolePermissionEntity, Long> {
    List<RbacRolePermissionEntity> findByRoleCodeInAndState(List<String> roleCodes, Integer state);
}
