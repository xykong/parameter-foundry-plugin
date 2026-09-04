# Unified Parameters 产品规格

## 1. 结论（先读）

| 决策项 | 内容 |
|--------|------|
| 正式名称 | **Unified Parameters** |
| Jenkins 展示名 | `Unified Parameters` |
| Plugin short name / artifactId | `unified-parameters` |
| 预期仓库 | `jenkinsci/unified-parameters-plugin` |
| Maven groupId | `io.jenkins.plugins` |
| Java package | `io.jenkins.plugins.unifiedparameters` |
| Pipeline 主 Symbol | `unifiedChoice`、`unifiedMultiChoice`、`unifiedForm` |
| 产品标语 | **One parameter model. Every Jenkins entry point.** |
| UI/API 一致性 | 强保证；不一致视为发布阻断 |
| 规格日期 | 2026-08-28 |
| 当前阶段 | 核心设计基线完成；下一里程碑为 `0.1 Technical Preview` |

**Unified Parameters** 不只是一个新的 Choice 控件，而是 Jenkins 的统一现代参数平台。

它必须解决当前生态最核心的问题：

> 同一个参数定义在配置页面、Build with Parameters、Pipeline、API、CLI、Webhook、
> Upstream、Remote Trigger 和 Rebuild 中使用同一数据模型、默认值、空值和验证规则。

详细的竞品全覆盖、功能 Epic、安全替代和迁移设计见：

[`functional-design.md`](./functional-design.md)。

版本范围和迁移承诺以
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)
为准。这里的“替代”和“1:1”均指用户业务需求与可观察行为可以迁移到更安全、优雅、高效的
实现，不表示旧 Java class、XML、Pipeline 配置或历史构建记录无缝兼容。

### 1.1 四项最高目标

1. 覆盖全部已调研插件的用户需求和有效能力，形成通用功能超集。
2. 不复制不安全、不一致或已经过时的实现；使用更优雅、合规、正确的方式完成同一目标。
3. 将平移、重构迁移、验证、回滚和旧插件下线作为产品功能，而不是外部实施项目。
4. 在安全、体验、一致性、可维护性和迁移成本上形成明确优势，让每类竞品用户都有充分迁移动机。

“100% 替代”在本规格中表示**使用场景覆盖 100%**，不表示：

- 二进制兼容旧插件 Java class。
- 原样保存旧 XML。
- 继续支持任意 Groovy、JavaScript、HTML 或 Controller 文件读取。
- 复制旧插件的隐式默认值、弱验证或 UI/API 不一致行为。

目标状态是：用户原有功能需求都能 1:1 或更好地表达，迁移助手可以说明目标配置、差异和验证
方法；用户不需要继续采用旧插件的不安全实现方式。

## 2. 名称决策

### 2.1 候选对比

| 候选 | 优点 | 问题 | 决策 |
|------|------|------|------|
| Unified Parameters | 精确表达统一模型、统一入口和统一扩展体系；长期有效 | 现代 UI 优势需要通过副标题表达 | **采用** |
| Modern Parameters | 直观、有产品感、搜索友好 | “Modern” 会随时间老化，更像品质评价 | 不采用 |
| Typed Parameters | 强调类型安全 | 容易让用户误解为只解决 Java 类型 | 不采用 |
| Parameter Studio | 有产品感 | 容易被理解为独立编辑器或低代码平台 | 不采用 |
| Advanced Parameters | 容易理解 | 泛化、老旧，无法建立差异化 | 不采用 |
| Universal Parameters | 愿景强 | 过度承诺 | 不采用 |

### 2.2 冲突检查

2026-08-28 完成以下检查：

- Jenkins Update Center 共扫描 2098 个插件，没有 `unified-parameters`。
- `plugins.jenkins.io/unified-parameters/` 不存在。
- `jenkinsci/unified-parameters-plugin` 不存在。
- Jenkins Maven 仓库中没有 `io.jenkins.plugins:unified-parameters`。
- 未发现已公开使用的 `unifiedChoice`、`unifiedMultiChoice`、`unifiedForm`
  Jenkins Symbol。

以上只是当前可用性证据。正式申请 Jenkins 社区托管时仍须执行最终名称检查和社区评审。

