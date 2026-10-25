package com.example.workflow.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 流程引擎：整个项目的“大脑”。
 *
 * <p>它自己不实现任何业务，只做四件事：</p>
 * <ol>
 *   <li>按 workflow.properties 的顺序驱动节点；</li>
 *   <li>把 Agent 的返回值交给 WorkflowContext（共享状态 + 落盘）；</li>
 *   <li>处理条件分支（Agent 返回 next 就跳过去）和最大步数保护；</li>
 *   <li>失败时按配置重试，并记录 trace.log。</li>
 * </ol>
 */
public class WorkflowEngine {

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final WorkflowConfig config;
    private final Map<String, Agent> agents;
    private final Path outputRoot;

    public WorkflowEngine(WorkflowConfig config, Map<String, Agent> agents, Path outputRoot) {
        this.config = config;
        this.agents = agents;
        this.outputRoot = outputRoot;
    }

    public void run(String task, String mode) throws Exception {
        String runId = LocalDateTime.now().format(RUN_ID_FORMAT);
        Path outputDir = outputRoot.resolve(runId);
        // 极端情况下两次运行时刻相同，自动加后缀避免覆盖上一次的产物
        int suffix = 1;
        while (Files.exists(outputDir)) {
            outputDir = outputRoot.resolve(runId + "-" + suffix++);
        }

        printHeader(task, mode, outputDir.getFileName().toString());
        int step = 0;

        try (WorkflowContext ctx = new WorkflowContext(runId, outputDir)) {
            ctx.put("task", task);
            ctx.trace("runId=" + runId + "\tmode=" + mode + "\ttask=" + task);

            String current = config.entry();
            while (current != null && step < config.maxSteps()) {
                step++;
                WorkflowConfig.Node node = config.node(current);
                Agent agent = agents.get(node.agent());
                if (agent == null) {
                    throw new IllegalStateException("没有注册名为“" + node.agent() + "”的 Agent，请检查 Main.java 的注册代码");
                }

                System.out.println();
                System.out.println("[" + step + "] 节点 " + node.id() + "（Agent: " + agent.id() + "）开始执行");

                long start = System.currentTimeMillis();
                AgentResult result = executeWithRetry(agent, task, ctx);
                long cost = System.currentTimeMillis() - start;

                for (Map.Entry<String, String> entry : result.artifacts().entrySet()) {
                    Path file = ctx.writeArtifact(entry.getKey(), entry.getValue());
                    System.out.println("      产物: " + outputDir.relativize(file).toString().replace('\\', '/'));
                }

                String next = result.next() != null ? result.next() : node.next();
                System.out.println("      完成（" + cost + "ms）：" + result.summary());
                if (result.next() != null) {
                    System.out.println("      条件分支：跳转到节点 " + next);
                }

                ctx.trace(step + "\t" + node.id() + "\t" + agent.id() + "\t" + cost + "ms\t"
                        + result.summary() + "\tnext=" + next);
                current = next;
            }

            if (step >= config.maxSteps() && current != null) {
                System.out.println();
                System.out.println("[警告] 已达到最大步数 " + config.maxSteps() + "，流程强制结束（防止死循环）");
                ctx.trace("WARN\t达到最大步数，强制结束");
            }

            printSummary(outputDir, step);
        }
    }

    /** 失败重试：最多重试 config.maxRetries() 次，每次等待时间递增 */
    private AgentResult executeWithRetry(Agent agent, String task, WorkflowContext ctx) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                return agent.execute(task, ctx);
            } catch (Exception e) {
                lastError = e;
                System.err.println("      [错误] " + agent.id() + " 第 " + (attempt + 1) + " 次执行失败：" + e.getMessage());
                if (attempt < config.maxRetries()) {
                    long waitMillis = 500L * (attempt + 1);
                    System.out.println("      " + waitMillis + "ms 后重试……");
                    Thread.sleep(waitMillis);
                }
            }
        }
        throw new RuntimeException("节点 " + agent.id() + " 重试 " + config.maxRetries() + " 次后仍然失败", lastError);
    }

    private void printHeader(String task, String mode, String runId) {
        System.out.println("========================================================");
        System.out.println("  Simple Workflow MVP —— " + config.name());
        System.out.println("  模式    : " + mode);
        System.out.println("  任务    : " + task);
        System.out.println("  运行 ID : " + runId);
        System.out.println("  流程    : " + String.join(" -> ", config.nodes().keySet()));
        System.out.println("========================================================");
    }

    private void printSummary(Path outputDir, int step) throws IOException {
        System.out.println();
        System.out.println("==================== 流程结束 ====================");
        System.out.println("执行节点数 : " + step);
        System.out.println("产物目录   : " + outputDir.toAbsolutePath());
        System.out.println("执行日志   : " + outputDir.resolve("trace.log").toAbsolutePath());
        System.out.println("消息日志   : " + outputDir.resolve("messages.log").toAbsolutePath());
        System.out.println("生成的文件 :");
        try (Stream<Path> files = Files.walk(outputDir)) {
            files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> System.out.println("  - " + outputDir.relativize(file).toString().replace('\\', '/')));
        }
    }
}
