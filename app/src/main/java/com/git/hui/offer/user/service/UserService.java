package com.git.hui.offer.user.service;

import com.git.hui.offer.components.bizexception.BizException;
import com.git.hui.offer.components.bizexception.StatusEnum;
import com.git.hui.offer.components.context.ReqInfoContext;
import com.git.hui.offer.components.context.UserBo;
import com.git.hui.offer.components.env.SpringUtil;
import com.git.hui.offer.components.id.IdUtil;
import com.git.hui.offer.constants.common.BaseStateEnum;
import com.git.hui.offer.constants.user.RechargeLevelEnum;
import com.git.hui.offer.constants.user.permission.UserRoleEnum;
import com.git.hui.offer.user.convert.UserConvert;
import com.git.hui.offer.user.dao.entity.UserEntity;
import com.git.hui.offer.user.dao.repository.UserRepository;
import com.git.hui.offer.user.helper.UserRandomGenHelper;
import com.git.hui.offer.util.DateUtil;
import com.git.hui.offer.web.model.PageListVo;
import com.git.hui.offer.web.model.req.UserSaveReq;
import com.git.hui.offer.web.model.req.UserSearchReq;
import com.git.hui.offer.web.model.res.McpConfigVo;
import com.git.hui.offer.web.model.res.UserVo;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.mcp.server.autoconfigure.McpServerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户服务
 *
 * @author YiHui
 * @date 2025/7/16
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final McpServerProperties mcpServerProperties;

    private final UserInterestService userInterestService;

    public UserService(UserRepository userRepository, McpServerProperties mcpServerProperties, UserInterestService userInterestService) {
        this.userRepository = userRepository;
        this.mcpServerProperties = mcpServerProperties;
        this.userInterestService = userInterestService;
    }

    public UserVo detail(Long userId) {
        Optional<UserEntity> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().getId() == null) {
            throw new BizException(StatusEnum.USER_NOT_EXISTS, userId);
        }

        UserVo vo = UserConvert.toVo(user.get());
        vo.setConfig(buildMcpConfig(user.get()));
        vo.setInterest(userInterestService.getUserInterest(userId));
        return vo;
    }

    private McpConfigVo buildMcpConfig(UserEntity user) {
        McpConfigVo configVo = new McpConfigVo("sse",
                SpringUtil.getSiteConfig().getWebSiteUrl() + mcpServerProperties.getSseEndpoint(),
                mcpServerProperties.getVersion(),
                Map.of("Authorization", "Bearer " + user.getWxId())
        );
        return configVo;
    }

    /**
     * 查询用户列表
     *
     * @param req
     * @return
     */
    public PageListVo<UserVo> searchUserList(UserSearchReq req) {
        if (req.getRole() != null && req.getRole().equals(UserRoleEnum.ALL.getValue())) {
            req.setRole(null);
        }
        PageListVo<UserEntity> res = userRepository.findList(req);
        List<UserVo> list = UserConvert.toVo(res.getList());
        return PageListVo.of(list, res.getTotal(), req.getPage(), req.getSize());
    }


    /**
     * 用户充值成功，自动更新对应的角色和过期时间
     *
     * @param userId   用户id
     * @param vipLevel 充值的会员级别
     * @return
     */
    public boolean updateUserVipInfo(Long userId, RechargeLevelEnum vipLevel) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }

        if (user.getRole().equals(UserRoleEnum.ADMIN.getValue())) {
            // 管理员时，无需刷新过期时间
            return true;
        }

        long lastExpireTime = user.getExpireTime() == null ? System.currentTimeMillis() : user.getExpireTime().getTime();
        return updateUserRole(userId, UserRoleEnum.VIP, lastExpireTime + vipLevel.getMillSeconds());
    }

    /**
     * 更新用户角色和会员到期时间
     *
     * @param userId
     * @param role       用户角色
     * @param expireTime 到期时间
     * @return
     */
    public boolean updateUserRole(Long userId, UserRoleEnum role, Long expireTime) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }
        if (Objects.equals(role, UserRoleEnum.VIP)) {
            // vip用户，要求过期时间存在
            if (expireTime == null) {
                expireTime = System.currentTimeMillis() + RechargeLevelEnum.MONTH.getMillSeconds();
            }
            user.setExpireTime(new Date(expireTime / DateUtil.ONE_DAY_MILL * DateUtil.ONE_DAY_MILL));
        }
        user.setRole(role.getValue());
        user.setUpdateTime(new Date());
        userRepository.saveAndFlush(user);
        return true;
    }

    /**
     * 用户修改自己的信息
     *
     * @param req
     * @return
     */
    public UserVo updateUserInfo(UserSaveReq req) {
        if (!Objects.equals(ReqInfoContext.getReqInfo().getUserId(), req.getUserId())) {
            throw new BizException(StatusEnum.FORBID_ERROR);
        }

        UserEntity user = userRepository.findById(req.getUserId()).orElse(null);
        if (user == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "用户不存在");
        }
        if (StringUtils.isNotBlank(req.getEmail())) {
            user.setEmail(req.getEmail());
        }
        if (StringUtils.isNotBlank(req.getIntro())) {
            user.setIntro(req.getIntro());
        }
        if (StringUtils.isNotBlank(req.getDisplayName())) {
            user.setDisplayName(req.getDisplayName());
        }
        if (StringUtils.isNotBlank(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        user.setUpdateTime(new Date());
        userRepository.saveAndFlush(user);
        return UserConvert.toVo(user);
    }


    @Transactional(rollbackFor = Exception.class)
    public UserBo autoRegisterWxUserInfo(String uuid) {
        UserSaveReq req = new UserSaveReq().setWxId(uuid);
        UserEntity user = registerOrGetUserInfo(req);
        UserBo bo = UserConvert.toBo(user);
        ReqInfoContext.getReqInfo().setUserId(user.getId());
        ReqInfoContext.getReqInfo().setUser(bo);
        return bo;
    }

    /**
     * 没有注册时，先注册一个用户；若已经有，则登录
     *
     * @param req
     */
    private UserEntity registerOrGetUserInfo(UserSaveReq req) {
        UserEntity user = userRepository.findByWxId(req.getWxId());
        if (user == null) {
            return registerByWx(req.getWxId());
        }
        return user;
    }


    /**
     * 微信用户注册
     *
     * @param wxId 微信三方 id
     * @return 用户id
     */
    private UserEntity registerByWx(String wxId) {
        UserEntity user = new UserEntity()
                .setId(IdUtil.genId())
                .setWxId(wxId)
                .setRole(UserRoleEnum.NORMAL.getValue())
                .setCreateTime(new Date())
                .setUpdateTime(new Date())
                .setState(BaseStateEnum.NORMAL_STATE.getValue())
                .setLoginName("")
                .setPassword("")
                .setEmail("")
                .setIntro("")
                .setDisplayName(UserRandomGenHelper.genNickName())
                .setAvatar(UserRandomGenHelper.genAvatar());
        userRepository.saveAndFlush(user);
        return user;
    }

    /**
     * 获取用户信息
     *
     * @param userId
     * @return
     */
    public UserBo getUserBo(Long userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        if (user.getRole().equals(UserRoleEnum.VIP.getValue())
                && System.currentTimeMillis() >= user.getExpireTime().getTime()) {
            // 会员已过期
            user.setRole(UserRoleEnum.NORMAL.getValue());
            userRepository.saveAndFlush(user);
        }
        return UserConvert.toBo(user);
    }

    public UserBo getUserByWxId(String wxId) {
        UserEntity user = userRepository.findByWxId(wxId);
        if (user == null) {
            return null;
        }
        return UserConvert.toBo(user);
    }

    public List<UserBo> getUserByUserIds(List<Long> ids) {
        return userRepository.findByIdIn(ids).stream().map(UserConvert::toBo).toList();
    }
}
