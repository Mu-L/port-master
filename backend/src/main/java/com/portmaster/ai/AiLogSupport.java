package com.portmaster.ai;

import com.portmaster.ai.dto.AiChatRequest;
import org.springframework.util.StringUtils;

/**
 * AI 调试日志辅助（脱敏 API Key、截断长文本）
 */
final class AiLogSupport {

    private AiLogSupport() {
    }

    static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "(empty)";
        }
        String k = apiKey.trim();
        if (k.length() <= 8) {
            return "***";
        }
        return k.substring(0, 3) + "***" + k.substring(k.length() - 4);
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "...(+" + (t.length() - max) + " chars)";
    }

    static String summarizeRequest(AiChatRequest req) {
        if (req == null) {
            return "request=null";
        }
        int historySize = req.getHistory() == null ? 0 : req.getHistory().size();
        return String.format(
                "providerId=%s, model=%s, baseUrl=%s, apiKey=%s, includeContext=%s, historySize=%d, message=%s",
                nullToDash(req.getProviderId()),
                nullToDash(req.getModel()),
                nullToDash(req.getBaseUrl()),
                maskApiKey(req.getApiKey()),
                req.isIncludeContext(),
                historySize,
                truncate(req.getMessage(), 120));
    }

    static String describeProvider(String providerId, String type, String baseUrl, String model, String apiKey) {
        return String.format(
                "providerId=%s, type=%s, baseUrl=%s, model=%s, apiKey=%s",
                nullToDash(providerId),
                nullToDash(type),
                nullToDash(baseUrl),
                nullToDash(model),
                maskApiKey(apiKey));
    }

    private static String nullToDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }
}
