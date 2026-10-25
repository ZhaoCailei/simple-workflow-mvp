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
 * 编码 Agent：读取设计 Agent 确认的类名和功能清单，生成 Java 源代码。
 *
 * <p>生成的是完整可编译的单文件程序：包含数据存储、各业务方法、输入循环和异常处理。
 * 产物会保存为 output/{运行ID}/code/{类名}.java，可以直接用 javac 编译运行。</p>
 */
public class CodeAgent implements Agent {

    @Override
    public String id() {
        return "code";
    }

    @Override
    public AgentResult execute(String task, WorkflowContext ctx) {
        String entity = ctx.getString("entity");
        String updateSuffix = ctx.getString("updateSuffix");
        String className = ctx.getString("design.className");
        List<FeatureSpec> features = ctx.getList("features");

        if (className.isBlank() || features.isEmpty()) {
            throw new IllegalStateException("共享状态里缺少设计结果，请检查流程顺序：设计节点必须在编码节点之前");
        }

        String code = generate(task, entity, updateSuffix, className, features);

        ctx.put("code", code);
        ctx.send(Message.done("code", "生成 Java 文件 1 个（" + className + ".java），流程结束"));

        return AgentResult.ok("生成 " + className + ".java",
                Map.of("code/" + className + ".java", code));
    }

    private String generate(String task, String entity, String updateSuffix,
                            String className, List<FeatureSpec> features) {
        StringBuilder code = new StringBuilder();

        code.append("import java.io.InputStreamReader;\n");
        code.append("import java.nio.charset.Charset;\n");
        code.append("import java.util.ArrayList;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Scanner;\n\n");
        code.append("/**\n");
        code.append(" * 由 Workflow 自动生成。\n");
        code.append(" * 需求：").append(sanitize(task)).append("\n");
        code.append(" */\n");
        code.append("public class ").append(className).append(" {\n\n");
        code.append("    /** ").append(entity).append("列表 */\n");
        code.append("    private final List<String> items = new ArrayList<>();\n\n");

        for (FeatureSpec feature : features) {
            appendMethod(code, feature, updateSuffix);
        }

        boolean needIndexCheck = features.stream()
                .anyMatch(feature -> feature.kind() == Kind.UPDATE || feature.kind() == Kind.REMOVE);
        if (needIndexCheck) {
            appendIndexCheck(code);
        }

        appendMain(code, className, features);
        code.append("}\n");
        return code.toString();
    }

    private void appendMethod(StringBuilder code, FeatureSpec feature, String updateSuffix) {
        String method = feature.method();
        switch (feature.kind()) {
            case ADD -> code.append("""
                        public void %s(String text) {
                            if (text == null || text.isBlank()) {
                                System.out.println("内容不能为空");
                                return;
                            }
                            items.add(text.trim());
                            System.out.println("已添加：" + text.trim());
                        }

                    """.formatted(method));

            case LIST -> code.append("""
                        public void %s() {
                            if (items.isEmpty()) {
                                System.out.println("暂无数据");
                                return;
                            }
                            for (int i = 0; i < items.size(); i++) {
                                System.out.println((i + 1) + ". " + items.get(i));
                            }
                        }

                    """.formatted(method));

            case UPDATE -> code.append("""
                        public void %s(int index) {
                            if (!isValidIndex(index)) {
                                return;
                            }
                            items.set(index - 1, items.get(index - 1) + "%s");
                            System.out.println("已更新第 " + index + " 项：" + items.get(index - 1));
                        }

                    """.formatted(method, updateSuffix));

            case REMOVE -> code.append("""
                        public void %s(int index) {
                            if (!isValidIndex(index)) {
                                return;
                            }
                            items.remove(index - 1);
                            System.out.println("已删除第 " + index + " 项");
                        }

                    """.formatted(method));

            case STAT -> code.append("""
                        public void %s() {
                            if (items.isEmpty()) {
                                System.out.println("暂无数据，无法统计");
                                return;
                            }
                            double sum = 0;
                            int count = 0;
                            for (String item : items) {
                                try {
                                    sum += Double.parseDouble(item.trim());
                                    count++;
                                } catch (NumberFormatException e) {
                                    System.out.println("跳过非数字内容：" + item);
                                }
                            }
                            if (count == 0) {
                                System.out.println("没有可统计的数字");
                                return;
                            }
                            System.out.printf("平均分：%%.2f%%n", sum / count);
                        }

                    """.formatted(method));

            default -> throw new IllegalStateException("未知功能类型：" + feature.kind());
        }
    }

