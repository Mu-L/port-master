package com.portmaster.ai;

import com.portmaster.ai.dto.AiChatRequest;
import com.portmaster.ai.dto.AiMessageDTO;
import com.portmaster.config.PortMasterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话服务：组装 system + 上下文 + 历史，调用 Spring AI ChatClient
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiClientRegistry registry;
    private final AiContextBuilder contextBuilder;
    private final AiChatModelFactory modelFactory;
    private final AiOllamaDirectClient ollamaDirectClient;

    public String chat(AiChatRequest request) {
        log.debug("AI chat(sync) resolving client...");
        PortMasterProperties.Provider cfg = modelFactory.mergeWithOverrides(
                resolveProviderId(request),
                request.getApiKey(),
                request.getBaseUrl(),
                request.getModel());
        ChatClient client = registry.resolve(
                request.getProviderId(),
                request.getApiKey(),
                request.getBaseUrl(),
                request.getModel());
        List<Message> messages = buildMessages(request);
        log.debug("AI chat(sync) calling model, messageCount={}", messages.size());
        ChatResponse response = client.prompt()
                .messages(messages)
                .call()
                .chatResponse();
        String content = extractText(response);
        if (!StringUtils.hasText(content) && isOllama(cfg)) {
            log.info("AI chat(sync) empty content from Spring AI, trying ollama reasoning fallback");
            content = ollamaDirectClient.chat(cfg, messages);
            log.info("AI chat(sync) ollama fallback replyChars={}", content == null ? 0 : content.length());
        }
        return content == null ? "" : content;
    }

    public Flux<String> stream(AiChatRequest request) {
        log.debug("AI chat(stream) resolving client...");
        ChatClient client = registry.resolve(
                request.getProviderId(),
                request.getApiKey(),
                request.getBaseUrl(),
                request.getModel());
        List<Message> messages = buildMessages(request);
        log.debug("AI chat(stream) calling model, messageCount={}", messages.size());
        return client.prompt()
                .messages(messages)
                .stream()
                .content();
    }

    private List<Message> buildMessages(AiChatRequest request) {
        List<Message> messages = new ArrayList<>();
        StringBuilder system = new StringBuilder(registry.getSystemPrompt());
        if (request.isIncludeContext()) {
            long ctxStart = System.currentTimeMillis();
            String ctx = contextBuilder.buildPortContext();
            system.append("\n\n").append(ctx);
            log.debug("AI port context built in {}ms, chars={}",
                    System.currentTimeMillis() - ctxStart, ctx.length());
        } else {
            log.debug("AI port context skipped (includeContext=false)");
        }
        messages.add(new SystemMessage(system.toString()));
        log.debug("AI system prompt total chars={}", system.length());

        int historyAdded = 0;
        if (request.getHistory() != null) {
            for (AiMessageDTO m : request.getHistory()) {
                if (m == null || !StringUtils.hasText(m.getContent())) {
                    continue;
                }
                if ("assistant".equalsIgnoreCase(m.getRole())) {
                    messages.add(new AssistantMessage(m.getContent()));
                } else {
                    messages.add(new UserMessage(m.getContent()));
                }
                historyAdded++;
            }
        }
        messages.add(new UserMessage(request.getMessage()));
        log.debug("AI messages assembled: history={}, userMessage={}",
                historyAdded, AiLogSupport.truncate(request.getMessage(), 80));
        return messages;
    }

    private String resolveProviderId(AiChatRequest request) {
        return StringUtils.hasText(request.getProviderId())
                ? request.getProviderId()
                : registry.getDefaultProviderId();
    }

    private static boolean isOllama(PortMasterProperties.Provider cfg) {
        return cfg != null && "ollama".equalsIgnoreCase(cfg.getType());
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
