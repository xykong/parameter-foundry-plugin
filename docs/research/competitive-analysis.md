# Jenkins Unified Parameters 功能调研与产品目标

## 1. 结论（先读）

| 决策项 | 结论 |
|--------|------|
| 文档状态 | 第二阶段完成：功能调研、产品目标和核心设计基线 |
| 产品定位 | 面向 Jenkins 社区的通用现代参数插件，不是 sample-project 内部专用插件 |
| 正式名称 | `Unified Parameters` |
| Plugin short name | `unified-parameters` |
| 核心判断 | 现有插件各自解决了部分问题，但没有一个同时做到现代 UI、类型安全、API 一致、安全数据源和优雅 Pipeline DSL |
| 建议方向 | 建立统一的“参数模型 + 选项数据源 + 渲染器 + 验证器”体系 |
| 第一版本重点 | 静态单选、多选、Radio、Checkbox；Freestyle/Pipeline/API/Rebuild 一致；无脚本迁移 |
| 是否开始编码 | 开始 `0.1 Technical Preview`；只有全部 integration gate 通过后才冻结 `1.0` 模型/API |

详细产品规格和编号验收目标见：

[`../product/product-spec.md`](../product/product-spec.md)。

竞品全覆盖、安全替代和迁移功能设计见：

[`../product/functional-design.md`](../product/functional-design.md)。

**新插件不应只是另一个多选控件，也不应复制 Extended Choice Parameter。**

它的目标应是：

> 为 Jenkins 提供一个安全、类型明确、可扩展、Pipeline-first、API 一致并符合现代交互习惯的
> 参数平台，使同一个参数定义在人工页面、REST/CLI、Webhook、Upstream 和 Rebuild 中具有
> 相同语义。

产品承诺是用户业务需求可以 1:1 或更好地迁移，不是旧 Java class、XML、Pipeline `$class`
或历史 Build 的无缝兼容。见
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)。

现有插件中值得继承的能力包括：

- Active Choices 的多种选择控件、搜索和参数联动。
- Extensible Choice 的 Provider 扩展模型和共享选项。
- Editable Choice 的可编辑下拉框。
- REST List Parameter 的 HTTP Provider、认证、缓存、展示值/实际值分离。
- JSON Parameter 的 JSONPath 和引用参数。
- Custom Checkbox Parameter 的声明式静态数据和 Checkbox 体验。
- Json Editor Parameter 的 Schema 驱动思想。
- Jenkins 内置参数稳定、简单的 Pipeline 和 API 语义。

不应继承的模式包括：

- 通过页面 DOM 查找其他参数并实现联动。
- 为表达静态列表强制执行 Groovy。
- 在核心插件中允许任意 HTML、JavaScript、Controller 文件或无约束网络访问。
- UI、API 和 Rebuild 使用不同的默认值或验证规则。
- 把提示文字伪装成默认选项。
- 依赖重复字段、分隔符猜测或隐式选择第一项。

## 2. 调研范围与口径

### 2.1 直接候选

本次比较以下 Jenkins 参数方案：

1. Jenkins Core `String`、`Choice` 和 `Boolean` 参数。
2. Extended Choice Parameter。
3. Active Choices。
4. Json Editor Parameter。
5. Extensible Choice Parameter。
6. Editable Choice。
7. Custom Checkbox Parameter。
8. Multiselect Parameter。
9. Validating String Parameter。
10. HTML Parameters。
11. JSON Parameter。
12. REST List Parameter。

Scriptler 作为 Active Choices 的脚本来源参与分析，但它不是独立参数类型。

以下插件只解决特定领域，不作为通用替代候选：

- Git Parameter、List Git Branches Parameter：Git ref 专用。
- Artifact Repository Parameter、Maven Metadata：制品仓库专用。
- Image Tag Parameter、GitLab Registry Image Parameter：容器镜像专用。
- Node and Label Parameter：Agent 调度专用。
- Select2 API：前端库封装，不提供参数模型。
- Dynamic Extended Choice Parameter：官方描述为示例插件。

### 2.2 比较口径

“支持 Pipeline”在本文中表示能够作为 Job 的构建参数定义，而不只是 Pipeline 运行过程中的
`input` 步骤。

“API 一致”表示以下入口使用同一组默认值、空值、类型和合法值规则：

- Jenkins 参数页面。
- `buildWithParameters`。
- Jenkins CLI。
- Generic Webhook 等触发插件。
- Pipeline `build` 步骤。
- Remote trigger。
- Rebuild。

能力标记：

| 标记 | 含义 |
|------|------|
| `●` | 原生、明确支持 |
| `△` | 部分支持、需要组合配置或存在限制 |
| `—` | 不支持或不属于该插件定位 |
| `?` | 官方文档没有明确说明，不能当作稳定契约 |