### 2.3 命名规范

| 对象 | 规范 |
|------|------|
| Java package | `io.jenkins.plugins.unifiedparameters.*` |
| CSS class | `unified-parameters-*` |
| JavaScript module | `unified-parameters/*` |
| REST path | `unified-parameters/*` |
| Metrics prefix | `jenkins_unified_parameters_*` |
| Log namespace | Java package名 |
| JCasC root | `unifiedParameters` |

禁止使用：

- `mp` 作为公开配置名，含义不明确。
- `choice2`、`extendedChoice2` 等依赖旧插件的名称。
- `universal`、`complete` 等无法兑现的绝对化文案。

## 3. 产品定位

### 3.1 目标用户

- 在 Jenkins 页面中选择构建、部署、测试目标的普通用户。
- 同时通过 UI、API、Webhook 和 Upstream 调用同一 Job 的平台团队。
- 维护全局选项、Folder 选项和动态数据源的 Jenkins 管理员。
- 使用 Jenkinsfile、Job DSL、JCasC 和共享库管理 Job 的工程团队。
- 需要从 Extended Choice、Active Choices 等插件迁移的组织。

### 3.2 核心价值

1. **同一语义**：所有触发入口行为一致。
2. **优雅配置**：静态选项不写脚本，Pipeline 不使用巨大 `$class` Map。
3. **现代交互**：搜索、chips、分组、虚拟化、键盘、移动端和主题适配。
4. **安全默认**：核心插件不执行任意 Groovy、JavaScript 或 HTML。
5. **受控扩展**：Provider、Renderer、Validator 可以扩展，但必须遵循统一契约。
6. **可迁移**：扫描、预览、批次、回滚和活动配置旧 class 归零是正式能力；历史 Build 单独报告。

## 4. 统一概念模型

### 4.1 Parameter

一个参数由以下部分组成：

```yaml
name: TARGETS
cardinality: multiple
provider:
  type: inline
renderer:
  type: chips
defaults:
  mode: explicit
validation:
  required: true
environmentExport:
  mode: delimited
```

参数模型和 UI Renderer 必须分离。把 `chips` 改成 `checkboxes` 不得改变 API 值和验证语义。

### 4.2 Option

```yaml
value: android
label: Android
description: Android client build
group: Mobile
selected: false
disabled: false
disabledReason: ""
tags:
  - mobile
metadata: {}
```

要求：

- `value` 是稳定机器值。
- `label` 是展示值，可本地化。
- `selected` 只作为导入或 Provider 默认候选提示；运行时默认值由参数级 `DefaultSpec` 唯一决定。
- 默认候选、禁用和分组使用结构化字段，不编码成 `:selected` 字符串。
- 所有 Provider 输出统一转换为 Option。

精确的空值、默认值、重复值、顺序和序列化语义见
[`ADR 0005`](../decisions/0005-canonical-value-and-serialization.md)。

### 4.3 Value

| 参数 | Pipeline `params` | 默认环境变量 |
|------|-------------------|----------------|
| single | `String` | 原字符串 |
| multiple | 无序选择集合的确定性 `List<String>` 视图 | 逗号分隔字符串 |
| form | 不可变结构化 Map | JSON 字符串 |

多选环境变量可配置：

- `delimited`：默认，兼容现有 Jenkins Job。
- `json`：JSON Array。
- `none`：不导出环境变量。

多选顺序不是 `unifiedMultiChoice` 的业务语义；List/环境变量顺序在一个 Provider snapshot 内
确定，动态快照变化后可能变化。顺序敏感需求使用后续 ordered collection/Form 类型。

如果选项值包含 delimiter，保存配置时必须报错，或要求切换为 JSON 输出。

## 5. `0.1 Technical Preview`

`0.1` 是不承诺配置/API 兼容性的工程验证版本，不作为稳定 Update Center 版本发布。

