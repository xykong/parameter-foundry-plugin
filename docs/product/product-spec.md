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
| 当前阶段 | 独立开发仓库已初始化；尚未开始功能实现 |

**Unified Parameters** 不只是一个新的 Choice 控件，而是 Jenkins 的统一现代参数平台。

它必须解决当前生态最核心的问题：

> 同一个参数定义在配置页面、Build with Parameters、Pipeline、API、CLI、Webhook、
> Upstream、Remote Trigger 和 Rebuild 中使用同一数据模型、默认值、空值和验证规则。

详细的竞品全覆盖、功能 Epic、安全替代和迁移设计见：

[`functional-design.md`](./functional-design.md)。

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
6. **可迁移**：扫描、预览、批次、回滚和旧 class 归零是正式能力。

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
- 默认、禁用和分组使用结构化字段，不编码成 `:selected` 字符串。
- 所有 Provider 输出统一转换为 Option。

### 4.3 Value

| 参数 | Pipeline `params` | 默认环境变量 |
|------|-------------------|----------------|
| single | `String` | 原字符串 |
| multiple | 不可变 `List<String>` | 逗号分隔字符串 |
| form | 不可变结构化 Map | JSON 字符串 |

多选环境变量可配置：

- `delimited`：默认，兼容现有 Jenkins Job。
- `json`：JSON Array。
- `none`：不导出环境变量。

如果选项值包含 delimiter，保存配置时必须报错，或要求切换为 JSON 输出。

## 5. `1.0` 功能目标

### 5.1 参数类型

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-TYPE-001 | 静态单选 | 下拉框和 searchable combobox |
| UP-TYPE-002 | 静态多选 | listbox、chips 和 Checkbox |
| UP-TYPE-003 | Radio | 使用与单选相同的数据和验证模型 |
| UP-TYPE-004 | Checkbox group | 使用与多选相同的数据和验证模型 |
| UP-TYPE-005 | Editable single choice | 可显式允许列表外值 |
| UP-TYPE-006 | Read-only/disabled option | 页面、API 和 Rebuild 均拒绝禁用值 |

### 5.2 Inline Provider

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-PROV-001 | UI 表格编辑 Option | 支持排序、分组、默认和禁用状态 |
| UP-PROV-002 | Pipeline List/Map 配置 | 不要求 Groovy 脚本 |
| UP-PROV-003 | YAML/JSON 导入导出 | 导入前预览差异 |
| UP-PROV-004 | 重复值检测 | 保存前阻止重复机器值 |

### 5.3 默认值和空值

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-SEM-001 | `none` | 不隐式选择第一项 |
| UP-SEM-002 | `first` | 只有显式配置后才选第一项 |
| UP-SEM-003 | `explicit` | 默认值必须存在且可用 |
| UP-SEM-004 | absent/empty-string/empty-list | UI、API、CLI 和 Rebuild 结果一致 |
| UP-SEM-005 | stale default | 默认阻止保存，可显式配置兼容策略 |

### 5.4 验证

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-VAL-001 | required | 空值返回参数级错误 |
| UP-VAL-002 | min/max selections | 页面和服务端同时校验 |
| UP-VAL-003 | allow custom value | 默认关闭 |
| UP-VAL-004 | regex/length | 自定义值可配置 |
| UP-VAL-005 | disabled value | 所有入口拒绝 |
| UP-VAL-006 | unknown value | 返回 HTTP 4xx，不返回 HTTP 500 |
| UP-VAL-007 | duplicate submitted value | 可配置拒绝或去重，默认拒绝 |

### 5.5 Jenkins 入口一致性

