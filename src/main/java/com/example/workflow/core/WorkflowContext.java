package com.example.workflow.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期共享状态，也就是常说的“黑板（Blackboard）”。
 *
 * <p>它同时承担三件事：</p>
 * <ol>
 *   <li><b>状态共享</b>：一个 Map，上游 Agent 放东西，下游 Agent 取东西；</li>
 *   <li><b>产物落盘</b>：把生成的文件写到 output/{runId}/ 下；</li>
 *   <li><b>可观测性</b>：把消息写到 messages.log，把执行轨迹写到 trace.log。</li>
 * </ol>
 */
public class WorkflowContext implements AutoCloseable {

    private final String runId;
    private final Path outputDir;
    private final Map<String, Object> state = new HashMap<>();
    private final List<Message> messages = new ArrayList<>();
    private final BufferedWriter messageLog;
    private final BufferedWriter traceLog;

    public WorkflowContext(String runId, Path outputDir) throws IOException {
        this.runId = runId;
        this.outputDir = outputDir;
        Files.createDirectories(outputDir);
        this.messageLog = Files.newBufferedWriter(outputDir.resolve("messages.log"), StandardCharsets.UTF_8);
        this.traceLog = Files.newBufferedWriter(outputDir.resolve("trace.log"), StandardCharsets.UTF_8);
    }

    public String runId() {
        return runId;
    }

    public Path outputDir() {
        return outputDir;
    }

    // ---------------- 状态共享 ----------------

    /** 写入共享状态 */
    public void put(String key, Object value) {
        state.put(key, value);
    }

    /** 读取共享状态（类型不匹配会抛异常） */
    public <T> T get(String key, Class<T> type) {
        return type.cast(state.get(key));
    }

    /** 读取字符串状态，没有就返回空串 */
    public String getString(String key) {
        Object value = state.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /** 读取整数状态，没有就返回默认值 */
    public int getInt(String key, int defaultValue) {
        Object value = state.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    /** 读取列表状态（例如需求 Agent 给出的功能清单），没有就返回空列表 */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) {
        Object value = state.get(key);
        return value instanceof List<?> list ? (List<T>) list : List.of();
    }

    // ---------------- 产物落盘 ----------------

    /** 把内容写成文件，返回文件路径 */
    public Path writeArtifact(String fileName, String content) throws IOException {
        Path target = outputDir.resolve(fileName);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    // ---------------- 消息与日志 ----------------

    /** 发送一条 Agent 间消息：加入列表、写日志、打印到控制台 */
    public void send(Message message) {
        messages.add(message);
        try {
            messageLog.write(message.time() + "\t" + message.from() + " -> " + message.to()
                    + "\t" + message.type() + "\t" + message.content());
            messageLog.newLine();
            messageLog.flush();
        } catch (IOException e) {
            System.err.println("[警告] 写入消息日志失败：" + e.getMessage());
        }
        System.out.println("      [消息] " + message.from() + " -> " + message.to()
                + "  " + message.content());
    }

    public List<Message> messages() {
        return messages;
    }

    /** 记录一条执行轨迹 */
    public void trace(String line) {
        try {
            traceLog.write(line);
            traceLog.newLine();
            traceLog.flush();
        } catch (IOException e) {
            System.err.println("[警告] 写入执行日志失败：" + e.getMessage());
        }
    }

    @Override
    public void close() {
        closeQuietly(messageLog);
        closeQuietly(traceLog);
    }

    private void closeQuietly(BufferedWriter writer) {
        try {
            writer.close();
        } catch (IOException ignored) {
            // 关闭失败不影响主流程
        }
    }
}
