package com.git.hui.jobclaw.util;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGenerateUtilTest {

    @Test
    void generatesSixDigitNonDeterministicCodes() {
        var codes = IntStream.range(0, 1_000)
                .mapToObj(CodeGenerateUtil::genCode)
                .toList();

        assertThat(codes).allMatch(CodeGenerateUtil::isVerifyCode);
        assertThat(codes.stream().distinct().count()).isGreaterThan(990);
    }

    @Test
    void rejectsLegacyFourDigitCodes() {
        assertThat(CodeGenerateUtil.isVerifyCode("6666")).isFalse();
        assertThat(CodeGenerateUtil.isVerifyCode("123456")).isTrue();
        assertThat(CodeGenerateUtil.isVerifyCode("12a456")).isFalse();
    }
}