## 3. Update Center 和 UI Lab 基线

以下版本是 2026-08-27 保存的公开 Update Center/UI Lab 调研快照。UI Lab 的实际插件版本固定在
各 profile 的 `plugins.txt` 中：

| 插件 | Short name | 调研版本 | 最低 Core |
|------|------------|----------|-----------|
| Extended Choice Parameter | `extended-choice-parameter` | `388.ve7b_d0b_920e10` | `2.479.1` |
| Active Choices | `uno-choice` | `2.8.10` | `2.504.3` |
| Json Editor Parameter | `json-editor-parameter` | `54.v71962f37a_416` | `2.426.3` |
| Extensible Choice Parameter | `extensible-choice-parameter` | `255.vfa_41f46828ef` | `2.479.1` |
| Editable Choice | `editable-choice` | `71.v02a291ebbe45` | `2.361.4` |
| Custom Checkbox Parameter | `custom-checkbox-parameter` | `1.76.v08d845691023` | `2.541.3` |
| Multiselect Parameter | `multiselect-parameter` | `1.8` | `2.375.4` |
| Validating String Parameter | `validating-string-parameter` | `345.v0f4330488a_f6` | `2.528.3` |
| HTML Parameters | `html-parameters` | `10.v9a_56672a_96a_5` | `2.528.3` |
| JSON Parameter | `json-parameter` | `120.v3e5546f06794` | `2.555.3` |
| REST List Parameter | `rest-list-parameter` | `362.v52147f86a_172` | `2.479.1` |
| Scriptler | `scriptler` | `463.v52c7c5f7a_5e7` | `2.516.3` |

版本只代表当前观察快照，不作为新插件设计依赖。

## 4. 输入和 UI 能力对比

全部通用候选的真实 Jenkins 配置、默认、交互、Pipeline、Rebuild、校验和数据源状态截图见：

[`../ui-gallery/README.md`](../ui-gallery/README.md)。

| 方案 | 单选下拉 | Radio | 真正多选 | Checkbox 组 | 可自由输入 | 级联/联动 | 搜索过滤 | 结构化表单 |
|------|----------|-------|----------|-------------|------------|-----------|----------|------------|
| Jenkins Core | `●` | `—` | `—` | `—` | `●` String | `—` | `—` | `—` |
| Extended Choice | `●` | `●` | `●` | `●` | `△` Text/Hidden | `●` 多级选择 | `—` | `△` JSON 模式 |
| Active Choices | `●` | `●` | `●` | `●` | `△` Reactive HTML | `●` | `●` | `△` 自定义 HTML |
| Json Editor | `△` Schema enum | `△` | `●` Schema array | `●` Schema array | `●` | `△` Schema 条件 | `△` | `●` |
| Extensible Choice | `●` | `—` | `—` | `—` | `●` Editable | `—` | `—` | `—` |
| Editable Choice | `●` | `—` | `—` | `—` | `●` 可配置限制 | `—` | `●` autocomplete | `—` |
| Custom Checkbox | `—` | `—` | `●` | `●` | `—` | `—` | `—` | `—` |
| Multiselect Parameter | `●` 多列 | `—` | `—` | `—` | `—` | `●` 依赖下拉 | `—` | `△` 多列矩阵 |
| Validating String | `—` | `—` | `—` | `—` | `●` 正则校验 | `—` | `—` | `—` |
| HTML Parameters | 自定义 | 自定义 | 自定义 | 自定义 | 自定义 | `△` 自定义代码 | 自定义 | `●` |
| JSON Parameter | `●` | `—` | `—` | `—` | `—` | `●` 参数引用 | `△` | `—` |
| REST List Parameter | `●` | `—` | `—` | `—` | `—` | `△` URL 引用 | `●` | `—` |

### 4.1 关键观察

1. Active Choices 的控件覆盖最完整，但数据必须由脚本产生。
2. Custom Checkbox 只覆盖多选，不解决单选、Radio、联动和统一模型。
3. Multiselect Parameter 的名称容易误解；它提供的是多个相互依赖的单选下拉框，不是一个
   参数选择多个值。
4. Json Editor 和 HTML Parameters 能构建复杂表单，但复杂度、安全面和输出模型超过普通
   Choice 参数需求。
5. Extensible Choice 和 Editable Choice 在单选领域体验较好，但不能覆盖多选和 Checkbox。

## 5. 数据源和动态能力对比

