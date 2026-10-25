package com.example.workflow.agents;

import com.example.workflow.core.Agent;
import com.example.workflow.core.AgentResult;
import com.example.workflow.core.Message;
import com.example.workflow.core.WorkflowContext;
import com.example.workflow.model.FeatureSpec;

import java.util.List;
import java.util.Map;

/**
 * 设计 Agent：读取需求 Agent 产出的功能清单，输出《设计文档》。
 *
 * <p>它把功能清单转换成模块划分、数据结构和异常处理方案，
 * 并把最终确定的类名写回共享状态，供编码 Agent 使用。</p>
 */
public class DesignAgent implements Agent {

    @Override
    public String id() {
        return "design";
    }

    @Override
    public AgentResult execute(String task, WorkflowContext ctx) {
        String entity = ctx.getString("entity");
        String className = ctx.getString("className");
        List<FeatureSpec> features = ctx.getList("features");

        if (entity.isBlank() || className.isBlank() || features.isEmpty()) {
            throw new IllegalStateException("共享状态里缺少需求分析结果，请检查流程顺序：需求节点必须在设计节点之前");
        }

        String document = buildDocument(entity, className, features);

        ctx.put("design", document);
        // 编码 Agent 读取由设计节点确认的类名
        ctx.put("design.className", className);

        ctx.send(Message.handoff("design", "code",
                "设计文档已生成：主类 " + className + "，" + features.size() + " 个业务方法"));

        return AgentResult.ok("生成设计文档", Map.of("design.md", document));
    }

    private String buildDocument(String entity, String className, List<FeatureSpec> features) {
        StringBuilder document = new StringBuilder();
        document.append("# 设计文档\n\n");

        document.append("## 1. 总体方案\n");
        document.append("采用单类 + 内存列表的实现方式：所有").append(entity)
                .append("保存在一个 List 中，通过命令行指令调用对应方法完成操作。\n");
        document.append("数据流：输入解析 -> 业务方法 -> 列表存储 -> 控制台输出。\n\n");

        document.append("## 2. 模块划分\n\n");
        document.append("| 模块 | 职责 |\n");
        document.append("|------|------|\n");
        document.append("| 输入解析模块 | 读取命令行，按空格切分命令与参数 |\n");
        document.append("| 业务逻辑模块（").append(className).append("） | ")
                .append(String.join("、", features.stream().map(f -> f.method() + "()").toList()))
                .append(" |\n");
        document.append("| 数据存储模块 | List<String> items，保存全部").append(entity).append(" |\n");
        document.append("| 展示输出模块 | 统一编号展示，输出提示信息 |\n\n");

        document.append("## 3. 核心数据结构\n");
        document.append("- `List<String> items`：每个元素是一条").append(entity).append("\n");
        document.append("- 用户可见编号从 1 开始，内部下标从 0 开始，转换规则：下标 = 编号 - 1\n\n");

        document.append("## 4. 关键流程\n");
        document.append("1. 程序启动，创建 ").append(className).append(" 对象；\n");
        document.append("2. 循环读取命令行输入；\n");
        document.append("3. 解析第一条指令，映射到对应方法；\n");
        document.append("4. 方法执行后输出结果，回到第 2 步；\n");
        document.append("5. 输入 exit 时退出循环。\n\n");

        document.append("## 5. 异常处理\n");
        document.append("- 空输入：直接跳过，继续循环；\n");
        document.append("- 编号不是数字：解析失败返回 -1，由业务方法判定为非法编号；\n");
        document.append("- 编号越界：提示“编号无效”，不抛出异常；\n");
        document.append("- 输入流结束：退出循环，避免死循环。\n");

        return document.toString();
    }
}
