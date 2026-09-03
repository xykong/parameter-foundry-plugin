# Unified Parameters 功能设计

## 1. 功能结论（先读）

| 决策项 | 内容 |
|--------|------|
| 产品 | **Unified Parameters** |
| 目标 | 成为 Jenkins 通用参数领域的功能超集和首选实现 |
| 覆盖原则 | 全部竞品有效使用场景必须有明确替代路径 |
| 兼容原则 | 追求语义正确和迁移可控，不追求旧实现逐字节兼容 |
| 安全原则 | 高风险能力隔离到受控 Provider 或 companion plugin |
| 迁移原则 | 发现、分类、转换、验证、回滚、下线全流程产品化 |
| UI/API 一致性 | 参数业务语义强一致；不一致不得发布 |
| 完成标准 | 每个竞品功能都有目标能力、迁移器、测试和 Gallery 证据 |

Unified Parameters 的最终目标不是“比 Active Choices 更好”，而是：

> 使用一个统一平台，替代 Jenkins Core 基础参数之外所有通用参数插件，并让旧插件用户在功能、
> 安全、UI、自动化和维护成本上获得足够明显的收益。

这里的“替代”指用户业务需求和可观察行为可以 1:1 或更好地迁移，不指旧插件 Java class、
XML、Pipeline `$class` 或历史构建记录的无缝兼容。规范定义见
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)。

## 2. “100% 替代”的定义

### 2.1 必须达到

- 每个已调研插件的有效使用场景都有目标实现。
- 每类来源配置都有自动迁移或明确的辅助重构流程。
- 完成功能迁移后，活动 Job 可以只使用 Unified Parameters 进行配置、构建、自动触发和 Rebuild。
- Freestyle、Pipeline、API、CLI、Webhook、Upstream 和 Rebuild 行为一致。
- 对无法自动等价转换的配置，提供可执行决策而不是简单标记“不支持”。
- 所有迁移路径都有测试样本和回滚方式。

历史 Build 中已经序列化的来源插件对象属于独立的退役决策，不属于业务需求迁移门槛，见
[`ADR 0008`](../decisions/0008-rebuild-and-historical-compatibility.md)。

### 2.2 明确不追求

- 原 Java class、XML tag 或 Pipeline `$class` 兼容。
- 继续执行来源不明的非 sandbox Groovy。
- 继续读取任意 Controller 文件路径。
- 在 Job 配置中保存任意 JavaScript。
- 使用任意 HTML/CSS 绕过 Jenkins CSP 和权限模型。
- 保留竞品错误的默认值、空值、分隔符和非法值行为。

### 2.3 替代层级

| 等级 | 含义 | 示例 |
|------|------|------|
| Direct | 配置可以直接转换，语义不变 | 静态单选、多选、Radio、Checkbox |
| Semantic | 用新模型实现相同业务目标 | `:selected` 转成结构化 default |
| Refactor | 需要用户确认更安全的新实现 | Groovy 网络请求转成 REST Provider |
| Extension | 需要受控 companion plugin | 复杂动态脚本转成 Script Provider |
| Reject-and-redesign | 旧行为本身不应保留 | 任意 JavaScript、未授权文件读取 |

即使属于 `Reject-and-redesign`，Migration Assistant 也必须说明：

- 原配置试图实现什么目标。
- Unified Parameters 应使用什么功能。
- 哪些输入需要用户补充。
- 如何验证结果。

## 3. 调研对象与最终覆盖范围

统一覆盖：

1. Jenkins Core String、Text、Boolean、Choice 和 File interoperability。
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
13. Scriptler 作为动态脚本来源。

专用领域插件不直接并入核心，但通过 Provider SPI 支持：

- Git refs，包括 List Git Branches migration adapter。
- Artifact repository。
- Container registry。
- Cloud resource。
- Node/label。

## 4. 竞品功能覆盖矩阵

### 4.1 Jenkins Core

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| String | `unifiedText` | Direct |
| Text | `unifiedText(multiline: true)` | Direct |
| Boolean | `unifiedBoolean` / Unified Form boolean | Direct |
| Choice | `unifiedChoice` | Direct |
| 首项隐式默认 | 显式 `defaultMode: first` | Semantic |
| API/CLI | 统一 Value Parser | Direct |
| File upload | 保留 Jenkins multipart 原生参数；Typed JSON 显式不支持 | Interoperate |

Core 参数继续可用；Unified Parameters 不要求用户迁移简单 Core 参数，但必须提供一致且更丰富的替代。

