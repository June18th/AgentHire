package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.user.dao.entity.RbacRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RbacRoleRepository extends JpaRepository<RbacRoleEntity, Long> {
    List<RbacRoleEntity> findByState(Integer state);
}
