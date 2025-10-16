package com.git.hui.offer;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.git.hui.offer.web.config.SiteConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Schedulers;

@Slf4j
@SpringBootApplication
@EnableJpaRepositories
@EntityScan
@EnableAsync
@EnableScheduling
@ServletComponentScan
public class AiOcApplication implements ApplicationRunner {
    @Value("${server.port:8080}")
    private Integer webPort;
    @Autowired
    private SiteConfig siteConfig;

    public static void main(String[] args) {
        initReactorExecutorService();
        SpringApplication.run(AiOcApplication.class, args);
    }


    /**
     * 在项目启动之前，注册Reactor 线程池装饰器，使用阿里的TTL进行包装，避免MCP Server在响应时，因为Reactor的异步执行，导致上下文ReqInfoContext丢失
     */
    private static void initReactorExecutorService() {
        // 注册ExecutorService装饰器，针对boundedElastic类型的调度器
        Schedulers.addExecutorServiceDecorator(
                "boundedElastic", // 目标调度器的名称（与Reactor默认boundedElastic名称匹配）
                (schedulerName, executorService) -> {
                    // 用TtlExecutors包装原始线程池，增强TTL上下文传递能力
                    if (log.isDebugEnabled()) {
                        log.debug("schedulerName -> {}", schedulerName);
                    }
                    return TtlExecutors.getTtlScheduledExecutorService(executorService);
                }
        );
    }

    @Override
    public void run(ApplicationArguments args) {
        if (webPort != null) {
            String url = siteConfig.getWebSiteHost() + ":" + webPort;
            log.info("启动成功，点击进入首页: {}", url);
        }
    }
}
