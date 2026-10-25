package com.example.workflow.agents;

import com.example.workflow.core.Agent;
import com.example.workflow.core.AgentResult;
import com.example.workflow.core.Message;
import com.example.workflow.core.WorkflowContext;
import com.example.workflow.model.FeatureSpec;
import com.example.workflow.model.FeatureSpec.Kind;

import java.util.List;
import java.util.Map;

/**
 * 需求 Agent：把用户的一句话需求整理成《需求文档》。
 *
 * <p>它先根据关键词判断需求属于哪一类（待办 / 图书 / 成绩 / 通用），
 * 再给出对应的功能清单。功能清单会以结构化数据的形式写进共享状态，
 * 供后面的设计 Agent 和编码 Agent 使用。</p>
 */
public class RequirementAgent implements Agent {

    @Override
    public String id() {
        return "requirement";
    }

    @Override
    public AgentResult execute(String task, WorkflowContext ctx) {
        Domain domain = Domain.detect(task);
        List<FeatureSpec> features = domain.features;

        // 写入共享状态：后续节点直接读取，不需要重新分析需求
        ctx.put("domain", domain.name());
        ctx.put("entity", domain.entity);
        ctx.put("updateSuffix", domain.updateSuffix);
        ctx.put("className", domain.className);
        ctx.put("features", features);

        String document = buildDocument(task, features);

        ctx.send(Message.handoff("requirement", "design",
                "需求文档已生成：" + features.size() + " 项功能（" + domain.entity + "）"));

        return AgentResult.ok("生成需求文档（" + features.size() + " 项功能）",
                Map.of("requirement.md", document));
    }

    private String buildDocument(String task, List<FeatureSpec> features) {
        StringBuilder document = new StringBuilder();
        document.append("# 需求文档\n\n");

        document.append("## 1. 项目背景\n");
        document.append("用户希望：").append(task).append("\n\n");

        document.append("## 2. 功能需求\n");
        for (int index = 0; index < features.size(); index++) {
            document.append(index + 1).append(". ").append(features.get(index).label()).append("\n");
        }

        document.append("\n## 3. 非功能需求\n");
        document.append("- 命令行界面，仅使用 JDK 标准库\n");
        document.append("- 单条指令响应时间小于 100 毫秒\n");
        document.append("- 数据保存在内存中，程序退出后清空\n\n");

        document.append("## 4. 验收标准\n");
        document.append("- 依次执行 ");
        for (int index = 0; index < features.size(); index++) {
            if (index > 0) {
                document.append(" / ");
            }
            document.append(features.get(index).method());
        }
        document.append(" 命令均有正确反馈\n");
        document.append("- 空内容、非法编号等错误输入不会导致程序崩溃\n");

        return document.toString();
    }

    /**
     * 需求分类与对应的功能清单。
     * 这里用简单的关键词规则识别需求类型，属于规则化的“需求分析”。
     */
    private enum Domain {

        TODO("待办事项", "TodoApp", "（已完成）", List.of(
                new FeatureSpec("添加待办事项", "add", Kind.ADD),
                new FeatureSpec("查看待办列表", "list", Kind.LIST),
                new FeatureSpec("标记待办为已完成", "complete", Kind.UPDATE),
                new FeatureSpec("删除待办事项", "remove", Kind.REMOVE))),

        LIBRARY("图书", "LibraryApp", "（已借出）", List.of(
                new FeatureSpec("添加图书", "add", Kind.ADD),
                new FeatureSpec("查看图书列表", "list", Kind.LIST),
                new FeatureSpec("借阅图书", "borrow", Kind.UPDATE),
                new FeatureSpec("删除图书", "remove", Kind.REMOVE))),

        SCORE("成绩", "ScoreApp", "（已修改）", List.of(
                new FeatureSpec("录入成绩", "add", Kind.ADD),
                new FeatureSpec("查看成绩列表", "list", Kind.LIST),
                new FeatureSpec("统计平均分", "average", Kind.STAT),
                new FeatureSpec("删除成绩", "remove", Kind.REMOVE))),

        GENERIC("记录", "RecordApp", "（已更新）", List.of(
                new FeatureSpec("新增记录", "add", Kind.ADD),
                new FeatureSpec("查看记录列表", "list", Kind.LIST),
                new FeatureSpec("修改记录", "update", Kind.UPDATE),
                new FeatureSpec("删除记录", "remove", Kind.REMOVE)));

        private final String entity;
        private final String className;
        private final String updateSuffix;
        private final List<FeatureSpec> features;

        Domain(String entity, String className, String updateSuffix, List<FeatureSpec> features) {
            this.entity = entity;
            this.className = className;
            this.updateSuffix = updateSuffix;
            this.features = features;
        }

        static Domain detect(String task) {
            String text = task == null ? "" : task;
            String lower = text.toLowerCase();
            if (text.contains("待办") || text.contains("任务") || lower.contains("todo")) {
                return TODO;
            }
            if (text.contains("图书") || text.contains("借阅") || text.contains("书籍")) {
                return LIBRARY;
            }
            if (text.contains("成绩") || text.contains("分数") || text.contains("学生")) {
                return SCORE;
            }
            return GENERIC;
        }
    }
}
