package com.example.workflow.core;

import java.util.Map;

/**
 * 一个 Agent 执行完之后交还给引擎的结果。
 *
 * @param summary   一句话说明这个节点做了什么（会打印到控制台和 trace.log）
 * @param artifacts 要落盘的文件：文件名 -> 文件内容
 * @param next      动态跳转目标。null 表示按配置文件里的 next 继续走，
 *                  非 null 表示“我要跳回某个节点”（实现条件分支/循环）
 */
public record AgentResult(String summary, Map<String, String> artifacts, String next) {

    /** 正常执行，按配置的 next 继续 */
    public static AgentResult ok(String summary, Map<String, String> artifacts) {
        return new AgentResult(summary, artifacts, null);
    }

    /** 条件分支：跳转到指定节点 */
    public static AgentResult branch(String summary, Map<String, String> artifacts, String next) {
        return new AgentResult(summary, artifacts, next);
    }
}