| 方案 | Inline 静态 | 全局共享 | 文件 | HTTP/REST | JSON/XML | Groovy | Scriptler | 缓存/超时 | Provider 扩展点 |
|------|-------------|----------|------|-----------|----------|--------|----------|-----------|-----------------|
| Jenkins Core | `●` | `—` | `—` | `—` | `—` | `—` | `—` | `—` | `—` |
| Extended Choice | `●` | `—` | `●` Controller | `—` | `△` JSON | `●` | `—` | `—` | `—` |
| Active Choices | `△` Groovy List | `△` Scriptler | 脚本访问 | 脚本访问 | 脚本解析 | `●` | `●` | 自行实现 | `△` Script 模型 |
| Json Editor | `●` Schema | `—` | `△` Pipeline 代码 | `—` | `●` JSON Schema | `—` | `—` | `—` | `—` |
| Extensible Choice | `●` | `●` Global | `●` | 扩展插件 | Provider 决定 | `●` System | `—` | Provider 决定 | `●` |
| Editable Choice | `●` | `—` | `—` | `—` | `—` | `—` | `—` | `—` | `—` |
| Custom Checkbox | `●` YAML/JSON | `—` | `●` | `●` | `●` | `—` | `—` | `?` | `—` |
| Multiselect Parameter | `●` CSV | `—` | `—` | `—` | `—` | `—` | `—` | `—` | `—` |
| Validating String | `—` | `—` | `—` | `—` | `—` | `—` | `—` | `—` | `—` |
| HTML Parameters | `●` HTML | `—` | `—` | `—` | `△` 映射输出 | `—` | `—` | `—` | `—` |
| JSON Parameter | `●` 默认 JSON | `—` | `●` 配置文件 | `●` | `●` JSONPath | `—` | `—` | `?` | `—` |
| REST List Parameter | `△` 默认值 | `—` | `—` | `●` | `●` JSONPath/XPath | `—` | `—` | `●` | `△` REST 模型 |

### 5.1 值得吸收的设计

- Extensible Choice：把“选项从哪里来”抽象成 `ChoiceListProvider`。
- REST List Parameter：
  - 展示值和提交值分离。
  - Credentials、认证类型和自定义 Header。
  - JSONPath/XPath。
  - 缓存、排序和过滤。
- JSON Parameter：
  - 通过其他参数替换 URL 和 JSONPath。
  - 配置预览。
- Custom Checkbox：
  - 用 YAML/JSON 表达静态选项，不要求用户写 Groovy。
- Active Choices：
  - 默认项和禁用项元数据。
  - fallback。
  - 对大列表的过滤能力。

### 5.2 不应直接复制的设计

- Controller 任意文件路径读取。
- 在参数核心中直接执行不受约束的 System Groovy。
- 让脚本自行实现网络超时、缓存、TLS、凭据和错误处理。
- 通过字符串后缀 `:selected`、`:disabled` 编码选项元数据。
- 通过页面 DOM 读取依赖参数。
- 允许未经结构化约束的任意 HTML 和 JavaScript。

## 6. Jenkins 集成和语义对比

| 方案 | Freestyle | Pipeline Job 参数 | 声明式简洁 DSL | API/CLI 契约 | Rebuild 原控件 | 空值语义明确 | 服务端合法值校验 |
|------|-----------|-------------------|----------------|--------------|----------------|--------------|------------------|
| Jenkins Core | `●` | `●` | `●` | `●` | `●` | `△` Choice 默认首项 | `●` |
| Extended Choice | `●` | `●` | `△` `$class` | `△` | `●` 专用模板 | `△` | `△` |
| Active Choices | `●` | `●` | `△` `$class` | `△` 官方提示非 UI 限制 | `△` 文本框 | `△` 多选默认首项 | `△` |
| Json Editor | `—` 普通参数 | `△` 仅 `input` | `△` | `—` 构建入口 | `—` | Schema 决定 | `●` Schema |
| Extensible Choice | `●` | `—` 官方不计划支持 | `—` | `?` | `?` | `△` | `△` |
| Editable Choice | `●` | `●` | `△` `$class` | `?` | `?` | `△` 首项默认 | `△` 可允许任意值 |
| Custom Checkbox | `●` | `●` | `●` `customCheckbox` | `?` | `?` | `?` | `?` |
| Multiselect Parameter | `●` | `●` | `△` | `?` | `?` | `△` | `△` |
| Validating String | `●` | `●` | `●` | `△` | `●` 文本 | `●` | `●` 正则 |
| HTML Parameters | `●` | `△` 非 Declarative | `—` | `?` | `?` | 表单决定 | `△` 映射验证 |
| JSON Parameter | `●` | `●` | `●` `jsonParameter` | `?` | `?` | `△` | `△` |
| REST List Parameter | `●` | `●` | `●` `restList` | `?` | `?` | `△` | `△` |

### 6.1 Active Choices 专项验证

隔离 Jenkins `2.568.2`、Active Choices `2.8.10` 的验证观察到：

