# System Constitution

## Core Principles

### I. Layered Architecture (NON-NEGOTIABLE)

所有代码必须严格遵循四层分层架构：**API层 → 用户接口层 → 应用层 → 领域层 → 基础设施层**。

- 分层主干为单向依赖（上层依赖下层）；不强制禁止跨层，**领域层允许依赖外层 API 模块**（本工程以外的其他工程/外部系统
  API、client、DTO），**不得依赖本工程 API 模块**；禁止下层依赖上层以避免循环
- 数据对象按层级命名：DTO(API层) → VO(用户接口层) → BO(应用层) → DO(领域层) → Entity(基础设施层)
- 依赖注入必须使用接口，不能直接依赖实现类
- 应用层不能包含业务逻辑校验，只做用例编排
- 领域层封装核心业务逻辑和规则验证

**Rationale**: 清晰的分层架构确保系统的可维护性、可测试性和可扩展性，避免代码耦合导致的维护困难。

### II. Region-First Data Isolation (NON-NEGOTIABLE)

所有核心业务数据必须按 `region` 字段进行隔离。

- 所有核心业务表必须包含 `region` 字段 (varchar(32) NOT NULL)
- 所有查询方法必须包含 region 参数
- 所有唯一约束必须包含 region 字段 (如: uk_code_region)
- 索引设计必须考虑 region 字段的查询模式

**Rationale**: 支持多地区部署和数据隔离,确保不同地区的数据独立管理,满足合规要求和业务扩展需求。

### III. Idempotency & Event-Driven Design (NON-NEGOTIABLE)

系统通过 MQ 解耦，所有异步操作必须实现幂等性。

- 所有 MQ 消费者必须实现幂等性设计 (通过唯一键去重)
- 数据库操作使用 `INSERT IGNORE` 或 `UPDATE WHERE` 条件避免重复
- 外部服务调用必须支持重试且不改变结果
- 使用软删除 (is_valid 字段) 保留历史数据
- MQ 消息必须包含完整的业务标识 (如: bizId + taskId + cycleStr + region)

**Rationale**: 分布式系统中的消息可能重复投递,幂等性设计确保系统的正确性和一致性,避免数据重复或状态错误。

### IV. Test-First & Contract Testing (NON-NEGOTIABLE)

每个功能必须先编写测试用例，确保测试失败后再实现功能代码。

- 单元测试使用 H2 内存数据库，表结构必须与 MySQL 保持一致
- 集成测试必须覆盖 MQ 消费、外部服务调用、完整业务流程
- Contract testing 必须验证 API 接口的请求/响应契约
- 使用分页插件进行分页，禁止手动写 LIMIT 语句
- 测试必须独立运行，不依赖外部环境

**Rationale**: TDD 确保代码质量和需求覆盖,减少生产环境的 bug 和返工成本。

### V. Database & MyBatis Standards

数据库设计和 MyBatis 配置必须遵循统一规范。

- 表名和字段名使用 snake_case (如: order_task, create_by)
- 所有表必须包含通用字段: id, region, create_by, update_by, ctime, utime, is_valid
- 主键使用 bigint NOT NULL AUTO_INCREMENT
- 时间字段使用 bigint 存储时间戳 (便于跨时区处理)
- 所有 insert/insertSelective 方法必须配置主键回填 (@Options useGeneratedKeys=true)
- DAO 层必须定义 QueryCondition 内部类统一管理查询条件（允许 DAO 层 QueryCondition 作为内部类的唯一例外）
- 软删除通过更新 is_valid=0 实现，必须使用 ValidStatusEnum 枚举值

**Rationale**: 统一的数据库规范确保系统的一致性、可维护性和跨团队协作效率。DAL 代码生成规范（Entity/DAO 命名、包结构、BaseDAO
约定）详见 [DAO 层规范](specrules/03_coding/db_dao.md)。

### VI. Naming Conventions & Standards

命名必须遵循统一的中英文映射表和命名规范。

- 类名使用 PascalCase，方法名/变量名使用 camelCase，常量使用 UPPER_CASE
- 复数集合必须使用 `list` 后缀 (禁止使用 s/es 后缀)
- 外部服务调用类必须以 `Proxy` 结尾 (禁止 Gateway/Client/Service)
- 分层架构命名: API层(Service) → 用户接口层(ServiceImpl) → 应用层(AppService) → 领域层(DomainService) → 数据访问层(
  DAO/DAOImpl)
- 参数对象命名: QueryDO(查询参数)、Param(操作参数)、SyncParam(同步参数)
- **方法参数个数 (REQUIRED)**：方法参数**超过 3 个**时必须使用参数对象封装，不得在方法签名上平铺多个参数。API 层使用
  Request/Param 对象；应用层使用 BO；**领域层使用 QueryDO、xxOperateDO、Param 等 DO 风格参数对象**（如 `TaskOperateDO`、
  `TaskQueryDO`）。详见 api_layer_standards §3.1、data_object_naming 参数对象规范。