### 4.2 Extended Choice Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Single/Multi Select | Choice + cardinality | Direct |
| Radio/Checkbox | Renderer | Direct |
| Text/Hidden | Text、Hidden renderer | Direct |
| Multi-level Select | Cascade Provider + column renderer | Semantic |
| 静态 `value` | Inline Provider | Direct |
| property file | Managed Config / SCM File Provider | Refactor |
| Groovy script | Provider 或 Script Provider | Refactor/Extension |
| Groovy script file | SCM Script Provider | Extension |
| default property/script | Provider default resolver | Refactor |
| custom delimiter | Environment Export policy | Semantic |
| quote value | JSON/export encoding | Semantic |
| description mapping | Option description | Direct |
| JSON Editor | Unified Form | Semantic |
| JavaScript | Declarative Renderer 或 Renderer extension | Reject-and-redesign |

### 4.3 Active Choices

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Active Choice | Provider + Choice renderer | Direct/Semantic |
| Reactive Choice | Dependency Graph | Semantic |
| Reactive Reference | Read-only Field/Content renderer | Semantic |
| Groovy List/Map | Inline、REST、JSON 或 Script Provider | Refactor |
| Scriptler | Unified Script Provider adapter | Extension |
| fallback script | Provider fallback chain | Semantic |
| filterable | Searchable renderer | Direct |
| `:selected` | Option.selected/defaults | Semantic |
| `:disabled` | Option.disabled | Semantic |
| `ET_TEXT_BOX` | Unified Form string field | Semantic |
| `ET_ORDERED_LIST`/`ET_UNORDERED_LIST` | Read-only Content or explicit typed choice | Semantic |
| `ET_FORMATTED_HTML` | Structured Content/Unified Form or reviewed Renderer | Reject-and-redesign |
| `ET_FORMATTED_HIDDEN_HTML` | Retain or explicit business-field redesign | Manual/Class D |
| `omitValueField` | Explicit presentation-only/output decision | Semantic |
| DOM 参数查找 | 服务端依赖图 | Semantic |

### 4.4 Json Editor Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| JSON Schema object | Unified Form Schema | Semantic |
| enum | Choice Field | Direct |
| array | Multi-choice Field | Direct |
| number/string/boolean | Typed Field | Direct |
| start values | Form defaults | Direct |
| editor options | Layout/Renderer options | Semantic |
| Pipeline `input` | Unified Form 支持 Job 参数和 Input Step | Semantic |
| JSON output | Structured Parameter Value | Direct |

### 4.5 Extensible Choice Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Textarea Provider | Inline Provider | Direct |
| Global Textarea | Global/Folder Provider | Direct |
| Filename Provider | Managed File / SCM Provider | Refactor |
| System Groovy | Script Provider companion | Extension |
| editable mode | Editable Choice policy | Direct |
| add edited choice | Managed Option contribution workflow | Semantic |
| ChoiceListProvider SPI | Unified Provider SPI | Direct |
| Freestyle only限制 | 全入口一致 | 改进 |

### 4.6 Editable Choice

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| autocomplete | Searchable combobox | Direct |
| allow custom | `allowCustomValue` | Direct |
| restrict | `allowCustomValue: false` | Direct |
| prefix/contains | Search policy | Direct |
| case insensitive | Search policy | Direct |
| default value | Default Resolver | Direct |

### 4.7 Custom Checkbox Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Checkbox group | Multiple + Checkbox renderer | Direct |
| Inline YAML/JSON | Inline import | Direct |
| HTTP source | REST Provider | Direct |
| file source | Managed File Provider | Refactor |
| display/value path | Provider field mapping | Direct |
| configured checked | Explicit defaults | Direct |
| select all | Batch action | Direct |
| remember previous selection | User Preference Layer | Semantic |

“记住上次选择”不得改变 API 默认值；它只能作为当前用户的页面偏好。

### 4.8 Multiselect Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| dependent dropdown columns | Cascade Form/Renderer | Direct |
| CSV decision tree | Tree import | Direct |
| variable per column | Field output mapping | Direct |
| title/variable/content rows | Structured Tree Schema | Semantic |
| alternative labels | Option label/value | Direct |
| Pipeline object tree | Unified Cascade DSL | Semantic |

### 4.9 Validating String Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Regex | Regex Validator | Direct |
| inline feedback | Unified validation UI | Direct |
| custom error | Localizable validation message | Direct |
| server validation | Unified Validator | Direct |
| CLI validation | Unified Value Parser/Validator | Direct |