- 参数页面多选、Checkbox、Radio 和单选通过。
- 单个逗号分隔字符串 API 通过。
- Generic Webhook、Pipeline Upstream、RemoteCause 通过。
- Rebuild 值保留，但控件退化为文本框。
- 重复提交同名字段返回 HTTP 500。
- 无默认 `PT_MULTI_SELECT` 在 API 省略参数时采用第一项。

完整证据见：

[`active-choices-compatibility-summary.md`](./active-choices-compatibility-summary.md)。

该结果说明 Active Choices 可以作为迁移候选，但不能被视为所有入口均有官方兼容承诺的
统一参数平台。

## 7. 安全性和维护性对比

| 方案 | 主要安全面 | 维护信号 | 设计判断 |
|------|------------|----------|----------|
| Jenkins Core | Jenkins Core 权限、CSRF 和表单绑定 | Core 维护 | 简单参数的基准 |
| Extended Choice | 历史任意文件读取、SSRF、Stored XSS；无修复版本 | 已弃用 | 必须迁移 |
| Active Choices | Groovy、Script Approval、动态 HTML、DOM 依赖 | 活跃发布 | 功能强，但默认能力面过大 |
| Json Editor | JSON Schema、前端表单库、Pipeline input | 活跃发布 | 适合结构化审批，不是构建参数基础 |
| Extensible Choice | System Groovy、文件 Provider、扩展 Provider | Up for adoption | Provider 思想优秀，核心实现需现代化 |
| Editable Choice | 用户可提交列表外值 | Up for adoption | Editable 模式应由显式策略控制 |
| Custom Checkbox | 远程文件、配置解析；官方页面安全状态信号需澄清 | 活跃发布 | 值得参考 UI 和 YAML/JSON，不宜直接作为统一核心 |
| Multiselect Parameter | CSV 配置和前端级联 | 发布正常 | 场景窄，命名与真正多选冲突 |
| Validating String | 正则和客户端/服务端验证 | 活跃发布 | 验证模式值得吸收 |
| HTML Parameters | 自定义 HTML/CSS、属性映射 | 新插件、活跃发布 | 强表达力与高审计成本并存 |
| JSON Parameter | HTTP、JSONPath、参数替换 | 活跃发布 | Provider 能力值得吸收 |
| REST List Parameter | HTTP、Credentials、Header、JSONPath/XPath | 活跃发布 | 动态 Provider 设计最完整 |
| Scriptler | 共享 Groovy、权限、脚本目录和可选 Git | 活跃发布 | 应作为可选 Provider，不进入安全核心 |

## 8. 单项结论：应继承什么

### 8.1 Jenkins Core

继承：

- 简单、稳定的参数值类型。
- 明确的 Pipeline symbol。
- UI 和 API 共用 `ParameterDefinition` 语义。
- 服务端验证。

补足：

- 多选、Radio、Checkbox、搜索和动态来源。

### 8.2 Extended Choice Parameter

继承：

- 单选、多选、Radio、Checkbox 的完整覆盖。
- 自定义分隔符和兼容字符串输出。
- 可见行数等基础 UI 配置。

拒绝继承：

- 任意文件读取。
- 内嵌 Groovy 和 JavaScript。
- JSON Editor 与普通 Choice 混合在一个巨大定义中。
- 隐式默认值和弱验证。

### 8.3 Active Choices

继承：

- 单选、多选、Radio、Checkbox。
- 过滤搜索。
- 参数依赖和刷新。
- fallback。
- 禁用项和默认项。

改进：

- 不依赖 DOM。
- 静态选项不需要 Groovy。
- 使用结构化 `selected`、`disabled` 属性，不使用字符串后缀。
- 所有触发入口共用服务端解析和验证。
- Rebuild 保持原控件。

### 8.4 Json Editor Parameter

继承：

- Schema 驱动。
- 结构化默认值和验证。
- 复杂表单的分组与条件表达。

边界：

- 作为未来 Composite Parameter 的参考。
- 不在第一版本复制完整 JSON Schema 编辑器。

### 8.5 Extensible Choice Parameter

继承：

- Provider 扩展点。
- 全局共享选项。
- Provider 可由其他插件贡献。

改进：

- Pipeline-first。
- Provider 权限、超时、缓存和健康状态统一。
- 不内置危险的 System Groovy 和 Controller 文件 Provider。

### 8.6 Editable Choice

继承：

- 可搜索下拉框。
- 允许或禁止列表外输入。
- 前缀/包含过滤和大小写策略。

改进：

- 自定义值必须通过服务器端策略验证。
- UI 必须明确区分“选择已有值”和“创建新值”。

### 8.7 Custom Checkbox Parameter

继承：

- 声明式 YAML/JSON 静态配置。
- 全选、全不选和记忆选择等 Checkbox 体验。
- Pipeline symbol。

改进：

- 使用统一 Option 数据模型，不单独维护 Checkbox 专用数据格式。
- 远程数据源必须进入受控 Provider 层。

