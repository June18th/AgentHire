package com.git.hui.jobclaw.core.router.intent.impl;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAgentRegistryAccessTest {

    @Test
    void normalUserCannotResolveAdminAgent() {
        DefaultAgentRegistry registry = new DefaultAgentRegistry();
        BizAgent defaultAgent = agent(PresetAgentIntro.DEFAULT, AgentPermission.TOTAL);
        BizAgent adminAgent = agent(PresetAgentIntro.COLLECT, AgentPermission.ADMIN);
        registry.register(defaultAgent);
        registry.register(adminAgent);

        assertThat(registry.getAgentForUser("guest-user", PresetAgentIntro.COLLECT.getAgentId())).isEmpty();
        assertThat(registry.getDefaultAgentForUser("guest-user")).contains(defaultAgent);
    }

    private BizAgent agent(PresetAgentIntro intro, AgentPermission permission) {
        BizAgent agent = mock(BizAgent.class);
        when(agent.getAgentIntro()).thenReturn(intro);
        when(agent.permission()).thenReturn(permission);
        when(agent.isAvailable()).thenReturn(true);
        return agent;
    }
}