### 4.10 HTML Parameters

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| 多字段表单 | Unified Form | Semantic |
| tabs/sections/grid | Form Layout Schema | Direct |
| text/select/checkbox | Typed Fields | Direct |
| field mappings | Output Mapping | Direct |
| theme-aware UI | Design Tokens | Direct |
| custom CSS | Layout/theme tokens 或 Renderer extension | Refactor |
| arbitrary HTML | Structured Renderer | Reject-and-redesign |

对于无法用 Schema 表达的自定义控件，允许第三方编译型 Renderer plugin，不允许在 Job 中保存任意脚本。

### 4.11 JSON Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| Config File source | Managed Config Provider | Direct |
| HTTP source | REST JSON Provider | Direct |
| Credentials | Connection Profile 绑定 Jenkins Credentials | Direct |
| JSONPath | JSON Query | Direct |
| default/placeholder | Default + Empty State | Direct |
| parameter reference | Dependency Graph | Semantic |
| dynamic refresh | Reactive Provider | Direct |

### 4.12 REST List Parameter

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| JSON/XML REST | REST Provider | Direct |
| Credentials | Connection Profile 绑定 Jenkins Credentials | Direct |
| custom headers | Profile-owned Secret Header / allowlisted non-secret Header | Direct |
| JSONPath/XPath | Query Engine | Direct |
| display/value separation | Option label/value | Direct |
| filter/sort | Provider transform chain | Direct |
| cache TTL | Provider Cache | Direct |
| validation | Unified Validator | Direct |
| allow empty | Empty Policy | Direct |
| empty/error message | Unified State UI | Direct |

### 4.13 Scriptler

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| shared scripts | Script Catalog companion | Extension |
| Git-managed scripts | SCM Script Provider | Extension |
| permissions | Provider-specific permission | Direct |
| parameters/bindings | Typed Provider Context | Semantic |
| arbitrary return | Option/Form schema output | Semantic |

### 4.14 List Git Branches

| 来源能力 | Unified Parameters 目标 | 替代方式 |
|----------|---------------------------|----------|
| branch/tag/ref discovery | governed REST or Git refs companion Provider | Extension |
| repository URL | administrator-owned Connection Profile | Refactor |
| credentials | credential ID in the governed profile | Direct |
| `PT_BRANCH` | Git refs companion `branch` mode | Semantic |
| `PT_TAG` | Git refs companion `tag` mode | Semantic |
| `PT_BRANCH_TAG` | Git refs companion full `ref` mode | Semantic |
| `ORG_BRANCH` | Branch candidate after organization output confirmation | Manual/Class C |
| branch/tag filter/sort | typed Provider configuration and transform chain | Semantic |
| default and deleted ref | explicit default/stale validation | Semantic |

Migration adapter 只读取类型和公开配置元数据，生成 Provider 骨架，不复制 URL、Credential
或 Secret。`ORG_BRANCH` 不是官方模式；只有确认输出是单个 branch string，而不是
organization/repository/ref 组合后，才能使用 branch candidate。

### 4.15 File Parameters

文件上传是 multipart transport，不是 Option Provider。Core File 和原生 Stashed File
参数可继续与 Unified Parameters 共存。标准 Jenkins 页面和 endpoint 保持原行为；JSON Typed
API 对包含文件定义的 Job 原子返回 unsupported，不把“未上传”伪装成成功。

## 5. 功能架构

### 5.1 核心层

```text
Parameter Definition
  -> Provider
  -> Option / Field Model
  -> Default Resolver
  -> Validator
  -> Renderer
  -> Parameter Value
  -> Export Policy
```

所有页面、API 和触发入口只能调用核心层，禁止绕过 Validator 直接构造值。

### 5.2 参数家族

| 公开类型 | 用途 |
|----------|------|
| `unifiedText` | 单行、多行、隐藏和可验证文本 |
| `unifiedBoolean` | Boolean/switch |
| `unifiedChoice` | 单选、Radio、Editable |
| `unifiedMultiChoice` | 多选、Checkbox、chips |
| `unifiedCascade` | 多列依赖选择 |
| `unifiedForm` | Schema 组合表单 |

内部尽量复用统一模型，但公开 Symbol 保持用途清晰。

`1.0` 只发布 `unifiedChoice` 和 `unifiedMultiChoice`。Editable/custom、Text、Boolean、
Cascade 和 Form 按
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)
的路线进入后续版本。

### 5.3 Renderer

- Select。
- Searchable combobox。
- Radio group。
- Checkbox group。
- Multi-select list。
- Chips。
- Cascade columns。
- Read-only content。
- Form fields/layout。

Renderer 不负责数据获取和合法值判断。

### 5.4 Provider

最终第一方核心 Provider：

- Inline。
- Global。
- Folder。
- Managed Config。
- REST JSON/XML。
- Cascade/Derived。

