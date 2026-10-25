# Simple Workflow MVP —— 三 Agent 协作的迷你软件工程流程

一个用 **纯 Java、零第三方依赖、完全本地运行** 实现的多 Agent 协作 Workflow MVP：

> **需求 Agent → 设计 Agent → 编码 Agent**

三个 Agent 顺序协作，前一个节点的产物通过**共享状态**和**消息**交给下一个节点，
最终自动生成一份需求文档、一份设计文档和一个可编译运行的 Java 程序。

整个流程不需要联网、不需要任何外部服务，双击运行即可复现。

---

## 1. 特性

| 作业要求 | 本项目对应实现 |
|---|---|
| ≥2 个 Agent 协作完成完整流程 | 3 个 Agent：需求 / 设计 / 编码（可增删） |
| Agent 间消息传递或状态共享 | **两者都有**：`Message` 显式消息 + `WorkflowContext` 共享状态 |
| 顺序流程控制 | `WorkflowEngine` 按 `workflow.properties` 顺序驱动 |
| 执行日志与状态追踪 | `trace.log`（节点级）+ `messages.log`（消息级）+ 控制台日志 |
| 错误处理 | 节点失败自动重试（次数可配置） |
| 可扩展性 | 新增 Agent = 新建类 + 注册一行 + 配置一个节点，**引擎零改动** |
| 条件分支（扩展能力） | 引擎支持 `AgentResult.next` 动态跳转，默认流程未使用，可按需扩展 |

---

## 2. 环境要求

- JDK 17 或更高（本项目在 JDK 24 上验证通过）
- Maven 3.6+（也可以直接用 IDEA 打开，无需命令行）
- **不需要联网**

---

## 3. 快速开始

### 3.1 命令行运行

```bash
# 在项目根目录执行
mvn clean package
java -jar target/simple-workflow-mvp.jar
```

看到类似输出即为成功：

```
[1] 节点 requirement（Agent: requirement）开始执行
      [消息] requirement -> design  需求文档已生成：4 项功能（待办事项）
      产物: requirement.md

[2] 节点 design（Agent: design）开始执行
      [消息] design -> code  设计文档已生成：主类 TodoApp，4 个业务方法
      产物: design.md

[3] 节点 code（Agent: code）开始执行
      [消息] code -> user  生成 Java 文件 1 个（TodoApp.java），流程结束
      产物: code/TodoApp.java
```

### 3.2 在 IDEA 里运行

1. `File → Open`，选择本项目根目录（含 `pom.xml`），等待 Maven 导入完成；
2. 打开 `src/main/java/com/example/workflow/Main.java`，点击 `main` 方法左侧的绿色三角；
3. 需要参数时，在 `Run → Edit Configurations → Program arguments` 里填写，例如：
   `--task "做一个图书借阅管理工具"`。

> **注意**：运行配置里的 `Working directory` 必须是项目根目录，否则找不到 `workflow.properties`。

### 3.3 换一个需求试试

```bash
java -jar target/simple-workflow-mvp.jar --task "做一个图书借阅管理工具"
java -jar target/simple-workflow-mvp.jar --task "做一个学生成绩统计工具"
java -jar target/simple-workflow-mvp.jar --task "做一个设备借用登记系统"
```

需求 Agent 会按关键词识别需求类型，自动给出不同的功能清单：

| 关键词 | 识别结果 | 生成的主类 | 功能 |
|---|---|---|---|
| 待办 / 任务 / todo | 待办事项 | `TodoApp` | 添加、列表、标记完成、删除 |
| 图书 / 借阅 / 书籍 | 图书 | `LibraryApp` | 添加、列表、借阅、删除 |
| 成绩 / 分数 / 学生 | 成绩 | `ScoreApp` | 录入、列表、统计平均分、删除 |
| 其他 | 通用记录 | `RecordApp` | 新增、列表、修改、删除 |

---

## 4. 命令行参数

| 参数 | 说明 | 默认值 |
|---|---|---|
| `--task <需求>` | 要处理的需求描述 | 一个待办事项工具 |
| `--config <文件>` | 流程定义文件 | `workflow.properties` |
| `--output <目录>` | 产物输出目录 | `output` |
| `-h, --help` | 显示帮助 | — |

---

## 5. 流程配置：改流程不用改代码

流程定义在 `workflow.properties` 中：

```properties
name=Mini SE 流程（需求 -> 设计 -> 编码）
entry=requirement
maxSteps=10
maxRetries=2

node.1.id=requirement
node.1.agent=requirement
node.1.next=design

node.2.id=design
node.2.agent=design
node.2.next=code

node.3.id=code
node.3.agent=code
node.3.next=
```

**只改配置文件就能做到：**

- **只跑到设计就停**：把 `node.2.next` 改成空 → 流程变成「需求 → 设计」；
- **调整顺序**：修改各节点的 `next` 即可；
- **加节点**：追加 `node.4.*` 并在前一个节点的 `next` 里指向它；
- **限制步数**：`maxSteps` 防止配置错误导致死循环。

