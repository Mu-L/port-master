package com.portmaster.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderInfoDTO {
    private String id;
    private String type;
    private String displayName;
    private String baseUrl;
    private String model;
    /** 服务端是否已配置 api-key（不返回明文） */
    private boolean configured;
    private boolean needsApiKey;
}
