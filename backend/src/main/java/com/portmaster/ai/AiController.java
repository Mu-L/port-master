package com.portmaster.ai;

import com.portmaster.ai.dto.AiChatRequest;
import com.portmaster.ai.dto.AiProviderInfoDTO;
import com.portmaster.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 助手 API：厂商列表 + 同步对话 + SSE 流式对话
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiClientRegistry registry;
    private final AiChatService chatService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        log.debug("AI status requested, enabled={}, defaultProvider={}",
                registry.isEnabled(), registry.getDefaultProviderId());
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", registry.isEnabled());
        map.put("defaultProvider", registry.getDefaultProviderId());
        map.put("providers", registry.listProviders());
        return ApiResponse.success(map);
    }

    @GetMapping("/providers")
    public ApiResponse<List<AiProviderInfoDTO>> providers() {
        List<AiProviderInfoDTO> list = registry.listProviders();
        log.debug("AI providers listed, count={}", list.size());
        return ApiResponse.success(list);
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@Valid @RequestBody AiChatRequest request) {
        ensureEnabled();
        log.info("AI chat(sync) start: {}", AiLogSupport.summarizeRequest(request));
        long start = System.currentTimeMillis();
        try {
            String content = chatService.chat(request);
            int len = content == null ? 0 : content.length();
            log.info("AI chat(sync) done in {}ms, replyChars={}", System.currentTimeMillis() - start, len);
            log.debug("AI chat(sync) reply preview: {}", AiLogSupport.truncate(content, 200));
            return ApiResponse.success(Map.of("content", content == null ? "" : content));
        } catch (Exception e) {
            log.error("AI chat(sync) failed in {}ms: {}", System.currentTimeMillis() - start, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AiChatRequest request) {
        ensureEnabled();
        log.info("AI chat(stream) start: {}", AiLogSupport.summarizeRequest(request));
        long start = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicInteger charCount = new AtomicInteger(0);

        return chatService.stream(request)
                .doOnSubscribe(s -> log.debug("AI chat(stream) subscribed"))
                .filter(token -> token != null && !token.isEmpty())
                .doOnNext(token -> {
                    int n = chunkCount.incrementAndGet();
                    charCount.addAndGet(token.length());
                    if (n <= 3 || n % 20 == 0) {
                        log.debug("AI chat(stream) chunk#{} chars={} preview={}",
                                n, token.length(), AiLogSupport.truncate(token, 60));
                    }
                })
                .map(token -> ServerSentEvent.builder(token).event("message").build())
                .concatWith(Flux.just(ServerSentEvent.builder("[DONE]").event("done").build()))
                .doOnComplete(() -> log.info(
                        "AI chat(stream) complete in {}ms, chunks={}, totalChars={}",
                        System.currentTimeMillis() - start, chunkCount.get(), charCount.get()))
                .doOnError(e -> log.error(
                        "AI chat(stream) error in {}ms, chunks={}, totalChars={}: {}",
                        System.currentTimeMillis() - start, chunkCount.get(), charCount.get(),
                        e.getMessage(), e))
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.builder(e.getMessage() == null ? "AI error" : e.getMessage())
                                .event("error")
                                .build()));
    }

    private void ensureEnabled() {
        if (!registry.isEnabled()) {
            log.warn("AI request rejected: portmaster.ai.enabled=false");
            throw new IllegalStateException("AI is disabled");
        }
    }
}
