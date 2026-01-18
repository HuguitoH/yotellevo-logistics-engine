package edu.msmk.clases.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class NettyConfig {

    @PostConstruct
    public void init() {
        System.setProperty("io.netty.noUnsafe", "true");
        System.setProperty("io.netty.tryReflectionSetAccessible", "false");
        log.info("Netty configurado sin sun.misc.Unsafe");
    }
}