| ID | 入口 | 目标 |
|----|------|------|
| UP-INT-001 | Freestyle | 原生配置和构建页面 |
| UP-INT-002 | Declarative Pipeline | 简洁 Symbol |
| UP-INT-003 | Scripted Pipeline | 完整支持 |
| UP-INT-004 | `buildWithParameters` | 支持单值、重复字段和 JSON Array |
| UP-INT-005 | Jenkins CLI | 与 REST 使用同一解析器 |
| UP-INT-006 | Pipeline `build` | 支持 typed value 和字符串兼容输入 |
| UP-INT-007 | Webhook/Remote Trigger | 标准 ParameterDefinition 契约 |
| UP-INT-008 | Rebuild | 保持原 Renderer、Option 和验证规则 |
| UP-INT-009 | Job copy/rename/reload | 行为和配置不漂移 |
| UP-INT-010 | Typed JSON API | 原生 String、Array、Boolean 和 Object |
| UP-INT-011 | Schema/Resolve/Validate API | 非 UI 客户端可完整发现和求值 |
| UP-INT-012 | Provider snapshot | UI/API 可使用同一选项快照 |
| UP-INT-013 | Parity guarantee | 同一输入产生同一 canonical value/error code |

所有入口必须调用同一个：

- `ValueParser`
- `DefaultResolver`
- `ParameterValidator`

不得为页面和 API 分别实现独立规则。

视觉 Renderer 可以只存在于页面，但其可选值、默认值、验证和输出必须与 API 完全一致。
每个功能只有通过 UI/API Parity Test Matrix 后才能发布。

### 5.6 Pipeline DSL

目标语法：

```groovy
parameters {
    unifiedChoice(
        name: 'CHANNEL',
        choices: ['production', 'staging', 'testing'],
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
        defaultValues: ['android', 'ios'],
        renderer: 'chips',
        minSelections: 1
    )
}
```

设计要求：

- `@DataBoundConstructor` 只包含真正必需字段。
- 可选字段使用 `@DataBoundSetter`。
- Descriptor 和所有嵌套 Describable 提供唯一 `@Symbol`。
- Snippet Generator 不输出未修改的默认字段。
- 配置序列化稳定，新增可选字段不破坏旧 Job。

Jenkins 官方建议 Pipeline 插件使用短构造器、`DataBoundSetter` 和唯一 `Symbol`，
本插件必须遵循。

### 5.7 现代 UI

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
| UP-UX-011 | No-JS fallback | 基础静态参数仍可提交 |

### 5.8 配置体验

- 参数预览。
- 默认值预览。
- Pipeline snippet。
- API 请求示例。
- 当前输出值预览。
- 保存前兼容性检查。
- 配置 diff。
- Option 数量、重复值和不可达默认值摘要。

### 5.9 迁移助手

`1.0` 首先支持 Extended Choice 静态参数：

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
- 备份和回滚。
- 批次执行。
- 旧 class 引用归零检查。

## 6. `1.1` Provider 平台

### 6.1 内置 Provider

- Global Provider。
- Folder Provider。
- REST JSON/XML Provider。
- Jenkins Config File Provider。
- JSONPath/XPath。
- 展示值和机器值分离。

### 6.2 Provider 运行契约

每个 Provider 必须提供：

- `load(context)`。
- timeout。
- cache key。
- last-known-good。
- health status。
- Option schema validation。
- 权限要求。
- 可安全记录的诊断摘要。

### 6.3 REST 安全

- Jenkins Credentials ID，不存储 Secret。
- TLS 校验。
- 连接/读取超时。
- 响应大小上限。
- Content-Type 检查。
- 重定向限制。
- DNS/IP 和管理员 allowlist。
- 阻止 loopback、cloud metadata 和受限内网 SSRF。
- JSON/XML 安全解析。
- 日志不输出 Authorization Header 或敏感值。

## 7. `1.2` Reactive 和级联

| ID | 需求 | 验收标准 |
|----|------|----------|
| UP-REACT-001 | 声明依赖参数 | 不扫描页面 DOM |
| UP-REACT-002 | 依赖图 | 服务端检测循环 |
| UP-REACT-003 | 异步刷新 | debounce、取消旧请求、版本号 |
| UP-REACT-004 | 状态展示 | loading、empty、error、fallback 明确 |
| UP-REACT-005 | 非 UI 入口 | 可直接提交并执行同一服务端验证 |
| UP-REACT-006 | 缓存快照 | 构建记录使用的 Provider 版本可追溯 |

## 8. `2.0` Unified Form

`unifiedForm` 用于结构化组合参数：