### 8.8 Multiselect Parameter

继承：

- 多列级联选择。
- CSV/矩阵表达父子关系。
- 依赖值变化时刷新后续列。

改进：

- 命名必须区分“multiple selection”和“cascading selects”。
- 使用结构化树模型，不依赖 CSV 列位置。

### 8.9 Validating String Parameter

继承：

- 服务端验证。
- 明确的错误提示。
- Pipeline symbol。

扩展：

- Choice 参数也应支持 required、min/max、正则、自定义值策略等验证。

### 8.10 HTML Parameters

继承：

- 一组字段映射到结构化参数。
- 现代布局和字段组合能力。
- HTML 清理与权限分离的思路。

拒绝继承：

- 把任意 HTML 作为普通 Choice 的默认扩展机制。
- 让用户自行拼接前端代码实现基础控件。

### 8.11 JSON Parameter 和 REST List Parameter

继承：

- HTTP Provider。
- Credentials 引用，不在配置中存储 Secret。
- JSONPath/XPath、展示值/实际值分离。
- 参数引用、缓存、排序、过滤和预览。

改进：

- SSRF 防护、目标 allowlist、重定向策略、响应大小上限和超时成为统一能力。
- Provider 返回统一 Option 对象，不直接耦合某个下拉控件。

### 8.12 Scriptler

继承：

- 可复用脚本目录。
- 权限和审计。
- 可选 Git 管理。

边界：

- Scriptler 只能作为可选脚本 Provider。
- 新插件核心不依赖 Scriptler。
- 静态列表和常规 REST Provider 不应要求安装 Scriptler。

## 9. 现有生态的共同缺口

### 9.1 参数模型和 UI 控件耦合

很多插件为每个控件重复实现：

- 选项获取。
- 默认值。
- 表单提交。
- Pipeline 序列化。
- 验证。

结果是 Checkbox、Radio、多选和下拉框具有不同数据格式和行为。

### 9.2 UI 和非 UI 入口语义不一致

典型问题：

- 浏览器提交数组，API 只能提交逗号字符串。
- 页面能过滤合法值，API 可以绕过。
- Rebuild 退化为文本框。
- 没有默认值时，UI、API 和 Pipeline 选择不同值。

### 9.3 静态配置被迫进入脚本模型

简单静态列表仍要执行 Groovy，带来：

- Script Approval。
- 沙箱 API 变化。
- 运行时错误。
- 安全和性能成本。
- 配置难以结构化分析。

### 9.4 动态来源缺少统一治理

每个插件分别实现或要求脚本自行实现：

- Credentials。
- 超时和重试。
- 缓存。
- TLS 和代理。
- 响应解析。
- 错误和 fallback。
- SSRF 防护。

### 9.5 默认值和空值不是一等模型

需要明确区分：

- 参数未提供。
- 提供空字符串。
- 提供空列表。
- 默认选择第一项。
- 显式没有默认项。
- 默认项当前不可用。

### 9.6 Pipeline 配置不够优雅

很多插件只能使用嵌套 `$class` Map，导致：

- Jenkinsfile 难读。
- 参数重命名和重构困难。
- 静态检查和文档生成困难。
- Freestyle 与 Pipeline 配置不一致。

### 9.7 现代 UI 和可访问性不足

常见缺口：

- 大列表不虚拟化。
- 多选缺少 chips、批量操作和清晰计数。
- 加载、失败、空结果和缓存过期状态不明确。
- 键盘导航、屏幕阅读器和移动布局不完整。
- 主题适配和 Jenkins Design Library 使用不一致。

### 9.8 缺少迁移工具

现有插件通常要求人工重新配置，缺少：

- 使用范围扫描。
- 兼容性预检。
- 自动生成迁移预览。
- 默认值和空值差异报告。
- 回滚快照。
- 活动 Job、生成源和队列中的旧 class 引用归零检查。
- 历史 Build 来源 class 报告和管理员退役决策。

## 10. 新插件的产品愿景

### 10.1 一句话定义

`Unified Parameters` 是 Jenkins 的现代类型化参数平台，通过统一 Option 模型、可扩展 Provider、
现代 Renderer 和服务端 Validator，为 Freestyle、Pipeline 和所有构建入口提供相同语义。

### 10.2 目标用户

- 需要人工选择构建、部署或测试目标的 Jenkins 用户。
- 通过 API、Webhook 和 Upstream 自动触发同一 Job 的平台团队。
- 管理大量共享选项和动态来源的 Jenkins 管理员。
- 编写 Jenkinsfile、Job DSL 或 JCasC 的工程团队。
- 需要从 Extended Choice、Active Choices 等旧插件迁移的组织。

### 10.3 产品原则