- **禁止内部类 (NON-NEGOTIABLE)**：除 **DAO 层的 QueryCondition** 可作为内部类（如 `TaskDAO.QueryCondition`
  ）外，禁止在其他任何地方使用内部类（inner class）。
- **构造函数 (REQUIRED)**：不需要实例化的类（如仅含静态方法的工具类、常量类）不应提供 public 构造函数；若需禁止实例化，可提供
  `private` 无参构造函数。不需要创建实例时不要写构造函数。

**Rationale**: 一致的命名规范提高代码可读性,减少团队沟通成本,避免命名混乱导致的理解障碍。参数对象化提升可读性与扩展性；禁止内部类与审慎使用构造函数有利于单测与可维护性。

### VII. Observability & Monitoring

所有关键业务操作必须记录日志和监控指标。

- 应用层记录关键业务操作日志 (入参、出参、异常)
- 领域层记录业务规则校验和状态流转日志
- MQ 消费端记录消息处理的开始、成功、失败日志
- 外部服务调用记录请求耗时和失败原因
- 使用分布式追踪系统进行链路追踪
- 配置告警规则: MQ 消费延迟、外部服务超时、数据一致性异常

**Rationale**: 可观测性是系统稳定性的基础,完善的日志和监控能快速定位问题,减少故障恢复时间。

### VIII. Design Document Standards (NON-NEGOTIABLE)

所有技术方案设计必须严格遵循技术方案模板，禁止系统间互相调用。

- **模板强制**: 必须使用技术方案模板 (docs/tech-doc-template.md)
- **章节完整性**: 所有模板章节必须填写，不需要的项目填「无」，但必须考虑
- **系统解耦**: 禁止系统间互相调用（即禁止 A RPC/HTTP 调 B 且 B 再 RPC/HTTP 调 A）；允许单向 RPC；若需双向交互，采用单向
  RPC + MQ（例如 A 调 B 用 RPC，B 回 A 用 MQ）
- **干系方识别**: 必须明确列出所有干系方 (业务人员、产品、上下游研发、数据组、TSP、开放平台)
- **接口定义**: 后端接口必须通过 API 平台按分支方式定义并产出接口文档链接
- **架构图强制**: 必须包含整体架构设计图、业务流程图、核心数据模型 (ER 图)
- **非功能性评估**: 必须评估灰度、回滚、监控、容量、多机房配置、资金安全监控
- **设计评审**: 技术方案必须经过团队评审，方向负责人必须参加
- **流程图/时序图规范**: 流程图和时序图中禁止显示具体 SQL 语句 (如 `select * from xxx`)，只展示业务逻辑概念和数据流向，实现细节留给开发阶段
- **流程完整性**: 所有业务流程必须完整覆盖正常流程、异常分支和边界情况，无遗漏步骤或决策点
- **方案整体性**: 设计方案必须包含从需求到交付的完整链路，确保各个模块之间的衔接完整无缝

**Rationale**:
统一的设计模板确保方案完整性和可评审性；禁止系统间互相调用避免循环依赖和耦合，提升系统可维护性和稳定性。流程图/时序图中隐藏实现细节（如SQL）确保设计文档专注于业务价值而非技术细节，方便非技术干系方理解；流程和方案的完整性确保开发无遗漏、测试有据可循、上线有保障。

## Architecture Constraints

### Four-Layer Architecture Enforcement

- **API层**: 只能定义接口契约 (REST/RPC)、Request/Response DTO
- **用户接口层**: 参数校验、DTO↔BO 转换、MQ 消费者、外部接口适配
- **应用层**: 业务用例编排、BO↔DO 转换、基本参数校验 (只调用 doCheck())
- **领域层**: 核心业务逻辑、业务规则验证、领域对象行为、状态流转
- **基础设施层**: 数据持久化 (DAO/Repository)、外部服务调用 (Proxy)、技术基础设施

### Dependency Rules

- 应用层不能依赖 API 层的 Request/Response 对象
- 领域层不能依赖应用层的 BO 对象
- 基础设施层不能依赖领域层以上的任何对象
- 所有服务层通过接口进行依赖注入，不直接依赖实现类
- **Repository 接口和实现都定义在领域层**；领域服务直接依赖 Repository；Repository 实现依赖基础设施层**提供的接口**（如
  DAO、Proxy、Cache），不关注 Infra 的具体实现（如 *DAOImpl、*Proxy）

### Data Storage Strategy