| ID | 验证目标 | 退出条件 |
|----|----------|----------|
| UP-SPIKE-001 | Public binding/persistence | flat static/nested dynamic binding，save/reload/copy/rename 通过 |
| UP-SPIKE-002 | Loaded definition validation | XStream 绕过 setter 的非法 fixture 可加载、可诊断、不可构建 |
| UP-SPIKE-003 | Pipeline values | Declarative/Scripted、`params` 和 downstream `build` 通过 |
| UP-SPIKE-004 | Form presence/decoding | presence marker、repeated 和 explicit-empty 语义通过 |
| UP-SPIKE-005 | Standard REST status | 最低基线和目标 LTS 上 invalid input 返回 4xx 且 queue 不变 |
| UP-SPIKE-006 | CLI | multi-value 和稳定错误契约通过 |
| UP-SPIKE-007 | Mixed Typed API | Core String/Text/Boolean/Choice + Unified Job 原子调度通过 |
| UP-SPIKE-008 | Rebuild | 保持 Renderer 并按当前 definition 重验证 |
| UP-SPIKE-009 | CSP/No-JS | CSP-safe enhancement 和 bounded fallback 通过 |
| UP-SPIKE-010 | Single HPI migration packaging | core、source adapter、Provider 和 Renderer 在一个 HPI 中加载通过 |

详细门禁见
[`ADR 0014`](../decisions/0014-technical-preview-and-ga-gates.md)。

## 6. `1.0` 功能目标

### 6.1 参数类型

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-TYPE-001 | 静态单选 | 下拉框和 searchable combobox |
| UP-TYPE-002 | 静态多选 | listbox、chips 和 Checkbox |
| UP-TYPE-003 | Radio | 使用与单选相同的数据和验证模型 |
| UP-TYPE-004 | Checkbox group | 使用与多选相同的数据和验证模型 |
| UP-TYPE-006 | Read-only/disabled option | 页面、API 和 Rebuild 均拒绝禁用值 |

### 6.2 Inline Provider

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-PROV-001 | UI 表格编辑 Option | 支持排序、分组、默认和禁用状态 |
| UP-PROV-002 | Pipeline List/Map 配置 | 不要求 Groovy 脚本 |
| UP-PROV-003 | YAML/JSON 导入导出 | 导入前预览差异 |
| UP-PROV-004 | 重复值检测 | 保存前阻止重复机器值 |

### 6.3 默认值和空值

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-SEM-001 | `none` | 不隐式选择第一项 |
| UP-SEM-002 | `first` | 只有显式配置后才选第一项 |
| UP-SEM-003 | `explicit` | 默认值必须存在且可用 |
| UP-SEM-004 | absent/explicit-empty | form 使用 reserved presence marker，其他入口结构化表达；所有入口结果一致 |
| UP-SEM-005 | stale default | `1.0` Inline stale default 始终阻止保存；动态策略在后续版本显式配置 |
| UP-SEM-006 | multi-choice order | 业务值为无序集合；所有输出按机器值 `String.compareTo` 稳定排序 |
| UP-SEM-007 | form presence marker | `__unified_parameters_presence` 不进入值列表，冲突/重复/未知 marker 拒绝 |

### 6.4 验证

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-VAL-001 | required | 空值返回参数级错误 |
| UP-VAL-002 | min/max selections | 页面和服务端同时校验 |
| UP-VAL-005 | disabled value | 所有入口拒绝 |
| UP-VAL-006 | unknown value | 返回 HTTP 4xx，不返回 HTTP 500 |
| UP-VAL-007 | duplicate submitted value | 可配置拒绝或去重，默认拒绝 |
| UP-CONFIG-001 | loaded definition validation | 所有入口在 Provider/排队前运行同一 authoritative validator |

### 6.5 Jenkins 入口一致性

