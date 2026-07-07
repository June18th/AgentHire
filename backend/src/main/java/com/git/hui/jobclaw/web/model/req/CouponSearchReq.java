package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author YiHui
 * @date 2025/7/17
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CouponSearchReq extends PageReq {
    /**
     * 根据code进行查询
     */
    private String couponCode;
}
