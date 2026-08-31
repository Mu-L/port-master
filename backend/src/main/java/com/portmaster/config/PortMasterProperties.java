package com.portmaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port Master 全局配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "portmaster")
public class PortMasterProperties {

    private Monitor monitor = new Monitor();
    private Scan scan = new Scan();
    private Ssh ssh = new Ssh();
    private Ai ai = new Ai();

    @Data
    public static class Monitor {
        /** 监控轮询间隔（毫秒），前端可读取 */
        private long pollIntervalMs = 5000;
    }

    @Data
    public static class Scan {
        /** 扫描结果缓存 TTL（毫秒），0 表示不缓存 */
        private long cacheTtlMs = 3000;
    }

    @Data
    public static class Ssh {
        /** SSH 连接超时（毫秒） */
        private int connectTimeoutMs = 10000;
        /** 命令执行超时（秒） */
        private int commandTimeoutSec = 60;
    }

    @Data
    public static class Ai {
        /** 是否启用 AI 助手 */
        private boolean enabled = true;
        /** 默认厂商 id */
        private String defaultProvider = "openai";
        /** 系统提示词 */
        private String systemPrompt = "你是 Port Master 的端口诊断助手。";
        /** 预置厂商 */
        private Map<String, Provider> providers = new LinkedHashMap<>();
    }

    @Data
    public static class Provider {
        /**
         * openai | deepseek | ollama | openai-compatible
         * deepseek / openai 均走 OpenAI 协议客户端，仅默认 base-url 不同
         */
        private String type = "openai-compatible";
        private String displayName = "";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private Double temperature = 0.3;
        private Integer maxTokens = 2048;
    }
}
