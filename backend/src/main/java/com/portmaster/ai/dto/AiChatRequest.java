package com.portmaster.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 对话请求（支持运行时覆盖厂商参数）
 */
@Data
public class AiChatRequest {

    /** 预置厂商 id，如 deepseek / ollama / custom */
    private String providerId;

    @NotBlank
    private String message;

    /** 是否注入当前端口扫描上下文 */
    private boolean includeContext = true;

    /** 可选：覆盖 API Key（前端设置页传入，不落盘） */
    private String apiKey;

    /** 可选：覆盖 base-url */
    private String baseUrl;

    /** 可选：覆盖模型名 */
    private String model;

    /** 多轮历史（可选） */
    private List<AiMessageDTO> history;
}
