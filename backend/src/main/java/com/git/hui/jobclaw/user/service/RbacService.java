package com.git.hui.jobclaw.user.service;

import com.git.hui.jobclaw.components.id.IdUtil;
import com.git.hui.jobclaw.constants.common.BaseStateEnum;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.user.dao.entity.RbacPermissionEntity;
import com.git.hui.jobclaw.user.dao.entity.RbacRoleEntity;
import com.git.hui.jobclaw.user.dao.entity.RbacUserRoleEntity;
import com.git.hui.jobclaw.user.dao.repository.RbacPermissionRepository;
import com.git.hui.jobclaw.user.dao.repository.RbacRolePermissionRepository;
import com.git.hui.jobclaw.user.dao.repository.RbacRoleRepository;
import com.git.hui.jobclaw.user.dao.repository.RbacUserRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RbacService {
    public static final String ROLE_JOB_SEEKER = "JOB_SEEKER";
    public static final String ROLE_VIP_USER = "VIP_USER";
    public static final String ROLE_RECRUITER = "RECRUITER";
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final RbacRoleRepository roleRepository;
    private final RbacPermissionRepository permissionRepository;
    private final RbacUserRoleRepository userRoleRepository;
    private final RbacRolePermissionRepository rolePermissionRepository;

    public RbacService(RbacRoleRepository roleRepository,
                       RbacPermissionRepository permissionRepository,
                       RbacUserRoleRepository userRoleRepository,
                       RbacRolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<String> listRoleCodes(UserBo user) {
        Set<String> roles = new LinkedHashSet<>();
        if (user != null && user.userId() != null) {
            userRoleRepository.findByUserIdAndState(user.userId(), BaseStateEnum.NORMAL_STATE.getValue())
                    .forEach(item -> roles.add(item.getRoleCode()));
        }

        if (user != null && user.role() != null) {
            roles.addAll(legacyRoles(user.role()));
        }
        return new ArrayList<>(roles);
    }

    public List<String> listPermissionCodes(UserBo user) {
        List<String> roles = listRoleCodes(user);
        if (roles.isEmpty()) {
            return List.of();
        }

        Set<String> permissions = new LinkedHashSet<>();
        rolePermissionRepository.findByRoleCodeInAndState(roles, BaseStateEnum.NORMAL_STATE.getValue())
                .forEach(item -> permissions.add(item.getPermissionCode()));
        if (roles.contains(ROLE_PLATFORM_ADMIN) || roles.contains(ROLE_SUPER_ADMIN)) {
            permissions.add("system:*");
        }
        return new ArrayList<>(permissions);
    }

    public List<GrantedAuthority> buildAuthorities(UserBo user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : listRoleCodes(user)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String permission : listPermissionCodes(user)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        if (user != null && user.role() == UserRoleEnum.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else if (user != null && user.role() == UserRoleEnum.VIP) {
            authorities.add(new SimpleGrantedAuthority("ROLE_VIP"));
        } else if (user != null && user.role() == UserRoleEnum.NORMAL) {
            authorities.add(new SimpleGrantedAuthority("ROLE_NORMAL"));
        }
        return new ArrayList<>(authorities);
    }

    public List<RbacRoleEntity> listRoles() {
        return roleRepository.findByState(BaseStateEnum.NORMAL_STATE.getValue());
    }

    public List<RbacPermissionEntity> listPermissions() {
        return permissionRepository.findByState(BaseStateEnum.NORMAL_STATE.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean grantRole(Long userId, String roleCode) {
        RbacUserRoleEntity existed = userRoleRepository.findByUserIdAndRoleCode(userId, roleCode);
        Date now = new Date();
        if (existed == null) {
            userRoleRepository.save(new RbacUserRoleEntity()
                    .setId(IdUtil.genId())
                    .setUserId(userId)
                    .setRoleCode(roleCode)
                    .setState(BaseStateEnum.NORMAL_STATE.getValue())
                    .setCreateTime(now)
                    .setUpdateTime(now));
            return true;
        }
        if (!BaseStateEnum.NORMAL_STATE.getValue().equals(existed.getState())) {
            existed.setState(BaseStateEnum.NORMAL_STATE.getValue());
            existed.setUpdateTime(now);
            userRoleRepository.save(existed);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean revokeRole(Long userId, String roleCode) {
        RbacUserRoleEntity existed = userRoleRepository.findByUserIdAndRoleCode(userId, roleCode);
        if (existed == null) {
            return true;
        }
        existed.setState(BaseStateEnum.DELETED_STATE.getValue());
        existed.setUpdateTime(new Date());
        userRoleRepository.save(existed);
        return true;
    }

    private List<String> legacyRoles(UserRoleEnum role) {
        return switch (role) {
            case ADMIN -> List.of(ROLE_PLATFORM_ADMIN);
            case VIP -> List.of(ROLE_JOB_SEEKER, ROLE_VIP_USER);
            case NORMAL -> List.of(ROLE_JOB_SEEKER);
            default -> List.of();
        };
    }
}