- text、number、boolean、choice、multi-choice。
- section、grid、tabs 和条件显示。
- JSON Schema 子集。
- 每个字段独立映射，或整体 JSON/Map 输出。
- 服务端 Schema 验证。
- 不允许任意 JavaScript。
- HTML 只允许由受信任 Renderer 生成。

它不是 `1.0` 阻塞项，也不应拖慢 Choice 核心质量。

## 9. 非功能目标

### 9.1 性能

- 1,000 个静态 Option 的页面可交互时间不超过 500ms（本地基准环境）。
- 10,000 个 Option 搜索响应不超过 100ms p95。
- 页面虚拟化后可见 Option DOM 节点不超过 200。
- Provider 默认 timeout 5 秒，管理员最大可配置 30 秒。
- Reactive 请求支持取消，不允许旧响应覆盖新状态。

### 9.2 可用性

- Provider 失败不能导致 Jenkins 页面 HTTP 500。
- 错误必须显示用户可行动的信息。
- 可配置 fail-closed、empty 或 last-known-good。
- Jenkins 重启后缓存和配置行为明确。

### 9.3 兼容性

- 发布时支持当前 Jenkins LTS 和前一个受支持 LTS 基线。
- Freestyle、Pipeline、Multibranch、Folder 和 Rebuild 自动化测试。
- Plugin Compatibility Tester 通过。
- 配置升级提供 `readResolve` 或迁移器，不静默改变语义。

## 10. 安全目标

核心插件：

- 不执行任意 Groovy。
- 不执行任意 JavaScript。
- 不读取任意 Controller 文件。
- 不渲染任意 HTML。
- 所有 POST endpoint 检查权限和 CSRF。
- 所有输出正确转义并符合 CSP。
- 所有值服务端校验。
- 管理员配置和普通 Build 权限分离。

Script Provider、Git Provider、Registry Provider 等高风险或领域能力放入独立 companion plugin。

## 11. 可观测性

管理员可以查看：

- Provider 最近成功和失败。
- 延迟和缓存命中。
- 当前缓存新鲜度。
- fallback 次数。
- 各 Provider 的 Job 使用范围。
- 验证失败类型统计。

默认不采集参数实际值。任何 telemetry 必须匿名、聚合并遵循 Jenkins telemetry 规范。

## 12. `1.0` 发布门禁

- [ ] 所有 UP-TYPE、UP-SEM、UP-VAL、UP-INT、UP-UX `1.0` 项完成。
- [ ] 所有 UP-PARITY 项完成。
- [ ] 10 种入口/语义契约测试通过。
- [ ] 真实 UI Gallery 包含默认、交互、Rebuild、validation、empty 和 error。
- [ ] 浅色、深色、移动端和键盘测试通过。
- [ ] 10,000 Option 性能门禁通过。
- [ ] SpotBugs、Checkstyle、Jenkins Security Scan 和依赖扫描通过。
- [ ] Extended Choice 静态迁移 dry-run 通过真实样本验证。
- [ ] 威胁模型、兼容性承诺、迁移和回滚文档完成。
- [ ] Jenkins 社区 hosting、名称和安全评审完成。

任何入口仍依赖页面 DOM、任何静态列表仍必须写脚本、或 Rebuild 退化为无验证文本框时，
不得发布 `1.0`。

## 13. 明确非目标

- 复刻旧插件的任意脚本、文件和 HTML 能力。
- 成为通用低代码平台。
- 在 `1.0` 支持所有云、Git、制品和镜像仓库。
- 自动迁移无法证明语义等价的动态参数。
- 仅靠客户端 JavaScript 校验。
- 为了兼容历史行为而保留隐式第一项默认值。

## 14. 下一步

名称和产品目标确定后，下一阶段输出：

1. Java 类和扩展点架构。
2. 参数值与环境变量序列化 ADR。
3. REST/API 输入格式 ADR。
4. Rebuild 集成方案。
5. UI 信息架构和可交互原型。
6. Jenkins Plugin Archetype 项目骨架。
7. 社区 RFC 和仓库托管申请材料。
