package com.example.workflow.core;

/**
 * 所有 Agent 的统一接口。
 *
 * <p>这就是“可扩展性”的核心：引擎只认识 Agent 接口，
 * 想新增一个 Agent，只需要：
 * 1. 新建一个类实现本接口；
 * 2. 在 Main 里注册一行；
 * 3. 在 workflow.properties 里加一个节点。
 * 引擎代码一行都不用改。</p>
 */
public interface Agent {

    /** Agent 的唯一标识，比如 requirement / design / code */
    String id();

    /**
     * 执行一次节点逻辑。
     *
     * @param task 用户输入的原始需求
     * @param ctx  共享上下文：可以读取上游产物、写入自己的产物、发送消息
     * @return 执行结果（产物 + 下一步跳转）
     */
    AgentResult execute(String task, WorkflowContext ctx) throws Exception;
}