Companion Provider：

- Script。
- Git。
- Artifact。
- Registry。
- Cloud。
- Node/Label。

### 5.5 Transform Chain

Provider 输出后支持声明式变换：

- select path。
- map label/value/description/group。
- filter。
- sort。
- distinct。
- limit。
- group。
- mark disabled。
- calculate default。

变换过程必须可预览、可测试并记录错误所在步骤。

核心 Java 类型、包结构和 SPI 见
[`Core Architecture`](../architecture/core-architecture.md)。

## 6. Reactive 和依赖

### 6.1 依赖声明

```yaml
dependsOn:
  - ENVIRONMENT
  - REGION
```

禁止 DOM 搜索。Jenkins 服务端构建依赖图并检查：

- 参数不存在。
- 自依赖。
- 循环依赖。
- 跨权限边界。

### 6.2 刷新协议

- 页面发送依赖值和参数定义版本。
- 服务端解析 Provider。
- 每次响应包含 request/version ID。
- 浏览器取消旧请求并拒绝过期响应。
- debounce 可配置。
- 记录 loading、empty、error、stale 和 fallback。

### 6.3 自动触发

非 UI 入口不需要先执行浏览器刷新：

- 调用方可以直接提交最终值。
- 服务端根据同一 Provider 快照验证。
- 默认执行 `fresh-or-resolve`，快照不可用时原子重新解析并验证。
- 可显式使用有最大时效的 last-known-good；核心 `1.x` 不支持 `trust-signed-input`。

精确策略见
[`ADR 0007`](../decisions/0007-provider-snapshots-cache-and-failure.md)。

## 7. Unified Form

### 7.1 Field

- string/text。
- integer/decimal。
- boolean。
- choice/multi-choice。
- date/time。
- read-only content。
- nested object。
- array。

### 7.2 Layout

- section。
- grid。
- tabs。
- collapsible group。
- conditional visibility。
- help/description。

### 7.3 输出

- 单个 JSON/Map。
- 每字段独立参数。
- 环境变量映射。
- Secret 字段禁止明文环境导出。

### 7.4 自定义 UI

不接受 Job 内任意 HTML/JS。扩展方式：

1. Schema 和 Design Token。
2. 受信任 Renderer SPI。
3. 独立签名插件。

## 8. 验证系统

内置 Validator：

- required。
- type。
- enum/known option。
- min/max selection。
- length。
- regex。
- numeric range。
- cross-field。
- disabled/stale option。
- custom value policy。

验证发生在：

- 配置保存。
- 页面实时输入。
- Build POST。
- REST/CLI。
- Upstream。
- Rebuild。

同一错误具有：

- error code。
- localizable message。
- field path。
- remediation hint。

## 9. 入口与输出一致性

### 9.1 保证范围

Unified Parameters 对**参数业务语义**提供 UI/API 强一致保证：

- 可选值。
- 默认值。
- 空值。
- 类型转换。
- 自定义值策略。
- 禁用和过期值。
- Provider 解析。
- Reactive 依赖。
- 验证结果。
- 最终 `ParameterValue`。
- Pipeline `params`。
- 环境变量输出。
- Rebuild 初始值。

UI 专属的视觉表现不要求 API 复刻：

- chips。
- tabs。
- 搜索框的视觉布局。
- 动画。
- 响应式排版。
- hover、tooltip 和键盘焦点。

但这些视觉能力不得改变参数业务语义。例如把 Renderer 从 Checkbox 改为 chips，
页面和 API 的 canonical value 必须完全相同。

管理能力也必须有自动化入口：

- Job/Freestyle 配置：UI、Pipeline DSL、Job DSL。
- Global/Folder Provider：UI 和 JCasC。
- Provider 测试和健康：UI 和 REST。
- Migration Assistant：UI、REST 和 CLI。

### 9.2 一致性承诺

| 场景 | Jenkins 页面 | 标准 Jenkins API | Unified Typed API |
|------|--------------|------------------|-------------------|
| 静态单选/多选 | 完整 Renderer | 支持 | 支持 |
| Editable value | 输入和提示 | 支持 | 支持 |
| Provider 选项 | 自动加载 | schema/resolve API | schema/resolve API |
| Reactive | 依赖变化自动刷新 | 提交依赖值解析 | 提交依赖值解析 |
| Unified Form | Schema Form | JSON 字符串 | JSON Object |
| 默认值 | 页面显示 canonical default | 缺省参数应用同一 default | 缺省字段应用同一 default |
| Validation | 字段级提示 | HTTP 4xx | 结构化 HTTP 4xx |
| Disabled/stale value | 不可选并显示原因 | 拒绝 | 拒绝 |
| Provider empty/error | 明确状态 | 明确 error code | 明确 error code |
| Rebuild | 原 Renderer 和历史值 | 相同校验 | 相同校验 |

