# 规则全局入口

本文件为仓库**唯一规则入口**。做任何设计或开发任务时，从本文件进入；所有条目均为引用路径，不复制正文。

---

## 1. 任务前置必加载（设计与开发共用）

任何设计类或开发类任务开始前，先加载以下基础规则：

- **全局宪章**: [宪章](specrules/constitution.md)
- **项目结构与 POM 规范**: [项目结构](specrules/00_general/project_struct.md)
- **分层架构总原则**: [分层架构核心](specrules/00_general/architecture/layered_architecture_core.md)
- **统一命名与数据对象边界**: [数据对象命名](specrules/00_general/naming/data_object_naming.md)

---

## 2. 设计阶段规则集合（在 §1 基础上追加）

### 2.1 设计文档 / 领域建模

- **设计流程与模板**: 由 `tech-design-architect-agent` 执行（含 Phase 1-5 完整工作流）
- **设计自检与评审**: [设计质量检查清单](specrules/02_design/design_quality_checklist.md)
- **数据存储设计**: [数据存储策略](specrules/02_design/data_storage_strategy.md)
- **状态流转与状态机**: [状态机设计](specrules/02_design/state_machine_design.md)

### 2.2 Mermaid / 图示表达

- **Mermaid 图表生成**：调用 `mermaid_chart_generator` skill

### 2.3 设计阶段常见追加专题

- 涉及接口设计时，追加：
  [API 层标准](specrules/00_general/architecture/api_layer_standards.md)、[API 文档规范](specrules/03_coding/api_documentation_standards.md)
- 涉及跨层对象边界时，追加：
  [对象转换规范](specrules/03_coding/object_conversion_standards.md)
- 涉及枚举 / 状态值设计时，追加：
  [枚举继承](specrules/00_general/enum/enum_inheritance_standards.md)、[枚举命名](specrules/00_general/enum/enum_naming_standards.md)、[枚举方法](specrules/00_general/enum/enum_method_design_standards.md)、[枚举使用](specrules/00_general/enum/enum_usage_rules.md)、[Integer 比较安全](specrules/00_general/enum/integer_comparison_safety_standards.md)

---

## 3. 开发阶段规则集合（在 §1 基础上追加）

### 3.1 分层实现

- **API / 接口开发
  **: [API 层标准](specrules/00_general/architecture/api_layer_standards.md)、[用户接口层标准](specrules/00_general/architecture/ui_layer_standards.md)、[对象转换规范](specrules/03_coding/object_conversion_standards.md)
- **应用层 / 用例编排
  **: [应用层标准](specrules/00_general/architecture/app_layer_standards.md)、[领域层标准](specrules/00_general/architecture/domain_layer_standards.md)、[对象转换规范](specrules/03_coding/object_conversion_standards.md)
- **领域层 / 业务逻辑
  **: [领域层标准](specrules/00_general/architecture/domain_layer_standards.md)、[基础设施层标准](specrules/00_general/architecture/infra_layer_standards.md)
- **基础设施 / DAO / Proxy
  **: [基础设施层标准](specrules/00_general/architecture/infra_layer_standards.md)、[领域层标准](specrules/00_general/architecture/domain_layer_standards.md)
- **熔断降级**: [基础设施层标准 §2](specrules/00_general/architecture/infra_layer_standards.md)

### 3.2 基础设施与通用编码

- **DB / 表设计**: [DB 表设计](specrules/03_coding/db_table_design.md)
- **DB / DAO 层**: [DAO 层规范](specrules/03_coding/db_dao.md)
- **DB / MyBatis Mapper**: [Mapper 规范](specrules/03_coding/db_mybatis_mapper.md)
- **DB / 分页全链路**: [分页全链路规范](specrules/03_coding/db_pagination_and_test.md) — **涉及列表分页查询或分页接口时必须加载
  **
- **MQ / 异步与一致性**: [MQ 规范](specrules/03_coding/mq.md)
- **依赖注入**: [依赖注入规范](specrules/03_coding/dependency_injection_core.md)（含循环依赖、最小依赖、单测注入）
- **对象转换**: [对象转换规范](specrules/03_coding/object_conversion_standards.md)
- **工具类 / JSON**: [JSON 工具规范](specrules/03_coding/jsonutils.md)
- **集合判空 / 集合操作**: [集合操作规范](specrules/03_coding/collection_standards.md)
- **配置与 region 读取**: [应用配置规范](specrules/03_coding/config_management.md) — 外部化配置 key 定义与 region 隔离读取
- **定时任务**: [定时任务调度规范](specrules/03_coding/task_scheduling.md) — @Scheduled / Quartz / K8s CronJob 与幂等性
- **线程池（Resilience）**: [线程池规范](specrules/03_coding/thread_pool_standards.md)

### 3.3 交付质量

- **API / 接口与网关文档**: [API 文档规范](specrules/03_coding/api_documentation_standards.md)
- **单元测试
  **: [技术栈规范](specrules/03_coding/unit_test_technology_stack.md)、[命名与结构](specrules/03_coding/unit_test_naming_and_structure.md)、[编译检查](specrules/03_coding/unit_test_compilation_standards.md)、[Mock 与断言](specrules/03_coding/mock_and_assertion_standards.md)、[基类规范](specrules/03_coding/test_base_class_standards.md)、[质量检查清单](specrules/03_coding/unit_test_quality_checklist.md)