1. **一致性优先**：同一参数在所有入口具有同一结果。
2. **安全默认**：核心能力不执行任意代码、不渲染任意 HTML。
3. **静态简单**：静态列表不需要脚本。
4. **动态受控**：网络、脚本和共享配置通过 Provider 治理。
5. **模型与 UI 分离**：同一 Option 集可以渲染为下拉框、Radio、多选或 Checkbox。
6. **Pipeline-first**：每项功能都有稳定、可读的 Pipeline symbol。
7. **渐进增强**：没有 JavaScript 时仍能安全提交基础表单；现代 UI 提供更佳体验。
8. **可迁移**：把旧插件迁移作为正式产品能力。
9. **可扩展**：第三方插件可贡献 Provider、Renderer 或 Validator。
10. **社区质量**：遵循 Jenkins 安全、兼容性、测试和发布规范。

## 11. 目标能力模型

### 11.1 统一 Option 模型

每个选项至少支持：

```yaml
value: android
label: Android
description: Android 客户端构建
group: Mobile
selected: true
disabled: false
disabledReason: ""
tags:
  - mobile
  - release
metadata: {}
```

要求：

- `value` 是提交给构建的稳定值。
- `label` 只负责展示，可本地化。
- 默认和禁用状态使用结构化字段，不编码到字符串后缀。
- 支持分组、描述、标签和禁用原因。
- Provider 输出都转换为同一 Option 模型。

### 11.2 选择基数和渲染器分离

参数模型定义：

- 基数：`single` 或 `multiple`。
- 是否允许自定义值。
- 是否 required。
- 空值策略。
- 默认值。
- Provider。
- Validator。

Renderer 只决定交互表现：

- Select。
- Searchable combobox。
- Radio group。
- Checkbox group。
- Multi-select chips。
- Cascading columns。

同一个参数可以改变 Renderer，而不改变 API 输出和验证语义。

### 11.3 内置 Provider

第一方 Provider 目标：

1. `inline`：静态 YAML/List。
2. `global`：Jenkins 全局或 Folder 范围共享列表。
3. `rest`：受控 HTTP JSON/XML。
4. `json`：配置文件或受控 URL 的 JSONPath。
5. `cascade`：根据声明的依赖参数过滤或查询。

扩展 Provider：

- Git refs。
- Artifact repository。
- Container registry。
- Cloud resource。
- Script。

高风险 Provider 应拆为可选 companion plugin，不增加核心安装的攻击面。

### 11.4 默认值和空值

必须显式支持：

```yaml
required: false
default:
  mode: none        # none | first | explicit | provider
  values: []
emptySubmission:
  mode: empty-list  # absent | empty-string | empty-list
```

规则：

- 不隐式选择第一项，除非用户明确配置 `mode: first`。
- 配置默认值不在选项中时，保存配置前报错或要求显式允许 stale default。
- UI、API、CLI、Upstream 和 Rebuild 共用同一默认值解析器。

### 11.5 验证能力

服务端统一验证：

- required。
- 单选/多选基数。
- 最少和最多选择数量。
- 列表外值策略。
- 重复值策略。
- 空值策略。
- 正则和长度。
- Provider 当前值校验。
- 禁用值拒绝。
- 依赖参数组合校验。

验证失败应返回明确的 HTTP 4xx 和参数级错误，不返回 HTTP 500。

### 11.6 动态依赖

要求：

- 通过配置声明依赖参数，不扫描 DOM。
- 服务端计算依赖图并拒绝循环依赖。
- 浏览器通过稳定 endpoint 请求新选项。
- 支持 debounce、取消旧请求和响应版本号。
- 显示 loading、empty、error、fallback 和 stale-cache 状态。
- 非 UI 入口可以直接提交值，并使用相同服务端校验。

### 11.7 现代 UI

基础交互：

- 可搜索单选和多选。
- 多选 chips。
- 已选数量。
- 全选、全不选、反选。
- 分组、描述、禁用原因和状态 badge。
- 清晰的默认值、required 和验证提示。
- 大列表虚拟化。
- 键盘完整操作。
- 屏幕阅读器语义。
- 响应式布局。
- Jenkins 浅色/深色主题。
- Jenkins Design Library 视觉一致性。
- 可本地化字符串。

配置页面：

- Provider 连接测试。
- 数据预览。
- 默认值预览。
- Pipeline snippet。
- API 调用示例。
- 安全风险提示。

### 11.8 Jenkins 集成

必须提供：

- Freestyle 参数配置。
- Declarative Pipeline symbol。
- Scripted Pipeline。
- Job DSL。
- JCasC 全局 Provider。
- REST `buildWithParameters`。
- Jenkins CLI。
- Generic Webhook 等触发插件使用的标准 `ParameterValue`。
- Pipeline `build` 步骤。
- Remote trigger。
- Rebuild 原控件和原验证规则。
- Configuration as Code 导出或可审计配置。