### 9.3 唯一求值链

```text
Raw Input
  -> Input Decoder
  -> Dependency Resolver
  -> Provider Resolver
  -> Default Resolver
  -> Canonical Value Parser
  -> Validator
  -> ParameterValue Factory
  -> Export Policy
```

以下入口只能调用同一个 `ParameterEvaluationService`：

- Build with Parameters 页面。
- `buildWithParameters`。
- Typed JSON API。
- Jenkins CLI。
- Pipeline `build`。
- Webhook/Remote Trigger。
- Rebuild。

不得出现：

- 页面 JavaScript 自己决定默认值。
- `createValue(StaplerRequest)` 和 `createValue(CLICommand)` 使用不同规则。
- Rebuild 直接接受历史字符串而不重新验证。
- API 绕过 Provider 或 disabled option 检查。

### 9.4 API 设计

#### 标准 Jenkins API

保留：

```text
POST /job/<job>/buildWithParameters
Content-Type: application/x-www-form-urlencoded
```

支持输入：

- 单值字段。
- 多选按参数显式配置的 `repeated`、`json-array` 或 `delimited` 模式。
- form 入口用 repeated `__unified_parameters_presence` marker 区分 absent 和 explicit empty。
- Unified Form JSON 字符串。

标准 API 不根据 Option 内容猜测编码。Transport decoder 在 Provider 解析前完成，Provider
解析后只负责合法值和 canonical value 验证。

#### Unified Typed API

```text
POST /job/<job>/unified-parameters/api/v1/build
Content-Type: application/json
```

示例：

```json
{
  "values": {
    "CHANNEL": "staging",
    "TARGETS": ["android", "ios"],
    "DEPLOYMENT": {
      "region": "ap-southeast",
      "replicas": 3
    }
  },
  "providerSnapshots": {
    "TARGETS": "snapshot-01J..."
  }
}
```

Typed API 不使用 delimiter 猜测。

Typed API 是 supported-definition Job 的完整调度入口：内置适配 Jenkins Core
String/Text/Boolean/Choice 和 Unified Parameters；存在 unsupported/重复名称 definition 时整体
返回 HTTP 409，不静默套用默认值。Password、Credentials、File 和未适配第三方 definition
不进入核心 Typed API。

#### Discovery API

```text
GET  /job/<job>/unified-parameters/api/v1/schema
POST /job/<job>/unified-parameters/api/v1/resolve
POST /job/<job>/unified-parameters/api/v1/validate
GET  /job/<job>/unified-parameters/api/v1/providers/health
```

用途：

- 非浏览器客户端获取参数 Schema。
- 获取当前选项和默认值。
- 传入依赖参数解析 Reactive 选项。
- 构建前验证。
- 查看 Provider 状态。

请求限制、权限、CSRF、队列响应、错误 family 和版本兼容规则见
[`ADR 0006`](../decisions/0006-api-input-errors-and-permissions.md)。

### 9.5 Canonical Value

不同输入必须归一为相同值：

| 参数 | 页面输入 | 标准 API | Typed API | Canonical |
|------|----------|----------|-----------|-----------|
| single | select | String | String | String |
| multiple | Checkbox/list | configured form mode | Array | unordered selection exposed as deterministic `List<String>` |
| boolean | Checkbox | `true/false` | Boolean | Boolean |
| form | fields | JSON String | Object | immutable Map |

对同一 canonical value：

- `ParameterValue.equals` 业务等价；多选忽略 serialized order。
- Pipeline `params` 等价。
- 环境变量等价。
- 构建记录 canonical value 等价；snapshot、配置 digest 和 provenance 可以不同。

多选确定性序列化统一使用机器值 `String.compareTo` 顺序，不使用 Provider 顺序、点击顺序或
历史提交顺序。

### 9.6 默认值和 UI 个性化

页面首次打开显示的默认值，必须等于 API 省略参数时得到的默认值。

为了覆盖竞品“记住上次选择”能力：

- 可以展示“Restore my last selection”操作。
- 不得自动用用户偏好替换 Job canonical default。
- 用户点击恢复后，视为显式输入。
- 自动触发永远不读取浏览器或用户偏好。

### 9.7 Provider 快照

动态选项可能在页面打开和提交之间变化。为避免 UI/API 时序差异：

