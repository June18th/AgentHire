package com.git.hui.jobclaw.agents.jobfetch.service;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JobFetchTaskServiceTest {

    @Test
    void generatesUniqueDatabaseSafeTaskIds() {
        var ids = IntStream.range(0, 10_000)
                .mapToObj(ignored -> JobFetchTaskService.generateTaskId())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> {
            assertThat(id).startsWith("job_");
            assertThat(id.length()).isLessThanOrEqualTo(64);
        });
    }
}