| ID | 入口 | 目标 |
|----|------|------|
| UP-INT-001 | Freestyle | 原生配置和构建页面 |
| UP-INT-002 | Declarative Pipeline | 简洁 Symbol |
| UP-INT-003 | Scripted Pipeline | 完整支持 |
| UP-INT-004 | `buildWithParameters` | 按参数显式 `standardApiInputMode` 解码 single/repeated/json-array/delimited，不猜测 |
| UP-INT-005 | Jenkins CLI | 使用 CLI 专用 transport decoder，并调用同一 Evaluation Service |
| UP-INT-006 | Pipeline `build` | 支持 typed value 和字符串兼容输入 |
| UP-INT-007 | Webhook/Remote Trigger | 标准 ParameterDefinition 契约 |
| UP-INT-008 | Rebuild | 保持原 Renderer、Option 和验证规则 |
| UP-INT-009 | Job copy/rename/reload | 行为和配置不漂移 |
| UP-INT-010 | Typed JSON API | 原生 String、Array、Boolean 和 Object |
| UP-INT-011 | Schema/Resolve/Validate API | 非 UI 客户端可完整发现和求值 |
| UP-INT-013 | Parity guarantee | 同一输入产生同一 canonical value/error code |
| UP-INT-014 | Mixed Job Typed API | 内置适配 Core String/Text/Boolean/Choice；unsupported definition 整体 409 |

所有入口必须调用同一个：

- `ValueParser`
- `DefaultResolver`
- `ParameterValidator`

不得为页面和 API 分别实现独立规则。

Typed API 是完整 Job 调度契约，不忽略 unsupported definition；详细适配边界见
[`ADR 0012`](../decisions/0012-typed-api-parameter-adapters.md)。

XStream 可能绕过 setter，所有入口在 Provider 和排队前必须运行
[`ADR 0013`](../decisions/0013-definition-validation-lifecycle.md)
定义的 authoritative validation。

视觉 Renderer 可以只存在于页面，但其可选值、默认值、验证和输出必须与 API 完全一致。
每个功能只有通过 UI/API Parity Test Matrix 后才能发布。

### 6.6 Pipeline DSL

目标语法：

```groovy
parameters {
    unifiedChoice(
        name: 'CHANNEL',
        choices: [
            [value: 'production'],
            [value: 'staging', label: 'Staging'],
            [value: 'testing']
        ],
        defaultMode: 'explicit',
        defaultValue: 'staging',
        renderer: 'searchable'
    )

    unifiedMultiChoice(
        name: 'TARGETS',
        choices: [
            [value: 'android', label: 'Android', group: 'Mobile'],
            [value: 'ios', label: 'iOS', group: 'Mobile'],
            [value: 'web', label: 'Web', group: 'Browser']
        ],
        defaultMode: 'explicit',
        defaultValues: ['android', 'ios'],
        renderer: 'chips',
        minSelections: 1,
        standardApiInputMode: 'repeated'
    )
}
```

设计要求：

- `@DataBoundConstructor` 只包含真正必需字段。
- 可选字段使用 `@DataBoundSetter`。
- Descriptor 和所有嵌套 Describable 提供唯一 `@Symbol`。
- Snippet Generator 不输出未修改的默认字段。
- 配置序列化稳定，新增可选字段不破坏旧 Job。

公开字段、互斥规则和 Snippet Generator 形态见
[`ADR 0009`](../decisions/0009-public-model-and-pipeline-data-binding.md)。

Jenkins 官方建议 Pipeline 插件使用短构造器、`DataBoundSetter` 和唯一 `Symbol`，
本插件必须遵循。

### 6.7 现代 UI

| ID | 能力 | 目标 |
|----|------|------|
| UP-UX-001 | Search | 支持 label、value、description 和 tags |
| UP-UX-002 | Multi chips | 删除、清空、计数和键盘操作 |
| UP-UX-003 | Batch actions | 全选、全不选、反选当前过滤结果 |
| UP-UX-004 | Grouping | 可折叠分组和组级选择 |
| UP-UX-005 | Option details | 描述、禁用原因和状态 badge |
| UP-UX-006 | States | loading、empty、error、stale、fallback |
| UP-UX-007 | Large lists | 10,000 项虚拟化，不渲染 10,000 个 DOM 节点 |
| UP-UX-008 | Accessibility | WCAG 2.2 AA，完整键盘和屏幕阅读器语义 |
| UP-UX-009 | Responsive | 390px 宽度可操作 |
| UP-UX-010 | Theme | Jenkins 浅色、深色和高对比主题 |
| UP-UX-011 | No-JS fallback | 不超过 200 个 Option 的基础静态参数可完整提交；更大列表安全降级并给出 API 方式 |

### 6.8 配置体验