`node.N.next` 留空表示流程结束。

---

## 6. 如何新增一个 Agent（可扩展性演示）

以新增「测试 Agent」为例，**三步，引擎代码零改动**：

**第 1 步**：新建 `TestAgent`，实现 `Agent` 接口：

```java
public class TestAgent implements Agent {
    @Override public String id() { return "test"; }

    @Override
    public AgentResult execute(String task, WorkflowContext ctx) {
        String code = ctx.getString("code");              // 读上游产物
        String cases = "# 测试用例\n\n- 空输入\n- 非法编号\n- 正常流程\n";
        ctx.send(Message.handoff("test", "user", "测试用例已生成"));
        return AgentResult.ok("生成测试用例", Map.of("test-cases.md", cases));
    }
}
```

**第 2 步**：在 `Main.java` 里注册一行：

```java
agents.put("test", new TestAgent());
```

**第 3 步**：在 `workflow.properties` 里加一个节点，并让编码节点指向它：

```properties
node.3.next=test

node.4.id=test
node.4.agent=test
node.4.next=
```

完成 —— 引擎、状态共享、消息记录、日志、重试全部自动生效。

---

## 7. 输出产物说明

每次运行会在 `output/{运行ID}/` 下生成：

| 文件 | 说明 |
|---|---|
| `requirement.md` | 需求 Agent 产出的需求文档 |
| `design.md` | 设计 Agent 产出的设计文档 |
| `code/*.java` | 编码 Agent 产出的 Java 源码（可直接编译运行） |
| `trace.log` | 节点级执行轨迹：步骤、节点、耗时、下一步 |
| `messages.log` | Agent 间消息记录：时间、发送方、接收方、类型、内容 |

生成的代码可以直接运行：

```bash
cd output/{运行ID}/code
javac -encoding UTF-8 TodoApp.java
java TodoApp
```

---

## 8. 项目结构

```
simple-workflow-mvp/
├─ pom.xml                      Maven 配置（零第三方依赖）
├─ workflow.properties          流程定义（改流程只改这里）
└─ src/main/java/com/example/workflow/
   ├─ Main.java                 入口：参数解析 + Agent 注册
   ├─ core/
   │  ├─ Agent.java             Agent 统一接口
   │  ├─ AgentResult.java       节点返回值（产物 + 下一步）
   │  ├─ Message.java           Agent 间消息
   │  ├─ WorkflowContext.java   共享状态（黑板）+ 日志 + 落盘
   │  ├─ WorkflowConfig.java    读取 workflow.properties
   │  └─ WorkflowEngine.java    流程引擎：顺序驱动 / 分支 / 重试
   ├─ agents/
   │  ├─ RequirementAgent.java  需求：识别需求类型，产出功能清单
   │  ├─ DesignAgent.java       设计：模块划分、数据结构、异常处理
   │  └─ CodeAgent.java         编码：按功能清单生成 Java 源码
   └─ model/
      └─ FeatureSpec.java       功能项（在三个 Agent 之间传递）
```

---

## 9. 常见问题

**Q：运行时报「找不到流程配置文件」？**
A：`workflow.properties` 必须位于**运行工作目录**下。IDEA 中检查 `Working directory` 是否为项目根目录；
或者显式指定：`--config "G:\IDEA Project\simple-workflow-mvp\workflow.properties"`。

**Q：控制台中文乱码？**
A：JDK 19+ 一般不会再乱码。若出现，加参数 `-Dstdout.encoding=UTF-8`；Windows CMD 可先执行 `chcp 65001`。

**Q：生成的文件在哪里？**
A：控制台最后会打印本次的运行 ID 和产物目录，通常在 `output/20261021-194136-671/` 这样的目录下。

**Q：每次运行都会产生新目录吗？**
A：会。运行 ID 精确到毫秒，不会覆盖之前的产物。提交作业前可以把整个 `output/` 删掉。

**Q：在 IDEA 里直接运行生成的 `TodoApp.class`，报 `ClassNotFoundException: TodoApp`？**
A：`.class` 是编译产物，不在 IDEA 模块的类路径里，直接点运行会找不到主类。两种办法：
① 用 IDEA 下方的终端，进入 `output/.../code` 执行 `java TodoApp`（推荐）；
② 把生成的 `TodoApp.java` 复制到项目的 `src/main/java` 下，再点绿色运行按钮。

**Q：在 PowerShell 里运行生成的程序，输入中文变成乱码？**
A：这是 Windows 终端编码（默认 GBK）和 Java 输入编码不一致导致的。
生成的代码会自动跟随终端编码读取输入，**重新运行一次流程生成新代码即可**。
如果用的是旧产物，也可以先在 PowerShell 执行 `chcp 65001` 再运行。

**Q：改了 `workflow.properties` 需要重新打包吗？**
A：不需要。它是运行时读取的，直接重新运行即可。