目标 Pipeline DSL：

```groovy
parameters {
    unifiedChoice(
        name: 'CHANNEL',
        choices: [
            [value: 'prod', label: 'Production'],
            [value: 'test', label: 'Testing']
        ],
        defaultMode: 'explicit',
        defaultValue: 'test'
    )
    unifiedChoice(
        selectionMode: 'multiple',
        name: 'TARGETS',
        choices: [
            [value: 'android', label: 'Android'],
            [value: 'ios', label: 'iOS'],
            [value: 'ohos', label: 'HarmonyOS']
        ],
        defaultMode: 'explicit',
        defaultValues: ['android', 'ios'],
        renderer: 'checkbox',
    selectedDisplay: 'chips',
        minSelections: 1
    )
}
```

`1.0` Symbol 已固定为 `unifiedChoice`（`selectionMode` 表达基数）和 `unifiedForm`；实现前仍须通过 Declarative
Pipeline 和嵌套 Describable 技术 spike。

### 11.9 REST Provider 安全

必须统一实现：

- 受治理 Connection Profile；Job 不组合任意 URL 和 Credentials。
- Secret 脱敏。
- 连接和读取超时。
- 最大响应体。
- Content-Type 检查。
- TLS 校验。
- 代理支持。
- 重定向限制。
- DNS/IP 和目标 allowlist。
- 阻止访问云 metadata、loopback 和受限内网目标的 SSRF 策略。
- JSON/XML 安全解析。
- 缓存、最大 TTL、手工刷新和 last-known-good。
- Provider 权限和审计日志。

规范方案见
[`ADR 0010`](../decisions/0010-governed-connection-profiles.md)。

### 11.10 可观测性

管理员应能看到：

- Provider 最近成功/失败时间。
- 延迟、缓存命中和响应大小。
- 当前缓存是否过期。
- fallback 使用次数。
- 参数验证失败原因统计。
- 哪些 Job 使用某个全局 Provider。

日志不得输出 Credentials、Authorization Header 或敏感参数值。

## 12. 迁移能力

### 12.1 支持的迁移来源

目标扫描：

- Extended Choice Parameter。
- Active Choices。
- Extensible Choice Parameter。
- Editable Choice。
- Custom Checkbox Parameter。
- Jenkins Core Choice/String。

### 12.2 自动迁移等级

| 等级 | 场景 | 动作 |
|------|------|------|
| A | 静态选项、合法默认值、标准分隔符 | 自动生成目标配置 |
| B | 空值差异、提示型默认值、自定义分隔符 | 生成配置和人工决策项 |
| C | Groovy、Scriptler、文件、HTTP、Reactive | 只生成报告，不自动执行 |
| D | JavaScript、任意 HTML、不可识别 class | 阻止迁移并要求重新设计 |

### 12.3 迁移工具输出

- Job 和参数清单。
- 来源 class、类型、选项和默认值。
- 入口兼容性风险。
- 迁移前后结构化 diff。
- 可逆备份。
- dry-run。
- 批次计划。
- 冒烟测试模板。
- 旧 class 引用归零检查。

迁移工具不得在没有确认时修改生产 Job。

## 13. 社区产品范围

### 13.1 `1.0` 必须具备

- Inline 静态 Provider。
- 单选下拉、Radio、多选和 Checkbox。
- 结构化 Option。
- 显式默认值和空值。
- 服务端合法值验证。
- Freestyle 和 Pipeline。
- UI/API/Upstream/Remote/Rebuild 一致。
- 搜索、chips、批量选择、分组和禁用项。
- Jenkins Design Library、主题、键盘和本地化。
- Extended Choice 静态参数迁移预览。
- 完整单元测试、集成测试和兼容测试。
- 不执行 Groovy，不渲染任意 HTML，不访问任意文件或网络。

`1.0` 不包含 Editable/custom value、Global/Folder Provider、REST Provider、Reactive、
Cascade、Unified Form 或生产 Job in-place migration。完整路线见
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)。

### 13.2 `1.x` 增强

- 全局和 Folder Provider。
- REST/JSON/XML Provider。
- Credentials、缓存、fallback 和健康状态。
- 参数依赖和级联。
- Editable/custom value。
- Job DSL 和 JCasC。
- 更多旧插件迁移器。

### 13.3 后续可选能力

- Composite/Schema Parameter。
- Git、制品库和镜像仓库 companion Provider。
- Script Provider companion plugin。
- 个性化“记住上次选择”，但不得改变自动触发默认值。
- Option 图标和更丰富的只读展示。

## 14. 明确非目标

核心插件不计划：