    private void appendIndexCheck(StringBuilder code) {
        code.append("""
                    private boolean isValidIndex(int index) {
                        if (index < 1 || index > items.size()) {
                            System.out.println("编号无效，请输入 1 ~ " + items.size());
                            return false;
                        }
                        return true;
                    }

                """);
    }

    private void appendMain(StringBuilder code, String className, List<FeatureSpec> features) {
        String hint = buildCommandHint(features);

        code.append("    public static void main(String[] args) {\n");
        code.append("        ").append(className).append(" app = new ").append(className).append("();\n");
        code.append("        Scanner scanner = new Scanner(new InputStreamReader(System.in, inputCharset()));\n");
        code.append("        System.out.println(\"命令：").append(hint).append(" | exit\");\n");
        code.append("        while (true) {\n");
        code.append("            System.out.print(\"> \");\n");
        code.append("            if (!scanner.hasNextLine()) {\n");
        code.append("                return;\n");
        code.append("            }\n");
        code.append("            String line = scanner.nextLine().trim();\n");
        code.append("            if (line.isEmpty()) {\n");
        code.append("                continue;\n");
        code.append("            }\n");
        code.append("            String[] parts = line.split(\" \", 2);\n");
        code.append("            String command = parts[0];\n");
        code.append("            String argument = parts.length > 1 ? parts[1] : \"\";\n");
        code.append("            switch (command) {\n");

        for (FeatureSpec feature : features) {
            code.append("                case \"").append(feature.method()).append("\":\n");
            switch (feature.kind()) {
                case ADD -> code.append("                    app.").append(feature.method()).append("(argument);\n");
                case UPDATE, REMOVE -> code.append("                    app.").append(feature.method())
                        .append("(parseIndex(argument));\n");
                default -> code.append("                    app.").append(feature.method()).append("();\n");
            }
            code.append("                    break;\n");
        }

        code.append("                case \"exit\":\n");
        code.append("                    System.out.println(\"再见\");\n");
        code.append("                    return;\n");
        code.append("                default:\n");
        code.append("                    System.out.println(\"未知命令：\" + command);\n");
        code.append("            }\n");
        code.append("        }\n");
        code.append("    }\n\n");
        code.append("""
                    /**
                     * 让命令行输入和终端使用同一种编码，避免 Windows 下中文输入乱码。
                     * 优先级：显式指定的 stdin.encoding > 终端输出编码 > 系统本地编码。
                     */
                    private static Charset inputCharset() {
                        String name = System.getProperty("stdin.encoding");
                        if (name == null || name.isBlank()) {
                            name = System.getProperty("stdout.encoding");
                        }
                        if (name == null || name.isBlank()) {
                            name = System.getProperty("native.encoding", "UTF-8");
                        }
                        try {
                            return Charset.forName(name);
                        } catch (Exception e) {
                            return Charset.defaultCharset();
                        }
                    }

                """);
        code.append("""
                    private static int parseIndex(String text) {
                        try {
                            return Integer.parseInt(text.trim());
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                """);
    }

    private String buildCommandHint(List<FeatureSpec> features) {
        StringBuilder hint = new StringBuilder();
        for (FeatureSpec feature : features) {
            if (hint.length() > 0) {
                hint.append(" | ");
            }
            hint.append(feature.method());
            switch (feature.kind()) {
                case ADD -> hint.append(" <内容>");
                case UPDATE, REMOVE -> hint.append(" <编号>");
                default -> {
                    // list / average 不需要参数
                }
            }
        }
        return hint.toString();
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ").replace("*/", "* /");
    }
}
