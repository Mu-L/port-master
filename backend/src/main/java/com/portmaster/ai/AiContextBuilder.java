package com.portmaster.ai;

import com.portmaster.model.dto.PortConflictDTO;
import com.portmaster.model.dto.PortInfoDTO;
import com.portmaster.model.dto.PortSummaryDTO;
import com.portmaster.model.dto.SystemStatsDTO;
import com.portmaster.service.PortService;
import com.portmaster.service.SystemMonitorService;
import com.portmaster.util.OsDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 为 AI 组装端口/系统上下文（控制长度，避免整表 dump）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiContextBuilder {

    private static final int MAX_LISTEN_ROWS = 40;
    private static final int MAX_CONFLICTS = 10;

    private final PortService portService;
    private final SystemMonitorService systemMonitorService;

    public String buildPortContext() {
        long start = System.currentTimeMillis();
        log.debug("AI context build start");
        StringBuilder sb = new StringBuilder();
        sb.append("【运行环境】\n");
        sb.append("- OS: ").append(OsDetector.getOsName()).append('\n');
        sb.append("- Java: ").append(System.getProperty("java.version")).append('\n');

        try {
            SystemStatsDTO stats = systemMonitorService.getSystemStats();
            if (stats != null) {
                sb.append("- CPU: ").append(stats.getCpuUsage()).append("%\n");
                sb.append("- Memory: ").append(stats.getMemoryUsedMb()).append(" / ")
                        .append(stats.getMemoryTotalMb()).append(" MB (")
                        .append(stats.getMemoryUsage()).append("%)\n");
                sb.append("- Listening ports: ").append(stats.getListenPortCount()).append('\n');
                sb.append("- Active connections: ").append(stats.getActiveConnectionCount()).append('\n');
                sb.append("- Process count: ").append(stats.getProcessCount()).append('\n');
            }
        } catch (Exception ignored) {
            log.warn("AI context: system stats unavailable");
        }

        try {
            PortSummaryDTO summary = portService.getSummary();
            sb.append("\n【端口汇总】\n");
            sb.append("- total=").append(summary.getTotal())
                    .append(", tcp=").append(summary.getTcpCount())
                    .append(", udp=").append(summary.getUdpCount())
                    .append(", listen=").append(summary.getListenCount())
                    .append(", established=").append(summary.getEstablishedCount())
                    .append('\n');
        } catch (Exception ignored) {
            log.warn("AI context: port summary unavailable");
        }

        try {
            List<PortInfoDTO> listen = portService.scanAllPorts().stream()
                    .filter(p -> p.getState() != null && p.getState().toUpperCase().contains("LISTEN"))
                    .limit(MAX_LISTEN_ROWS)
                    .collect(Collectors.toList());
            sb.append("\n【监听端口（最多 ").append(MAX_LISTEN_ROWS).append(" 条）】\n");
            for (PortInfoDTO p : listen) {
                sb.append("- ")
                        .append(p.getProtocol()).append(' ')
                        .append(p.getPort())
                        .append(" pid=").append(p.getPid())
                        .append(" process=").append(nullToDash(p.getProcessName()))
                        .append(" addr=").append(nullToDash(p.getLocalAddress()))
                        .append('\n');
            }
            log.debug("AI context: listen rows={}", listen.size());
        } catch (Exception e) {
            log.warn("AI context: listen ports failed: {}", e.getMessage());
            sb.append("\n【监听端口】获取失败: ").append(e.getMessage()).append('\n');
        }

        try {
            List<PortConflictDTO> conflicts = portService.detectConflicts();
            sb.append("\n【端口冲突】");
            if (conflicts == null || conflicts.isEmpty()) {
                sb.append("无\n");
            } else {
                sb.append('\n');
                conflicts.stream().limit(MAX_CONFLICTS).forEach(c ->
                        sb.append("- port=").append(c.getPort())
                                .append(" protocol=").append(c.getProtocol())
                                .append(" pids=").append(c.getPids())
                                .append(" processes=").append(c.getProcessNames())
                                .append('\n'));
                log.debug("AI context: conflicts={}", Math.min(conflicts.size(), MAX_CONFLICTS));
            }
        } catch (Exception ignored) {
            log.warn("AI context: conflicts unavailable");
        }

        sb.append("\n（以上为实时摘要，仅供诊断参考）\n");
        log.debug("AI context build done in {}ms, totalChars={}",
                System.currentTimeMillis() - start, sb.length());
        return sb.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