- 参数预览。
- 默认值预览。
- Pipeline snippet。
- API 请求示例。
- 当前输出值预览。
- 保存前兼容性检查。
- 配置 diff。
- Option 数量、重复值和不可达默认值摘要。

### 6.9 迁移助手

`1.0` 首先支持 Extended Choice 静态参数的发现、分类、预览、目标配置生成和验证 fixture：

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-MIG-001 | Extended Choice 静态迁移设计 | 支持版本范围内生成 Normalized Intent、目标配置和验证 fixture；不静默部分转换 |

| 等级 | 条件 | 动作 |
|------|------|------|
| A | 静态值、合法默认值、逗号分隔 | 自动生成转换预览 |
| B | 空默认、提示型默认、自定义 delimiter | 生成决策项 |
| C | 文件、Groovy、Reactive | 只报告，不自动修改 |
| D | JavaScript、任意 HTML、未知 class | 阻止迁移 |

要求：

- dry-run。
- Job/参数清单。
- before/after 结构 diff。
- Pipeline/Job 目标配置和验证 fixture。
- 不直接修改 SCM、Multibranch 子 Job 或生产 Job。

## 7. `1.1` Provider 平台

### 7.1 内置 Provider

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-SOURCE-001 | Global Provider | 稳定 ID、UI/JCasC、权限和使用范围明确 |
| UP-SOURCE-002 | Folder Provider | 继承、覆盖、移动和 sibling 隔离明确 |
| UP-SOURCE-003 | Managed Config Provider | 只通过 managed-config API，不接受 Controller 路径 |
| UP-SOURCE-004 | REST JSON/XML Provider | 通过 Connection Profile、JSONPath/XPath 映射为 Option |
| UP-SOURCE-005 | Connection Profile | 绑定目标、Credentials、Header、TLS、Proxy 和允许路径 |
| UP-SOURCE-006 | Transform Chain | map/filter/sort/distinct/limit/group 可预览、可测试 |
| UP-SOURCE-007 | Cache/Health/Fallback | 缓存隔离、健康、超时和 last-known-good 可观测 |

同时加入：

- Editable/custom single choice。
- 展示值和机器值分离。

### 7.2 Provider 运行契约

每个 Provider 必须提供：

- `load(context)`。
- timeout。
- cache key。
- last-known-good。
- health status。
- Option schema validation。
- 权限要求。
- 可安全记录的诊断摘要。
- principal/Job/Profile/global budget identity。

运行、缓存和失败策略见
[`ADR 0007`](../decisions/0007-provider-snapshots-cache-and-failure.md)。
限流、bounded queue 和 HTTP 429 契约见
[`ADR 0006`](../decisions/0006-api-input-errors-and-permissions.md)。

### 7.3 REST 安全

- Job 只引用 Connection Profile；URL authority、Credentials 和 Secret Header 不进入 Job 配置。
- TLS 校验。
- 连接/读取超时。
- 响应大小上限。
- Content-Type 检查。
- 重定向限制。
- DNS/IP 和管理员 allowlist。
- 阻止 loopback、cloud metadata 和受限内网 SSRF。
- JSON/XML 安全解析。
- 日志不输出 Authorization Header 或敏感值。

Connection Profile 的权限、URL 构造和凭据绑定见
[`ADR 0010`](../decisions/0010-governed-connection-profiles.md)。

完整信任边界和安全测试见
[`Threat Model`](../security/threat-model.md)。

### 7.4 Custom Value 验证

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-TYPE-005 | Editable single choice | 明确区分选择已有值和创建自定义值 |
| UP-VAL-003 | allow custom value | 默认关闭 |
| UP-VAL-004 | regex/length | 页面和服务端使用同一策略 |

### 7.5 自动化和静态迁移执行

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-AUTO-001 | Job DSL | 使用与 Pipeline/UI 相同的公开字段并稳定 round-trip |
| UP-AUTO-002 | JCasC | Connection Profile 和共享 Provider 可导入导出且不泄露 Secret |
| UP-MIG-002 | 静态迁移 apply/clone/rollback | compare-before-write、备份、审计和并发冲突保护 |

