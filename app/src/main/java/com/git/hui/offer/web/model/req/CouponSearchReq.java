package com.git.hui.offer.web.model.req;

import lombok.Data;

/**
 * @author YiHui
 * @date 2025/7/17
 */
@Data
public class CouponSearchReq extends PageReq {
    /**
     * 根据code进行查询
     */
    private String couponCode;
}
