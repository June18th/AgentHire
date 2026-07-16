package com.git.hui.jobclaw.util;


import cn.hutool.core.util.NumberUtil;

import java.security.SecureRandom;

/**
 * @author YiHui
 * @date 2022/8/15
 */
public class CodeGenerateUtil {
    public static final Integer CODE_LEN = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String genCode(int cnt) {
        return String.format("%0" + CODE_LEN + "d", RANDOM.nextInt(1_000_000));
    }

    public static boolean isVerifyCode(String content) {
        return NumberUtil.isNumber(content) && content.length() == CodeGenerateUtil.CODE_LEN;
    }
}
