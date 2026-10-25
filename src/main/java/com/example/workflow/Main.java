package com.example.workflow;

import com.example.workflow.agents.CodeAgent;
import com.example.workflow.agents.DesignAgent;
import com.example.workflow.agents.RequirementAgent;
import com.example.workflow.core.Agent;
import com.example.workflow.core.WorkflowConfig;
import com.example.workflow.core.WorkflowEngine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 程序入口：解析命令行参数 -> 创建 Agent -> 启动流程引擎。
 *
 * <p>常用命令：</p>
 * <pre>
 *   java -jar simple-workflow-mvp.jar
 *   java -jar simple-workflow-mvp.jar --task "做一个图书管理系统"
 *   java -jar simple-workflow-mvp.jar --task "做一个成绩统计工具" --output output
 * </pre>
 */
public class Main {

    private static final String DEFAULT_TASK = "做一个命令行图书借阅管理工具，支持添加、借阅、归还、查询";

    public static void main(String[] args) {
        String task = DEFAULT_TASK;
        Path configPath = Path.of("workflow.properties");
        Path outputRoot = Path.of("output");

        try {
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--task" -> {
                        requireValue(args, index);
                        task = args[++index];
                    }
                    case "--config" -> {
                        requireValue(args, index);
                        configPath = Path.of(args[++index]);
                    }
                    case "--output" -> {
                        requireValue(args, index);
                        outputRoot = Path.of(args[++index]);
                    }
                    case "-h", "--help" -> {
                        printUsage();
                        return;
                    }
                    default -> {
                        System.out.println("未知参数：" + args[index]);
                        printUsage();
                        return;
                    }
                }
            }

            // 注册 Agent：这是新增 Agent 时唯一需要改的 Java 代码
            Map<String, Agent> agents = new LinkedHashMap<>();
            agents.put("requirement", new RequirementAgent());
            agents.put("design", new DesignAgent());
            agents.put("code", new CodeAgent());

            WorkflowConfig config = WorkflowConfig.load(configPath);
            WorkflowEngine engine = new WorkflowEngine(config, agents, outputRoot);
            engine.run(task, "本地执行（无需联网）");

        } catch (Exception e) {
            System.err.println();
            System.err.println("[流程失败] " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void requireValue(String[] args, int index) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("参数 " + args[index] + " 后面缺少值");
        }
    }

    private static void printUsage() {
        System.out.println("""
                用法：java -jar simple-workflow-mvp.jar [参数]

                  --task <需求>     要处理的需求，默认是一个待办事项工具
                  --config <文件>   流程配置文件，默认 workflow.properties
                  --output <目录>   产物输出目录，默认 output
                  -h, --help        显示帮助

                示例：
                  java -jar simple-workflow-mvp.jar --task "做一个图书借阅管理工具"
                """);
    }
}