---

## 4. 横切专题追加加载

以下专题不强行归入单一阶段，按需追加：

- **枚举与常量
  **: [枚举继承](specrules/00_general/enum/enum_inheritance_standards.md)、[枚举命名](specrules/00_general/enum/enum_naming_standards.md)、[枚举方法](specrules/00_general/enum/enum_method_design_standards.md)、[枚举使用](specrules/00_general/enum/enum_usage_rules.md)、[Integer 比较安全](specrules/00_general/enum/integer_comparison_safety_standards.md)
- **数据对象命名 / 跨层对象边界
  **: [数据对象命名](specrules/00_general/naming/data_object_naming.md)、[对象转换规范](specrules/03_coding/object_conversion_standards.md)
- **接口定义 / 返回值 / 参数对象化
  **: [API 层标准](specrules/00_general/architecture/api_layer_standards.md)、[API 文档规范](specrules/03_coding/api_documentation_standards.md)
- **配置与 region 读取**: [应用配置规范](specrules/03_coding/config_management.md)

---

## 5. 推荐加载顺序

1. 先加载 **§1 任务前置必加载**
2. 根据当前任务进入：
    - 设计任务：继续加载 **§2**
    - 开发任务：继续加载 **§3**
3. 如任务涉及枚举、接口、对象转换等横切主题，再从 **§4** 追加
4. 完成以上加载后，追加加载 `project-rules/project-rules.md`（若存在）

**Skill / Agent 特别约定**：

- **API/接口开发**：必须命中 [API 层标准](specrules/00_general/architecture/api_layer_standards.md)
  中的返回值规范、参数对象化要求，以及 [API 文档规范](specrules/03_coding/api_documentation_standards.md)
- **对外 API 字段 / 方法契约变更**：必须同时命中 [项目结构与 POM 规范](specrules/00_general/project_struct.md) 的 API
  artifact 版本升级规则，确认 `groupId:artifactId`、当前版本、目标 `*-SNAPSHOT` 版本和需要修改的 `pom.xml` / version
  property；目标版本未明确时禁止开始开发
- **设计文档审查**：必须命中 [设计质量检查清单](specrules/02_design/design_quality_checklist.md)；设计流程由
  `tech-design-architect-agent` 执行
- **单元测试**：按 §3.3 逐一加载各子规范，不依赖零散规则

---

## 6. 加载协议（Agent 必须遵循）

### 6.1 完整加载顺序

```
任务开始
  ↓
【1】加载 specrules/rules/index.md
  ↓
【2】按 §1～§4 加载规范（见本文件各节）
  ↓
【3】追加加载 project-rules/project-rules.md（若存在，项目特有约定，优先级高于全局规范）
  ↓
开始设计 / 编码
```

> 规范文件统一位于 `specrules/`，无论是 copy 模式（文件直接复制）还是 submodule 模式（`specrules/` 是 git
> submodule），路径完全一致。若 `specrules/rules/index.md` 不可读，禁止开始任务。

### 6.2 project-rules 加载规则

- 路径固定为 `project-rules/project-rules.md`（相对于目标项目根目录）
- 文件存在 → 读取并加载，其中约定覆盖全局规范中的通用建议
- 文件不存在 → 跳过，不报错，向后兼容

---

## 7. 目录结构（物理存储层）

```
specrules/
├── constitution.md             # 全局宪章
├── 00_general/                 # 全局基础层
│   ├── project_struct.md       # 项目结构与 POM 规范
│   ├── architecture/           # 分层架构各层标准
│   ├── enum/                   # 枚举设计规范（5个文件）
│   └── naming/
│       └── data_object_naming.md
├── 02_design/                  # 设计阶段规范
│   ├── design_quality_checklist.md
│   ├── data_storage_strategy.md
│   └── state_machine_design.md
├── 03_coding/                  # 开发阶段规范
│   ├── api_documentation_standards.md
│   ├── config_management.md
│   ├── task_scheduling.md
│   ├── thread_pool_standards.md
│   ├── db_table_design.md
│   ├── db_dao.md
│   ├── db_mybatis_mapper.md
│   ├── db_pagination_and_test.md
│   ├── mq.md
│   ├── jsonutils.md
│   ├── dependency_injection_core.md    # 含循环依赖、最小依赖、单测注入
│   ├── object_conversion_standards.md
│   ├── collection_standards.md
│   ├── unit_test_technology_stack.md
│   ├── unit_test_naming_and_structure.md
│   ├── unit_test_compilation_standards.md
│   ├── mock_and_assertion_standards.md
│   ├── test_base_class_standards.md
│   └── unit_test_quality_checklist.md
└── rules/
    └── index.md                # 本文件：唯一入口
```

---

## 7. 版本与变更

- **索引版本**：`1.12.0`
- **最后更新**：2026-06-23

### 变更日志（倒序）

- **1.0.0** (2025-02-06): 全量初始化。

说明：规则或 Agent/Skill 变更后，由 **rules-curator** 刷新索引时，应配合 **spec-version-changelog** 技能更新上表及各规范文件内的版本与变更记录。
