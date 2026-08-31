package com.portmaster.ai;

import com.portmaster.config.PortMasterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 按厂商配置创建 Spring AI ChatModel（OpenAI 兼容 + Ollama）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatModelFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private final PortMasterProperties properties;

    public ChatModel create(PortMasterProperties.Provider cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("AI provider config is null");
        }
        String type = cfg.getType() == null ? "openai-compatible" : cfg.getType().trim().toLowerCase();
        log.debug("AI ChatModel create: type={}, model={}, baseUrl={}",
                type, cfg.getModel(), cfg.getBaseUrl());
        return switch (type) {
            case "ollama" -> buildOllamaViaOpenAiCompat(cfg);
            case "openai", "deepseek", "openai-compatible" -> buildOpenAiCompatible(cfg, type);
            default -> {
                log.error("AI unsupported provider type: {}", type);
                throw new IllegalArgumentException("Unsupported AI provider type: " + type);
            }
        };
    }

    private ChatModel buildOllamaViaOpenAiCompat(PortMasterProperties.Provider cfg) {
        PortMasterProperties.Provider compat = new PortMasterProperties.Provider();
        compat.setType("openai-compatible");
        compat.setDisplayName(cfg.getDisplayName());
        compat.setBaseUrl(StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl() : "http://localhost:11434");
        compat.setApiKey(StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey() : "ollama");
        compat.setModel(StringUtils.hasText(cfg.getModel()) ? cfg.getModel() : "qwen3-vl:4b");
        compat.setTemperature(cfg.getTemperature());
        compat.setMaxTokens(cfg.getMaxTokens());
        log.info("AI ollama -> openai-compatible: baseUrl={} normalized={} model={}",
                compat.getBaseUrl(), normalizeOpenAiBaseUrl(compat.getBaseUrl()), compat.getModel());
        return buildOpenAiCompatible(compat, "openai-compatible");
    }

    @SuppressWarnings("unused")
    private ChatModel buildOllamaNative(PortMasterProperties.Provider cfg) {
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl() : "http://localhost:11434";
        String model = StringUtils.hasText(cfg.getModel()) ? cfg.getModel() : "qwen3-vl:4b";
        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder())
                .build();
        OllamaOptions options = OllamaOptions.builder()
                .model(model)
                .temperature(cfg.getTemperature() != null ? cfg.getTemperature() : 0.3)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildOpenAiCompatible(PortMasterProperties.Provider cfg, String type) {
        String baseUrl = resolveBaseUrl(cfg, type);
        String normalized = normalizeOpenAiBaseUrl(baseUrl);
        String apiKey = StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey() : "sk-placeholder";
        String model = StringUtils.hasText(cfg.getModel()) ? cfg.getModel() : "gpt-4o-mini";

        log.info("AI openai-compatible client: type={}, baseUrl={} -> {}, model={}, apiKey={}, readTimeout={}s",
                type, baseUrl, normalized, model, AiLogSupport.maskApiKey(apiKey), READ_TIMEOUT.getSeconds());

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(normalized)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder())
                .build();

        OpenAiChatOptions.Builder opt = OpenAiChatOptions.builder()
                .model(model)
                .temperature(cfg.getTemperature() != null ? cfg.getTemperature() : 0.3);
        if (cfg.getMaxTokens() != null && cfg.getMaxTokens() > 0) {
            opt.maxTokens(cfg.getMaxTokens());
        }

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(opt.build())
                .build();
    }

    private RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory);
    }

    private String resolveBaseUrl(PortMasterProperties.Provider cfg, String type) {
        if (StringUtils.hasText(cfg.getBaseUrl())) {
            return cfg.getBaseUrl().trim();
        }
        return switch (type) {
            case "openai" -> "https://api.openai.com";
            case "deepseek" -> "https://api.deepseek.com";
            default -> throw new IllegalArgumentException("baseUrl is required for openai-compatible provider");
        };
    }

    static String normalizeOpenAiBaseUrl(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/v1")) {
            u = u.substring(0, u.length() - 3);
        }
        return u;
    }

    public PortMasterProperties.Provider mergeWithOverrides(
            String providerId,
            String apiKey,
            String baseUrl,
            String model) {
        PortMasterProperties.Provider base = properties.getAi().getProviders().get(providerId);
        if (base == null) {
            log.warn("AI unknown providerId={}", providerId);
            throw new IllegalArgumentException("Unknown AI provider: " + providerId);
        }
        PortMasterProperties.Provider merged = new PortMasterProperties.Provider();
        merged.setType(base.getType());
        merged.setDisplayName(base.getDisplayName());
        merged.setBaseUrl(StringUtils.hasText(baseUrl) ? baseUrl : base.getBaseUrl());
        merged.setApiKey(StringUtils.hasText(apiKey) ? apiKey : base.getApiKey());
        merged.setModel(StringUtils.hasText(model) ? model : base.getModel());
        merged.setTemperature(base.getTemperature());
        merged.setMaxTokens(base.getMaxTokens());

        log.debug("AI merge overrides providerId={}: baseUrl={} model={} apiKey={}",
                providerId,
                StringUtils.hasText(baseUrl) ? "request" : "config",
                StringUtils.hasText(model) ? "request" : "config",
                StringUtils.hasText(apiKey) ? "request" : "config");
        return merged;
    }
}
