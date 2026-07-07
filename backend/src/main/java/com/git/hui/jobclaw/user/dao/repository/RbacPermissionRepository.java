package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.user.dao.entity.RbacPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbacPermissionRepository extends JpaRepository<RbacPermissionEntity, Long> {
    List<RbacPermissionEntity> findByState(Integer state);
}