- `resolve` 返回 `snapshotId`、生成时间和过期时间。
- 页面提交时携带 `snapshotId`。
- API 可以先 resolve 再 build，也可以直接 build。
- 直接 build 在服务端原子地 resolve、parse 和 validate。
- 快照有效时使用；过期时按默认 `fresh-or-resolve` 原子重新解析并验证。
- 构建记录保存实际使用的 Provider snapshot metadata。

不得记录 Credentials 或完整敏感响应。

Snapshot 绑定、缓存 key、失败和 Jenkins 重启行为见
[`ADR 0007`](../decisions/0007-provider-snapshots-cache-and-failure.md)。
Build-value snapshot 可在同一 Job/definition/dependency 下跨 resolve、validate、build 使用；
diagnostic snapshot 不可用于构建。核心 snapshot 不绑定最初调用用户，但每次使用重新检查权限。

### 9.8 Reactive API

页面和 API 使用相同请求：

```json
{
  "parameter": "ARTIFACT",
  "dependencies": {
    "ENVIRONMENT": "staging",
    "REGION": "ap-southeast"
  }
}
```

响应：

```json
{
  "snapshotId": "snapshot-01J...",
  "options": [
    {
      "value": "client-1.2.3",
      "label": "Client 1.2.3",
      "disabled": false
    }
  ],
  "defaults": ["client-1.2.3"],
  "warnings": []
}
```

页面只是该 API 的一个客户端，不维护额外业务规则。

### 9.9 错误契约

同一错误必须具有稳定 code：

```json
{
  "errors": [
    {
      "code": "UNIFIED_PARAMETER_UNKNOWN_VALUE",
      "parameter": "TARGETS",
      "path": "$[1]",
      "message": "Value is not present in the resolved option set.",
      "hint": "Resolve options again or submit a supported value."
    }
  ]
}
```

- 页面显示同一 code 对应的本地化 message。
- 标准 Jenkins API 返回 HTTP 4xx 和安全错误摘要。
- Typed API 返回完整结构化错误。
- 任何验证错误都不得调度构建。
- 用户输入错误不得返回 HTTP 500。

### 9.10 Parity 需求

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-PARITY-001 | UI 使用统一 resolve/validate 服务 | UI 不包含独立默认或校验逻辑 |
| UP-PARITY-002 | 标准 API 归一化 | 与 UI 产生同一 canonical value |
| UP-PARITY-003 | Typed API | Array/Object 无损转换 |
| UP-PARITY-004 | CLI/Upstream/Remote | 调用统一 Evaluation Service |
| UP-PARITY-005 | Rebuild | 保留 Renderer 并重新验证 |
| UP-PARITY-006 | Provider snapshot | UI 和 API 可使用同一快照 |
| UP-PARITY-007 | Reactive | 页面和 API 使用同一 Dependency Resolver |
| UP-PARITY-008 | Error code | 所有入口 code 一致 |
| UP-PARITY-009 | Default | 页面初值等于 API 缺省值 |
| UP-PARITY-010 | Build record | 保存 canonical value 和 snapshot metadata |

必须支持：

- Freestyle。
- Declarative Pipeline。
- Scripted Pipeline。
- Multibranch Pipeline。
- Job DSL。
- JCasC。
- REST。
- CLI。
- Webhook。
- Pipeline `build`。
- Remote trigger。
- Rebuild。
- Copy/Rename/Reload。

契约测试对同一 fixture 从所有入口提交，比较：

- ParameterValue。
- Pipeline `params`。
- 环境变量。
- 构建记录。
- Rebuild 初始值。
- 错误 code。

### 9.11 发布保证

每个参数类型和 Provider 必须运行
[`Parity Test Matrix`](../testing/parity-test-matrix.md)
中全部适用的配置 surface、submission adapter 和 semantic dimension。

测试维度：

- explicit value。
- default。
- absent。
- empty。
- invalid。
- disabled。
- stale。
- duplicate。
- Provider success/empty/error/timeout。
- Reactive dependency change。

任一入口产生不同 canonical value、默认值或 error code，该功能不得标记完成，也不得进入发布版本。

## 10. Migration Assistant

### 10.1 迁移流程

```text
Discover
  -> Classify
  -> Plan
  -> Preview
  -> Validate
  -> Apply
  -> Observe
  -> Retire source plugin
```

### 10.2 Discover

扫描：

- Freestyle Job。
- Pipeline Job 当前参数属性。
- Folder/Multibranch 子 Job。
- Job DSL/Jenkinsfile 生成来源提示。
- 来源插件和版本。
- 动态脚本、文件、URL 和默认值。
- 最近构建和触发方式。

不得输出 Secret 或脚本中的敏感字面量。

