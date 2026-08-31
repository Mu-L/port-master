package com.portmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Port Master 应用入口
 * 端口与进程管理工具 - 跨平台 B/S 架构
 */
@SpringBootApplication
@EnableScheduling
public class PortMasterApplication {

    public static void main(String[] args) {
        // 关闭 JMX，避免 IDE/Agent 触发 RMI TCP Accept 噪声日志
        System.setProperty("spring.jmx.enabled", "false");
        System.setProperty("com.sun.management.jmxremote", "false");
        SpringApplication.run(PortMasterApplication.class, args);
    }
}