## 8. `1.2` Reactive 和级联

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-REACT-001 | 声明依赖参数 | 不扫描页面 DOM |
| UP-REACT-002 | 依赖图 | 服务端检测循环 |
| UP-REACT-003 | 异步刷新 | debounce、取消旧请求、版本号 |
| UP-REACT-004 | 状态展示 | loading、empty、error、fallback 明确 |
| UP-REACT-005 | 非 UI 入口 | 可直接提交并执行同一服务端验证 |
| UP-REACT-006 | 缓存快照 | 构建记录使用的 Provider 版本可追溯 |
| UP-INT-012 | Provider snapshot | UI/API 可使用同一选项快照 |
| UP-MIG-003 | Dynamic migration adapters | 生成 Provider/Cascade 重构骨架和验证 fixture |

## 9. `2.0` Unified Form

`unifiedForm` 用于结构化组合参数：

- text、number、boolean、choice、multi-choice。
- section、grid、tabs 和条件显示。
- JSON Schema 子集。
- 每个字段独立映射，或整体 JSON/Map 输出。
- 服务端 Schema 验证。
- 不允许任意 JavaScript。
- HTML 只允许由受信任 Renderer 生成。

它不是 `1.0` 阻塞项，也不应拖慢 Choice 核心质量。

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-FORM-001 | Typed Field Schema | string、number、boolean、choice、array 和 object 服务端验证 |
| UP-FORM-002 | Layout Schema | section、grid、tabs 和条件显示不依赖任意 JavaScript |
| UP-FORM-003 | Structured Output | Map/JSON、字段映射和环境导出有稳定 canonical contract |
| UP-FORM-004 | Trusted Rendering | 只允许内置或已审查 Renderer 输出安全 UI |
| UP-EXT-001 | Script Provider companion | 独立权限、审计、Schema 输出和安装边界 |
| UP-EXT-002 | Renderer companion SPI | CSP、XSS、兼容版本和受信任插件边界明确 |
| UP-MIG-004 | Retirement Dashboard | 活动配置、生成源、队列和历史 Build 分别报告 |
| UP-MIG-005 | Active Choices adapter | 不执行来源脚本，生成 Provider/Cascade/Form 骨架和验证 fixture |
| UP-MIG-006 | List Git Branches adapter | 不复制 URL/Credential，生成受治理 Git refs Provider 骨架 |
| UP-INT-015 | File interoperability | 原生文件参数继续走 Jenkins multipart；Typed JSON 明确 409，不静默丢文件 |

## 10. 非功能目标

### 10.1 性能

- 1,000 个静态 Option 的页面可交互时间不超过 500ms（本地基准环境）。
- 10,000 个 Option 搜索响应不超过 100ms p95。
- 页面虚拟化后可见 Option DOM 节点不超过 200。
- Provider 默认 timeout 5 秒，管理员最大可配置 30 秒。
- Reactive 请求支持取消，不允许旧响应覆盖新状态。

### 10.2 可用性

- Provider 失败不能导致 Jenkins 页面 HTTP 500。
- 错误必须显示用户可行动的信息。
- 可配置 fail-closed、empty 或 last-known-good。
- Jenkins 重启后缓存和配置行为明确。

### 10.3 兼容性

- 发布时支持当前 Jenkins LTS 和前一个受支持 LTS 基线。
- 实际发布矩阵必须记录 `pom.xml` 最低基线、发布时受支持 LTS 和集成插件版本。
- Freestyle、Pipeline、Multibranch、Folder 和 Rebuild 自动化测试。
- Plugin Compatibility Tester 通过。
- 配置升级提供 `readResolve` 或迁移器，不静默改变语义。

配置兼容规则见
[`Configuration and Compatibility Lifecycle`](../architecture/configuration-lifecycle.md)。

## 11. 安全目标

核心插件：

- 不执行任意 Groovy。
- 不执行任意 JavaScript。
- 不读取任意 Controller 文件。
- 不渲染任意 HTML。
- 所有 POST endpoint 检查权限和 CSRF。
- 所有输出正确转义并符合 CSP。
- 所有值服务端校验。
- 管理员配置和普通 Build 权限分离。

Script Provider、Git Provider、Registry Provider 等高风险或领域能力在
单一 HPI 内保持独立权限、代码和开关边界；外部实现仍可通过 SPI 扩展。
Endpoint 权限和 API 行为见
[`ADR 0006`](../decisions/0006-api-input-errors-and-permissions.md)。