### 10.3 Classify

| 级别 | 含义 |
|------|------|
| A | 可自动等价转换 |
| B | 可自动转换，但有显式语义选择 |
| C | 可生成重构建议和目标骨架 |
| D | 必须人工重新设计 |

### 10.4 Plan

每个参数显示：

- 来源能力。
- 目标能力。
- 行为差异。
- 安全改进。
- 默认/空值变化。
- API/Rebuild 影响。
- 自动化程度。
- 推荐测试。

### 10.5 Pipeline 和生成 Job

- Freestyle 可以支持受控 in-place 迁移。
- SCM Jenkinsfile 只生成 patch/snippet，不直接改远端仓库。
- Job DSL 生成目标 DSL。
- Multibranch 子 Job 不逐个修改，必须修复生成源。
- 检测下一次 `properties()` 是否会覆盖迁移结果。

### 10.6 Apply

模式：

- report-only。
- generate-snippet。
- clone-job。
- in-place。
- batch。

所有修改：

- 保存配置快照。
- 生成 migration record。
- 提供单 Job 和批次回滚。
- 不自动卸载来源插件。

`1.0` 只提供发现、预览、目标配置和验证 fixture；clone、in-place、batch、备份和回滚从
`1.1` 开始。完整架构和并发保护见
[`Migration Assistant Architecture`](../architecture/migration-assistant.md)。

### 10.7 Validate

自动生成测试：

- 默认值。
- 页面提交。
- API。
- Webhook。
- Upstream。
- Rebuild。
- 空值。
- 非法值。
- Provider 错误。

可创建无业务动作 clone Job 先验证参数行为。

### 10.8 Retire

Migration Dashboard 显示：

- 剩余来源 class。
- 运行中/活跃 Job。
- Pipeline 生成源未修复项。
- 来源插件依赖。
- 可以 disable/uninstall 的条件。

## 11. 迁移适配器

每个来源插件提供独立 adapter：

```text
Source Inspector
  -> Normalized Intent
  -> Migration Decision
  -> Unified Definition
  -> Verification Fixture
```

优先顺序：

1. Extended Choice。
2. Active Choices。
3. Editable/Extensible Choice。
4. Custom Checkbox/Multiselect。
5. JSON/REST List。
6. Validating String。
7. Json Editor/HTML Parameters。
8. Scriptler。

Adapter 作为可选 source-specific companion plugin 独立发布更新，以跟进来源插件配置变化。
Adapter 只生成 Normalized Intent，不直接修改 Job 或调度构建。

## 12. 让用户迁移的明确理由

### 12.1 Extended Choice 用户

- 消除已知安全债务。
- 保留所有选择类型。
- 不再用字符串后缀表达状态。
- 有自动迁移和卸载检查。

### 12.2 Active Choices 用户

- 常用动态场景不需要 Groovy。
- Reactive 不依赖 DOM。
- API、Webhook 和 Rebuild 是正式支持入口。
- 有 loading/error/cache/fallback。

### 12.3 Extensible/Editable 用户

- 保留 Provider 和自定义值。
- 获得 Pipeline、多选、Reactive 和统一验证。
- 全局共享可以 Folder 隔离。

### 12.4 REST/JSON 用户

- 统一 Credentials、SSRF、超时、缓存和健康状态。
- display/value/description/group 一次建模。
- 可在多个 Renderer 中复用。

### 12.5 Checkbox/Multiselect 用户

- 不再维护专用格式和专用 ParameterValue。
- 获得搜索、chips、批量操作和一致 API。
- Tree/CSV 可直接导入结构模型。

### 12.6 Json Editor/HTML 用户

- 保留复杂表单能力。
- 不再在 Job 中维护任意 HTML/CSS/JavaScript。
- 获得 Schema 校验、主题、无障碍和稳定输出。

### 12.7 Validating String 用户

- 保留实时和服务端验证。
- 增加类型、跨字段、枚举和所有入口一致性。

## 13. 采用优势必须可证明

| 维度 | 必须证明 |
|------|----------|
| 功能 | 所有竞品 use case 有 fixture |
| 安全 | Core 无任意代码/文件/HTML 执行路径 |
| UI | Gallery 横向优于来源插件 |
| API | 所有入口契约测试一致 |
| 性能 | 10,000 Option 门禁 |
| 可维护性 | 静态场景零脚本 |
| 迁移 | A/B 类自动化，C/D 类有可执行计划 |
| 运维 | Provider 健康、缓存和审计可见 |
| 下线 | 活动配置和生成源的旧 class 归零可验证；历史 Build 有明确退役决策 |

