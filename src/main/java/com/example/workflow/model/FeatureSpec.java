package com.example.workflow.model;

/**
 * 需求 Agent 分析出来的一条功能，会通过共享状态传给设计和编码 Agent。
 *
 * @param label  功能的中文描述，例如“添加待办事项”
 * @param method 生成代码时使用的方法名，例如 add
 * @param kind   功能类型，决定生成什么样的方法体
 */
public record FeatureSpec(String label, String method, Kind kind) {

    public enum Kind {
        /** 新增一条数据 */
        ADD,
        /** 列出全部数据 */
        LIST,
        /** 修改一条数据 */
        UPDATE,
        /** 删除一条数据 */
        REMOVE,
        /** 统计类功能（例如求平均分） */
        STAT
    }
}