- 原始数据存储在 MySQL，保证事务性和一致性
- 聚合数据存储在 ES，提供高性能查询和搜索
- 通过 MQ 异步同步数据到 ES，保证最终一致性
- 草稿数据和正式数据分离存储，避免数据污染
- 所有数据按 region 隔离访问
- **禁止本地缓存**: 不使用本地缓存（JVM 堆内缓存、静态变量等），所有缓存必须使用分布式缓存（Redis）或通过多机房统一部署确保一致性
- **缓存一致性**: 分布式缓存必须设置合理的 TTL，并在数据更新时主动清理缓存，避免缓存不一致导致的业务问题
- **缓存容错**: 缓存失败或缓存穿透时，系统必须能够降级至数据库查询而不是崩溃

## Development Workflow

### Feature Development Process

1. **需求分析**: 编写需求文档 (参考技术方案模板)，明确业务价值和技术关注点
2. **架构设计**: 绘制系统架构图 (C4 Context)、业务流程图、时序图、ER 图
3. **接口设计**: 定义 API 接口、MQ 消息契约、数据模型
4. **代码实现**: 按 API → DAO → Repository → DomainService → AppService → Gateway 顺序开发
5. **单元测试**: 编写 DAO、Repository、DomainService、AppService 的单元测试
6. **集成测试**: 编写完整业务流程的集成测试
7. **代码评审**: 检查架构规范、命名规范、幂等性设计、测试覆盖率
8. **灰度发布**: 先小流量验证，监控日志和指标，逐步放量
9. **上线总结**: 记录变更内容、风险点、回滚方案

### Code Review Standards

- 必须检查分层架构是否正确（无循环依赖；允许 domain 依赖外层 API 模块，禁止依赖本工程 API 模块）
- 必须检查 region 隔离是否实现 (查询是否带 region 参数)
- 必须检查幂等性设计是否完善 (MQ 消费、DB 操作)
- 必须检查命名规范是否遵循 (复数使用 list、外部调用使用 Proxy)
- 必须检查数据库设计是否符合规范 (通用字段、主键回填、软删除)
- 必须检查测试覆盖率是否达标 (单元测试 + 集成测试)

### Testing Requirements

- **单元测试**: 覆盖 DAO、Repository、DomainService、AppService 的核心逻辑
- **集成测试**: 覆盖完整业务流程 (如: Daily 预测、Monthly 定级、权益发放)
- **Contract Testing**: 验证 API 接口的请求/响应契约
- **Performance Testing**: 验证性能目标 (如: 100w 商家 < 2 小时计算完成)
- **Chaos Testing**: 验证系统在异常情况下的稳定性 (如: MQ 重复消费、外部服务超时)

## Governance

### Problem Escalation (NON-NEGOTIABLE)

在设计、分析、实现过程中，如遇到以下任何问题情况，**严禁猜测或自行决策**，必须立即使用 AskQuestion 向用户获取反馈：

- **需求模糊**: PRD 中存在歧义或多种理解方式
- **技术边界不清**: 需要澄清性能指标 (QPS、延迟、吞吐量)、技术选型、跨系统依赖
- **权衡决策**: 涉及架构权衡、成本/收益权衡、跳过某项非功能性需求的决策
- **规范冲突**: 需要遵循的某项宪法规范与需求产生冲突
- **风险识别**: 发现设计方案中的高风险点或潜在问题
- **资源约束**: 发现实现方案受资源限制 (人力、时间、基础设施)

**Action Protocol**:

1. 立即停止进行中的设计或分析
2. 调用 `AskQuestion` 工具或输出 `>>ASK_USER: [具体问题]` 向用户明确提问
3. 等待用户反馈，基于反馈更新方案
4. 在设计文档中记录问题、用户决策和决策理由

**Rationale**: 避免在需求/边界不清的情况下盲目设计导致返工或设计方向偏差，确保最终交付物与需求高度契合。

### Amendment Process

本宪法是项目的最高开发原则，任何修改必须:

1. 提交修改提案，说明修改原因和影响范围
2. 团队技术评审，达成共识
3. 更新相关文档和模板 (plan-template、spec-template、tasks-template)
4. 更新版本号 (遵循语义化版本规则)
5. 向团队通告变更内容

### Version Policy

- **MAJOR**: 向后不兼容的架构原则变更或原则移除
- **MINOR**: 新增原则/章节或重大扩展
- **PATCH**: 澄清说明、措辞优化、错别字修正

### Compliance Review

- 所有 PR 必须经过 Code Review 验证是否符合本宪法
- 违反架构原则的代码必须重构，不能合并
- 复杂性引入必须有充分理由，并在 plan.md 的 Complexity Tracking 表中记录
- 定期进行架构评审，确保系统始终符合宪法原则

**Version**: 1.7.0 | **Last Amended**: 2026-03-25