## 14. Epic 和版本路线

路线以
[`ADR 0004`](../decisions/0004-release-scope-and-migration-promise.md)
为唯一来源。

### 14.1 `0.1 Technical Preview`

- UP-E00 Persistence、Pipeline、REST、CLI、Typed API、Rebuild、CSP 和迁移 reactor spike。
- 不冻结 persisted model、API 或兼容性承诺。

### 14.2 `1.0 Static Choice Foundation`

- UP-E01 Core Model。
- UP-E02 Choice/Multi-choice。
- UP-E03 Renderer。
- UP-E04 Defaults/Validation。
- UP-E05 全入口一致。
- UP-E06 Inline Provider。
- UP-E07 Extended Choice 静态迁移发现、预览和目标生成。

可替代：

- Core Choice 的增强场景。
- Extended Choice 静态模式。
- Custom Checkbox 静态模式。

### 14.3 `1.1 Governed Provider Platform`

- UP-E08 Global/Folder/REST/JSON/XML/Managed Config Provider 和 Connection Profile。
- UP-E09 Provider Cache/Health/Security。
- UP-E10 Editable/custom values。
- UP-E11 Job DSL/JCasC。
- UP-E12 静态迁移 apply/clone/rollback。

可替代：

- Extensible Choice。
- Editable Choice。
- JSON Parameter 的非 Reactive 场景。
- REST List Parameter 的非 Reactive 场景。
- Validating String 的 choice/custom-value 场景。

### 14.4 `1.2 Reactive and Cascade`

- UP-E13 Reactive Dependency Graph。
- UP-E14 Provider Snapshot。
- UP-E15 Cascade/Tree。
- UP-E16 Dynamic migration adapters。

可替代：

- Active Choices 常见场景。
- Multiselect Parameter。

### 14.5 `2.0 Complete Requirement Coverage`

- UP-E17 Unified Form。
- UP-E18 Script Provider companion。
- UP-E19 Renderer extension。
- UP-E20 全来源 Migration Assistant。
- UP-E21 完整退役 Dashboard。
- UP-MIG-005 Active Choices 非执行式 adapter。
- UP-MIG-006 List Git Branches governed Provider adapter。
- UP-INT-015 native File parameter interoperability。

可替代：

- Json Editor Parameter。
- HTML Parameters。
- Scriptler/复杂 Groovy 来源场景。
- 全部调研通用参数插件。

## 15. Complete Replacement 发布门禁

- [ ] 覆盖矩阵每行都有实现或安全替代。
- [ ] 每个来源插件至少包含一套真实迁移 fixture。
- [ ] Adapter 声明支持的版本范围和 A 类 fixture 自动迁移成功率 100%，范围外确定性报告 unsupported。
- [ ] B 类参数全部产生明确用户决策。
- [ ] C/D 类全部产生目标骨架和验证步骤。
- [ ] UI、API、CLI、Webhook、Upstream、Remote 和 Rebuild 契约测试通过。
- [ ] Gallery 覆盖所有目标 Renderer 和状态。
- [ ] 活动 Job、生成源和队列中的旧插件 class 引用归零扫描通过。
- [ ] 历史 Build 来源 class 已报告并完成保留、归档或降级展示决策。
- [ ] 安全威胁模型和 Jenkins Security Review 通过。
- [ ] 性能、无障碍、主题和移动端门禁通过。
- [ ] 至少完成两个不同组织的真实迁移试点。

## 16. 非目标与边界

- 不提供旧插件二进制/XML 兼容层。
- 不让核心插件成为 Script Console。
- 不接受 Job 配置中的任意 JavaScript。
- 不允许任意 Controller 路径读取。
- 不承诺自动迁移无法判断业务意图的脚本。
- 不为了“兼容”而保留已知不正确语义。

这些边界不减少业务使用场景覆盖；它们要求把旧的实现方式迁移为可治理的新能力。用户需求
应当能够 1:1 或更好地实现，但配置格式、实现代码和历史序列化对象可以不同。

## 17. 下一阶段输出

设计基线已经形成：

1. Canonical Value 和序列化：ADR 0005。
2. Provider/Renderer/Validator SPI：Core Architecture。
3. REST、错误和权限：ADR 0006。
4. Provider snapshot/cache：ADR 0007。
5. Rebuild 与历史边界：ADR 0008。
6. Migration Adapter：Migration Assistant Architecture。
7. `1.0` UI：UI Interaction Specification。
8. 测试和性能：Parity Test Matrix、Performance and Accessibility Plan。

下一阶段是完成技术 spike、persisted model review、真实插件 UI 原型和契约测试骨架。
