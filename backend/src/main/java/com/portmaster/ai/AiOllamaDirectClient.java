package com.portmaster.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portmaster.config.PortMasterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * 直连 Ollama OpenAI 兼容接口，用于 Spring AI 丢弃 reasoning/thinking 字段时的兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOllamaDirectClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;

    public String chat(PortMasterProperties.Provider cfg, List<Message> messages) {
        String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());
        String url = baseUrl + "/v1/chat/completions";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("stream", false);
        if (cfg.getMaxTokens() != null && cfg.getMaxTokens() > 0) {
            body.put("max_tokens", cfg.getMaxTokens());
        }
        if (cfg.getTemperature() != null) {
            body.put("temperature", cfg.getTemperature());
        }
        body.set("messages", toMessageArray(messages));

        log.debug("AI ollama direct fallback POST {} model={} messages={}", url, cfg.getModel(), messages.size());
        RestClient client = restClient();
        String raw = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        return extractAssistantText(parseMessage(raw));
    }

    private RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }

    private ArrayNode toMessageArray(List<Message> messages) {
        ArrayNode array = objectMapper.createArrayNode();
        for (Message message : messages) {
            ObjectNode node = objectMapper.createObjectNode();
            if (message instanceof SystemMessage) {
                node.put("role", "system");
                node.put("content", message.getText());
            } else if (message instanceof UserMessage) {
                node.put("role", "user");
                node.put("content", message.getText());
            } else if (message instanceof AssistantMessage) {
                node.put("role", "assistant");
                node.put("content", message.getText());
            } else {
                node.put("role", "user");
                node.put("content", message.getText());
            }
            array.add(node);
        }
        return array;
    }

    private JsonNode parseMessage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message");
            }
            return root.path("message");
        } catch (Exception e) {
            log.warn("AI ollama direct fallback parse failed: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    static String extractAssistantText(JsonNode message) {
        if (message == null || message.isMissingNode()) {
            return "";
        }
        String content = textOrEmpty(message, "content");
        if (StringUtils.hasText(content)) {
            return content;
        }
        String reasoning = textOrEmpty(message, "reasoning");
        if (StringUtils.hasText(reasoning)) {
            return reasoning;
        }
        return textOrEmpty(message, "thinking");
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/v1")) {
            u = u.substring(0, u.length() - 3);
        }
        return u;
    }
}
