package com.example.workflow.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 流程定义：从 workflow.properties 读出来的节点列表。
 *
 * <p>把流程写在配置文件里，而不是写死在代码里，
 * 这样“改流程”和“加节点”都不用碰 Java 代码 —— 这就是可扩展性。</p>
 */
public class WorkflowConfig {

    /**
     * 一个流程节点。
     *
     * @param id    节点编号
     * @param agent 使用的 Agent 名字
     * @param next  默认的下一个节点；null 表示流程结束
     */
    public record Node(String id, String agent, String next) {
    }

    private String name;
    private String entry;
    private int maxSteps;
    private int maxRetries;
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    private WorkflowConfig() {
    }

    public static WorkflowConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("找不到流程配置文件：" + path.toAbsolutePath());
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        WorkflowConfig config = new WorkflowConfig();
        config.name = properties.getProperty("name", "未命名流程").trim();
        config.entry = properties.getProperty("entry", "").trim();
        config.maxSteps = parseInt(properties.getProperty("maxSteps"), 12);
        config.maxRetries = parseInt(properties.getProperty("maxRetries"), 2);

        // node.1.id / node.1.agent / node.1.next 这种格式，按编号排序保证顺序
        SortedSet<Integer> indexes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("node.") && key.endsWith(".id")) {
                String[] parts = key.split("\\.");
                if (parts.length == 3) {
                    try {
                        indexes.add(Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                        // 不是合法的节点编号，跳过
                    }
                }
            }
        }

        for (int index : indexes) {
            String id = properties.getProperty("node." + index + ".id", "").trim();
            String agent = properties.getProperty("node." + index + ".agent", id).trim();
            String next = properties.getProperty("node." + index + ".next", "").trim();
            if (!id.isEmpty()) {
                config.nodes.put(id, new Node(id, agent, next.isEmpty() ? null : next));
            }
        }

        if (config.nodes.isEmpty()) {
            throw new IOException("流程配置里没有任何节点，请检查 " + path.toAbsolutePath());
        }
        if (config.entry.isEmpty()) {
            config.entry = config.nodes.keySet().iterator().next();
        }
        if (!config.nodes.containsKey(config.entry)) {
            throw new IOException("入口节点 " + config.entry + " 不存在，请检查 " + path.toAbsolutePath());
        }
        return config;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Node node(String id) {
        Node node = nodes.get(id);
        if (node == null) {
            throw new IllegalStateException("流程配置里找不到节点：" + id);
        }
        return node;
    }

    public String name() {
        return name;
    }

    public String entry() {
        return entry;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Map<String, Node> nodes() {
        return Collections.unmodifiableMap(nodes);
    }
}
