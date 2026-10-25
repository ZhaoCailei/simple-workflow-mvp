package com.example.workflow.core;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 之间传递的消息。
 *
 * <p>作业要求里的“Agent 间的消息传递”，在本项目里就体现为这个类：
 * 每个 Agent 做完事之后，显式地给下游发一条消息，消息会被写入
 * messages.log，控制台也会打印出来
 *
 * @param from    发送方 Agent
 * @param to      接收方 Agent
 * @param type    消息类型：HANDOFF（交接产物）/ DONE（流程完成）
 * @param content 消息内容（一句话说明）
 * @param time    发送时间
 */
public record Message(String from, String to, String type, String content, String time) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 把产物交接给下游 */
    public static Message handoff(String from, String to, String content) {
        return new Message(from, to, "HANDOFF", content, LocalTime.now().format(FORMATTER));
    }

    /** 流程结束，通知用户 */
    public static Message done(String from, String content) {
        return new Message(from, "user", "DONE", content, LocalTime.now().format(FORMATTER));
    }
}