- 成为 Jenkins Script Console。
- 执行任意非 sandbox Groovy。
- 读取任意 Controller 文件。
- 渲染任意 JavaScript。
- 默认允许任意 HTML。
- 替代 Credentials/Password 参数。
- 替代完整审批表单和通用低代码表单平台。
- 无损复刻已弃用插件的所有危险行为。
- 让浏览器成为参数合法性的唯一验证者。

## 15. 质量目标和验收标准

### 15.1 行为一致性

每种参数类型必须执行
[`Parity Test Matrix`](../testing/parity-test-matrix.md)
中全部适用的配置 surface、submission adapter 和 semantic dimension。同一输入必须产生相同
canonical value、`ParameterValue`、环境变量、构建记录和稳定 error code。

### 15.2 UI

- 10,000 个静态选项时页面仍可搜索和选择，不一次渲染全部 DOM 节点。
- 所有操作可只用键盘完成。
- 错误、加载和缓存过期状态可见。
- 浅色、深色和窄屏布局通过视觉测试。
- 不依赖 Jenkins 页面内部 DOM 结构查找依赖参数。

### 15.3 安全

- Core plugin 无任意代码执行路径。
- 所有 POST endpoint 有权限和 CSRF 检查。
- 所有提交值服务端验证。
- 所有展示文本正确转义。
- CSP 兼容，无内联 JavaScript 依赖。
- Provider 通过 SSRF、Credentials 泄漏和解析器安全测试。

### 15.4 Jenkins 社区质量

- 使用受支持的 Jenkins plugin parent 和 BOM。
- 发布时记录 `pom.xml` 最低 Jenkins 基线、发布时受支持 LTS 和集成插件版本矩阵。
- Jenkins Plugin Compatibility Tester 通过。
- SpotBugs、静态检查和依赖安全检查通过。
- Pipeline、Freestyle 和 Rebuild 有自动化集成测试。
- 文档包含示例、迁移、威胁模型和兼容性承诺。
- 公开 issue、贡献指南、发布和安全响应流程。

## 16. 设计问题关闭状态

| 原问题 | 决策 |
|--------|------|
| 单一还是拆分公开类型 | 最终统一为 `UnifiedChoiceParameterDefinition`，由 `selectionMode` 表达单选/多选 |
| Declarative Pipeline Symbol 和字段 | ADR 0009 固定 flat static DSL 和 nested dynamic Provider，由技术 spike 验证 Jenkins binding |
| Rebuild 集成和历史兼容 | ADR 0008 |
| Provider API 校验和快照 | ADR 0007 |
| absent/empty/canonical value | ADR 0005 |
| Renderer 前端架构 | Core Architecture + UI Interaction Specification |
| Reactive 权限和隔离 | ADR 0006、ADR 0007、Threat Model |
| REST SSRF 管理边界 | ADR 0010 Connection Profile；Job 不组合任意 URL 和 Credential |
| Migration Assistant 形态 | ADR 0011：同仓库 Maven reactor，core HPI + source-specific adapter HPI |
| 名称和 short name | ADR 0001；社区托管前执行最终检查 |

新增门禁：

- ADR 0012 定义 mixed Core/Unified Typed API。
- ADR 0013 定义 XStream 后 authoritative configuration validation。
- ADR 0014 将 Pipeline、standard REST 4xx/no-queue、CLI、Rebuild 等先放入 `0.1` 可执行验证。

在全面实现前仍须完成
[`Core Architecture`](../architecture/core-architecture.md)
列出的 Jenkins 技术 spike。这些 spike 验证集成可行性，不再重新定义业务语义。

## 17. 参考资料

- [Jenkins Pipeline Syntax](https://www.jenkins.io/doc/book/pipeline/syntax/)
- [Jenkins Design Library](https://weekly.ci.jenkins.io/design-library/)
- [Extended Choice Parameter](https://plugins.jenkins.io/extended-choice-parameter/)
- [Active Choices](https://plugins.jenkins.io/uno-choice/)
- [Json Editor Parameter](https://plugins.jenkins.io/json-editor-parameter/)
- [Extensible Choice Parameter](https://plugins.jenkins.io/extensible-choice-parameter/)
- [Editable Choice](https://plugins.jenkins.io/editable-choice/)
- [Custom Checkbox Parameter](https://plugins.jenkins.io/custom-checkbox-parameter/)
- [Multiselect Parameter](https://plugins.jenkins.io/multiselect-parameter/)
- [Validating String Parameter](https://plugins.jenkins.io/validating-string-parameter/)
- [HTML Parameters](https://plugins.jenkins.io/html-parameters/)
- [JSON Parameter](https://plugins.jenkins.io/json-parameter/)
- [REST List Parameter](https://plugins.jenkins.io/rest-list-parameter/)
- [Scriptler](https://plugins.jenkins.io/scriptler/)
