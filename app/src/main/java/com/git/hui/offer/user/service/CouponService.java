package com.git.hui.offer.user.service;

import com.git.hui.offer.components.bizexception.BizException;
import com.git.hui.offer.components.bizexception.StatusEnum;
import com.git.hui.offer.constants.common.BaseStateEnum;
import com.git.hui.offer.user.convert.CouponConvert;
import com.git.hui.offer.user.dao.entity.CouponEntity;
import com.git.hui.offer.user.dao.repository.CouponRepository;
import com.git.hui.offer.util.RandUtil;
import com.git.hui.offer.web.model.PageListVo;
import com.git.hui.offer.web.model.req.CouponSaveReq;
import com.git.hui.offer.web.model.req.CouponSearchReq;
import com.git.hui.offer.web.model.res.CouponVo;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 优惠券服务
 *
 * @author YiHui
 * @date 2025/8/20
 */
@Slf4j
@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /**
     * 生成优惠券
     *
     * @return
     */
    private String genCoupon() {
        // 随机生成8位的优惠券码
        String couponCode = RandUtil.random(8).toUpperCase();

        if (couponRepository.findByCouponCode(couponCode) != null) {
            // 判断券码是否存在，若存在，则重新生成
            return genCoupon();
        }
        return couponCode;
    }

    /**
     * 保存优惠券
     *
     * @param req
     * @return
     */
    public boolean insertCoupon(CouponSaveReq req) {
        String couponCode = genCoupon();
        CouponEntity couponEntity = CouponConvert.toEntity(req);
        couponEntity.setCouponCode(couponCode);
        couponEntity.setCreateTime(new Date());
        couponEntity.setUpdateTime(new Date());
        couponRepository.saveAndFlush(couponEntity);
        return true;
    }


    /**
     * 更新优惠券信息
     *
     * @param req
     * @return
     */
    public boolean updateCoupon(CouponSaveReq req) {
        // 更新优惠券
        CouponEntity coupon = couponRepository.findById(req.getCouponId()).orElse(null);
        if (coupon == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "优惠券不存在");
        }

        if (req.getCouponCount() != null) {
            coupon.setCouponCount(req.getCouponCount());
        }
        if (req.getCouponType() != null && !StringUtils.isBlank(req.getCouponValue())) {
            CouponConvert.formatCouponValue(coupon, req.getCouponType(), req.getCouponValue());
        }

        coupon.setScope(req.getScope());
        if (req.getStartTime() != null) {
            coupon.setStartTime(new Date(req.getStartTime()));
        }
        if (req.getEndTime() != null) {
            coupon.setEndTime(new Date(req.getEndTime()));
        }
        coupon.setUpdateTime(new Date());
        couponRepository.saveAndFlush(coupon);
        return true;
    }

    /**
     * 删除优惠券
     *
     * @param couponId
     * @return
     */
    public boolean deleteCoupon(Long couponId) {
        CouponEntity coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "优惠券不存在");
        }
        coupon.setState(BaseStateEnum.DELETED_STATE.getValue());
        coupon.setUpdateTime(new Date());
        couponRepository.saveAndFlush(coupon);
        return true;
    }

    /**
     * 优惠券列表查询
     *
     * @return
     */
    public PageListVo<CouponVo> searchList(CouponSearchReq req) {
        PageListVo<CouponEntity> res = couponRepository.findList(req);
        List<CouponVo> list = CouponConvert.toVoList(res.getList());
        return PageListVo.of(list, res.getTotal(), req.getPage(), req.getSize());
    }
}