| ID | Release | 需求 | 验收标准 |
|----|---------|------|----------|
| UP-SEC-001 | `1.0` | Endpoint permission/CSRF | 每个 GET/POST 有权限矩阵和负向测试 |
| UP-SEC-002 | `1.0` | Server validation/output escaping | 所有入口服务端校验，所有 Option 文本安全输出 |
| UP-SEC-003 | `1.1` | Credential/target binding | Credentialed REST 只能引用受治理 Connection Profile |
| UP-SEC-004 | `1.1` | SSRF/network policy | DNS、redirect、proxy、IPv4/IPv6 和 metadata 测试通过 |
| UP-SEC-005 | `1.2` | Snapshot/cache isolation | cross-Job、cross-Folder 和 replay 测试通过 |
| UP-SEC-006 | `1.1` | Provider budgets | principal/Job/Profile/global 限流、bounded queue、429/Retry-After |

## 12. 可观测性

管理员可以查看：

- Provider 最近成功和失败。
- 延迟和缓存命中。
- 当前缓存新鲜度。
- fallback 次数。
- 各 Provider 的 Job 使用范围。
- 验证失败类型统计。

默认不采集参数实际值。任何 telemetry 必须匿名、聚合并遵循 Jenkins telemetry 规范。

## 13. 发布门禁

### 13.1 `0.1 Technical Preview`

- [ ] 所有 UP-SPIKE 项完成。
- [ ] 最低 Jenkins baseline 和目标 LTS 的测试报告完成。
- [ ] Standard REST 4xx/no-queue、mixed Typed API、CLI、Rebuild 和 XStream validation 均有可执行 fixture。
- [ ] `modelVersion: 1`、API `/v1` 和稳定 Symbol 尚未冻结。

### 13.2 `1.0 GA`

- [ ] `0.1 Technical Preview` 全部门禁通过并关闭设计反馈。
- [ ] 需求追踪清单中所有标记为 `1.0` 的需求完成。
- [ ] Parity Test Matrix 中所有适用入口和语义维度通过。
- [ ] 真实 UI Gallery 包含默认、交互、Rebuild、validation、empty 和 error。
- [ ] 浅色、深色、移动端和键盘测试通过。
- [ ] 10,000 Option 性能门禁通过。
- [ ] SpotBugs、Checkstyle、Jenkins Security Scan 和依赖扫描通过。
- [ ] Extended Choice 静态迁移 dry-run 通过真实样本验证。
- [ ] 威胁模型、兼容性承诺、迁移和回滚文档完成。
- [ ] Jenkins 社区 hosting、名称和安全评审完成。

可执行门禁定义见：

- [`Requirement Release and Test Manifest`](../testing/requirements.yaml)。
- [`Parity Test Matrix`](../testing/parity-test-matrix.md)。
- [`Performance and Accessibility Test Plan`](../testing/performance-accessibility-plan.md)。
- [`UI Interaction Specification`](./ui-interaction-spec.md)。

任何入口仍依赖页面 DOM、任何静态列表仍必须写脚本、或 Rebuild 退化为无验证文本框时，
不得发布 `1.0`。

## 14. 明确非目标

- 复刻旧插件的任意脚本、文件和 HTML 能力。
- 成为通用低代码平台。
- 在 `1.0` 支持所有云、Git、制品和镜像仓库。
- 自动迁移无法证明语义等价的动态参数。
- 仅靠客户端 JavaScript 校验。
- 为了兼容历史行为而保留隐式第一项默认值。

## 15. 下一步

设计基线已经补齐。下一步执行：

1. 实现 `0.1 Technical Preview` 和全部 UP-SPIKE fixture。
2. 根据 spike 结果修订 ADR；通过后再冻结 `modelVersion: 1` 和 API `/v1`。
3. 建立 `1.0` Jenkins Test Harness parity fixture。
4. 实现真实插件 UI 并生成目标 Gallery。
5. 将 Extended Choice 静态迁移 adapter 合入单一 HPI。
6. 准备社区 RFC、hosting 和名称最终检查材料。
