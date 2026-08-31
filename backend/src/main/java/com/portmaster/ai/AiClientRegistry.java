package com.portmaster.ai;

import com.portmaster.ai.dto.AiProviderInfoDTO;
import com.portmaster.config.PortMasterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多厂商 ChatClient 路由：按 providerId 构建客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClientRegistry {

    private final PortMasterProperties properties;
    private final AiChatModelFactory modelFactory;

    public boolean isEnabled() {
        return properties.getAi().isEnabled();
    }

    public List<AiProviderInfoDTO> listProviders() {
        List<AiProviderInfoDTO> list = new ArrayList<>();
        Map<String, PortMasterProperties.Provider> map = properties.getAi().getProviders();
        if (map == null) {
            return list;
        }
        for (Map.Entry<String, PortMasterProperties.Provider> e : map.entrySet()) {
            PortMasterProperties.Provider p = e.getValue();
            String type = p.getType() == null ? "openai-compatible" : p.getType();
            boolean needsKey = !"ollama".equalsIgnoreCase(type);
            boolean configured = !needsKey || StringUtils.hasText(p.getApiKey());
            if ("custom".equals(e.getKey())) {
                configured = StringUtils.hasText(p.getApiKey()) || StringUtils.hasText(p.getBaseUrl());
            }
            list.add(AiProviderInfoDTO.builder()
                    .id(e.getKey())
                    .type(type)
                    .displayName(StringUtils.hasText(p.getDisplayName()) ? p.getDisplayName() : e.getKey())
                    .baseUrl(p.getBaseUrl())
                    .model(p.getModel())
                    .configured(configured)
                    .needsApiKey(needsKey)
                    .build());
        }
        return list;
    }

    public ChatClient resolve(String providerId, String apiKey, String baseUrl, String model) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI is disabled. Set portmaster.ai.enabled=true");
        }
        String id = StringUtils.hasText(providerId) ? providerId : properties.getAi().getDefaultProvider();
        boolean requestKeyOverride = StringUtils.hasText(apiKey);
        boolean requestBaseOverride = StringUtils.hasText(baseUrl);
        boolean requestModelOverride = StringUtils.hasText(model);
        log.debug("AI resolve start: providerId={}, overrides=[key={}, baseUrl={}, model={}]",
                id, requestKeyOverride, requestBaseOverride, requestModelOverride);

        PortMasterProperties.Provider cfg = modelFactory.mergeWithOverrides(id, apiKey, baseUrl, model);
        log.info("AI resolve merged: {}",
                AiLogSupport.describeProvider(id, cfg.getType(), cfg.getBaseUrl(), cfg.getModel(), cfg.getApiKey()));

        validateReady(id, cfg);
        long start = System.currentTimeMillis();
        ChatClient client = ChatClient.builder(modelFactory.create(cfg)).build();
        log.debug("AI ChatClient created in {}ms for provider={}", System.currentTimeMillis() - start, id);
        return client;
    }

    public String getDefaultProviderId() {
        return properties.getAi().getDefaultProvider();
    }

    public String getSystemPrompt() {
        return properties.getAi().getSystemPrompt();
    }

    private void validateReady(String id, PortMasterProperties.Provider cfg) {
        String type = cfg.getType() == null ? "" : cfg.getType().toLowerCase();
        if ("ollama".equals(type)) {
            if (!StringUtils.hasText(cfg.getBaseUrl())) {
                log.warn("AI validate failed: ollama baseUrl missing");
                throw new IllegalArgumentException("Ollama baseUrl is required");
            }
            log.debug("AI validate ok: ollama baseUrl={} model={}", cfg.getBaseUrl(), cfg.getModel());
            return;
        }
        if (!StringUtils.hasText(cfg.getApiKey())) {
            log.warn("AI validate failed: provider={} missing apiKey", id);
            throw new IllegalArgumentException(
                    "API Key missing for provider '" + id + "'. Set env key or pass apiKey in request / settings.");
        }
        if (!StringUtils.hasText(cfg.getBaseUrl()) && !"openai".equals(type) && !"deepseek".equals(type)) {
            log.warn("AI validate failed: provider={} missing baseUrl", id);
            throw new IllegalArgumentException("baseUrl missing for provider '" + id + "'");
        }
        log.debug("AI validate ok: provider={} type={}", id, type);
    }
}
