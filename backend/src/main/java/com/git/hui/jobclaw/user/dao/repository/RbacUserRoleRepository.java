package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.user.dao.entity.RbacUserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbacUserRoleRepository extends JpaRepository<RbacUserRoleEntity, Long> {
    List<RbacUserRoleEntity> findByUserIdAndState(Long userId, Integer state);

    RbacUserRoleEntity findByUserIdAndRoleCode(Long userId, String roleCode);
}
