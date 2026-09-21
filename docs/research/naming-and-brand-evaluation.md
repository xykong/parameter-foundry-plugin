# Jenkins 参数插件重命名评估与方案全景分析

## 1. 背景与命名目标

在项目完成 `1.0` / `2.0` 核心能力设计（覆盖单选多选、Reactive 动态联动、Schema 驱动表单、跨 UI/API/Pipeline 一致性对齐与迁移适配器）后，项目组对原工作名称 **Unified Parameters** 的社区传播力与品牌心智进行了全面复盘。

原名称 `Unified Parameters` 准确概括了“跨入口一致性（One parameter model. Every Jenkins entry point.）”的技术特性，但存在明显痛点：
- **发音节奏偏长**：全称多达 6 个音节（`u-ni-fied pa-ra-me-ters`），在中英文口语交流中不易快速念出。
- **定位偏向内部重构**：“Unified”具有浓厚的架构整合感，但缺乏对一线开发与运维人员的感官冲击力与先进性唤起。
- **传播阻力**：用户在寻找下一代动态联动或安全参数方案时，不会主动搜索“统一参数”，使得优质架构在开源生态中的自传播力受阻。

为此，项目组确立了新名称必须同时满足的四大核心维度：
1. **朗朗上口**：音节自然、节奏清晰、中英文发音顺畅无磕绊，利于口口相传。
2. **望文生义**：一眼可知属于 Jenkins 参数体系（Parameter 领域），且能暗示其高级特性（动态、类型安全、工程化）。
3. **让人信任**：具备基础设施级的权威、坚固与工业级稳健感，彻底消除老旧参数插件带来的“脚本漏洞注入”与“脆弱玩具”负面印象。
4. **提高传播能力**：具备高辨识度与强 SEO，在社区讨论中易于成为推荐代名词（“参数问题用 XX”），代码 DSL 书写自然流畅。

---

## 2. 最终选定方案：Parameter Foundry（参数工场）

经过多轮评估与讨论，最终正式确定项目命名为 **Parameter Foundry**。

### 2.1 标识矩阵 (Identity Matrix)

| 要素 | 正式定义 | 说明 |
|---|---|---|
| **正式全称 (Display Name)** | **Parameter Foundry** | 插件中心列表、文档主标题及演讲封面展示 |
| **Artifact ID / 插件短名** | `parameter-foundry` | `pom.xml`、Jenkins 插件包与更新中心短名 |
| **社区仓库 (Repository)** | `jenkinsci/parameter-foundry-plugin` | GitHub 官方仓库地址 |
| **Java Package 根包名** | `io.jenkins.plugins.parameterfoundry` | 全拼小写，消除缩写割裂 |
| **Pipeline 核心符号 (Symbols)** | `foundryChoice`, `foundryForm` | Jenkinsfile 声明式参数定义指令 |
| **官方 Tagline** | *Forge resilient parameters for modern Jenkins.*<br>（为现代 Jenkins 熔铸坚固一致的参数体系） | 完美扣合铸造厂隐喻 |

### 2.2 命名深度品鉴与入选理由

- **让人信任（★★★★★ 满分）**：
  “Foundry（铸造厂 / 晶圆工场 / 冶炼车间）”在现代基础软件界代表着**高强度、精工模具与工业级淬火**（如智能合约测试框架 `Foundry`、大数据中枢 `Palantir Foundry`）。
  老旧的 Active Choices 与 Extended Choice 最为人诟病的是“在 UI 里面随意粘合不受控的 Groovy 脚本”，像粗制滥造的胶水玩具；而 `Foundry` 传达的是**“强类型、Schema 驱动、安全沙箱治理下的标准化模具熔铸”**，天生获得企业安全审计与架构师的高度信赖。
- **望文生义（★★★★★）**：
  前半部 `Parameter` 直击 Jenkins 核心概念，在插件市场搜索 `parameter` 权重最高；后半部 `Foundry` 将插件从“一个简单的下拉框”升维为“全流水线输入参数的生产制造平台”。
- **朗朗上口（★★★★☆）**：
  中文称呼“**参数工场**”或“**Foundry 插件**”非常亲切自然；全拼在国际交流中庄重标准。
- **Pipeline DSL 手感极佳**：
  以 `foundry` 作为命名空间（`foundryChoice`、`foundryForm`），相比原 `unifiedChoice` 更加具有实体感与专属辨识度。

---

## 3. 关键讨论记录：拒绝双轨制（Parameter vs. Param 争论）

在方案探讨过程中，曾出现一种折中思路：**全称使用 Parameter Foundry，而在包名、代码或口语中混用 ParamFoundry（`io.jenkins.plugins.paramfoundry`）**。

经过深入研讨，项目组**全票否决了该折中方案，决定全链条彻底统一使用全拼 `Parameter Foundry` / `parameterfoundry`**。

### 淘汰“双轨制”的核心考量：

1. **杜绝认知分裂与心智磨损**：
   开源项目中“对外一套名字、代码里另一套缩写”是典型的历史包袱坏味道。会导致使用者产生无谓疑惑：“导入包是 `parameterfoundry` 还是 `paramfoundry`？”、“POM 里的 artifactId 到底是哪个？”。全拼杜绝了任何记忆混淆。
2. **字符长度真相对比**：
   开发者直觉认为 `parameter` 太长而倾向缩写，但实际对比项目原包名：
   - 原包名：`io.jenkins.plugins.unifiedparameters`（**17 个字符**）
   - 全拼新包名：`io.jenkins.plugins.parameterfoundry`（**16 个字符**）
   **全拼包名甚至比原包名还要短 1 个字符**！完全符合 Jenkins 官方顶级插件（如 `extendedchoiceparameter`、`pipeline.modeldefinition`）的全拼惯例，完全没有冗长负担。
3. **DSL 符号独立精炼**：
   在 Pipeline DSL 层面，使用 `foundryChoice` 与 `foundryForm`，直接以独一无二的 `foundry` 为空间前缀，既不冗长（避免了 `parameterFoundryChoice`），又与全称概念浑然一体。

---

## 4. 候选命名与淘汰方案全景复盘

在本次重命名决策中，共有 8 组具备代表性的命名方案参与深入比选。以下记录所有被淘汰方案的定位及淘汰原因：

### 淘汰方案 1: ParamFlow (参数流)
- **定位**：强调参数与流水线、API 之间的顺畅流转。
- **优势**：仅 2 个音节，极度顺口，Flow 家族概念在 DevOps 领域认知度高。
- **淘汰原因**：
  1. “Flow”偏向动态过程控制，弱化了本项目核心的“类型约束、表单 Schema、静态配置治理”等工程重器特性；
  2. Jenkins 生态本身充斥着 Workflow / Pipeline 术语，容易让初学者误以为是类似 `input` 步骤的交互式流程控制插件，造成功能定位混淆。

### 淘汰方案 2: Smart Parameters (智能参数)
- **定位**：直击痛点，强调比 Active Choices / Extended Choice 更聪明先进。
- **优势**：零认知门槛，普通开发者搜索意图命中率极高（SEO 强）。
- **淘汰原因**：
  1. “Smart”一词在现代软件界过于泛滥（Smart XXX），缺乏独立品牌辨识度，显露出平庸的同质化感；
  2. 偏向轻量消费级功能描述，缺乏作为企业级核心基础设施的厚重感和严肃度，难以体现出类似 Foundry 的模具化与安全护城河。

### 淘汰方案 3: Paramount (至关重要 / 顶峰双关)
- **定位**：巧妙双关 `Param`（参数）+ `Paramount`（至高无上、最关键的基础设施）。
- **优势**：构词精巧，格调与思想性极高。
- **淘汰原因**：
  1. 对非英语母语开发者不够直观（容易第一时间联想到影视传媒公司 Paramount，而忽略开头的 Param 双关），降低了“望文生义”的得分；
  2. 丢弃了显式的 `Parameter` 词根，在插件中心按字符模糊检索时权重受损。

### 淘汰方案 4: OmniParam (全能参数)
- **定位**：利用现代前缀 `Omni-` 表达跨 UI、API、Webhook、CLI 的全打通能力。
- **优势**：发音快，极客感强。
- **淘汰原因**：
  1. 依然保留了 `Param` 缩写，未能解决全称规范性问题；
  2. “Omni”偏商业化宣传话术，缺少底层工程和开发者的实感。

### 淘汰方案 5: Modern Parameters (现代参数)
- **定位**：直观对照老旧过时插件，树立现代标准。
- **优势**：严肃正统，官方感强。
- **淘汰原因**：
  1. **时效性贬值陷阱**：软件工程中的“Modern”具有快速的时间半衰期，3 到 5 年后“Modern”就会显得落后尴尬（如现代 C++、Modern Web）；
  2. 缺乏具象化的品牌象征物。

### 淘汰方案 6: Reactive Parameters (响应式参数)
- **定位**：突出联动级联、响应式数据流。
- **优势**：技术原理描述极度精准。
- **淘汰原因**：
  1. 严重窄化了产品全貌：让人误以为该插件仅仅是 Active Choices 的联动替代品，忽略了它在强类型统一表单、多端对等 API、安全数据源治理等全局价值；
  2. 对不熟悉 Reactive 响应式编程概念的普通运维人员存在术语壁垒。

### 淘汰方案 7: ParamForge / ParamCraft (参数锻造 / 工艺)
- **定位**：工业感与精工细作。
- **优势**：与 Foundry 理念接近，有一定厚重感。
- **淘汰原因**：
  1. “Forge”易与 Git 仓库托管系统（如 SourceForge、Git Forge）或代码构建器产生概念重叠；
  2. 缩写前缀 `Param` 依然面临与全拼的分裂问题；相较之下 `Foundry`（晶圆代工/高精铸造）比 `Forge`（铁匠锻打）更具精密系统工程的隐喻高度。

### 淘汰方案 8: Paramify (参数化)
- **定位**：SaaS 动词轻量风。
- **优势**：年轻有活力，适合前端类轻量工具。
- **淘汰原因**：
  1. 过于轻量化、玩具化，与企业级 CI/CD 强类型输入中枢的稳健严肃定位相去甚远。

### 原案复盘: Unified Parameters (已淘汰原案)
- **定位**：跨端统一参数模型。
- **淘汰原因**：
  1. 发音音节过长（6音节），传播口语阻力大；
  2. 概念抽象偏被动，缺乏力量感与产品号召力。

---

## 5. 横向综合评估对比矩阵

| 候选方案 | 朗朗上口 | 望文生义 | 让人信任 | 传播力/SEO | 综合评级 | 决议结果 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Parameter Foundry** | 4.5/5 | 5/5 | 5/5 | 5/5 | **S 级** | **正式采纳 (Selected)** |
| **ParamFlow** | 5/5 | 5/5 | 4.5/5 | 4.5/5 | A 级 | 淘汰（概念与 Pipeline 步骤混淆） |
| **Smart Parameters** | 5/5 | 5/5 | 4.0/5 | 5/5 | A 级 | 淘汰（用词同质化、缺少工业厚重感） |
| **Paramount** | 4.5/5 | 4.0/5 | 5/5 | 4.5/5 | A 级 | 淘汰（双关偏生僻、非母语不易联想） |
| **OmniParam** | 4.5/5 | 4.5/5 | 4.5/5 | 4.5/5 | B+ 级 | 淘汰（偏商业抽象、缩写分裂） |
| **Modern Parameters**| 4.0/5 | 5/5 | 4.5/5 | 4.5/5 | B+ 级 | 淘汰（存在时效性贬值缺陷） |
| **Reactive Parameters**| 4.0/5 | 4.5/5 | 4.5/5 | 4.0/5 | B 级 | 淘汰（窄化为单一联动特性） |
| **ParamForge** | 4.5/5 | 4.5/5 | 4.5/5 | 4.0/5 | B 级 | 淘汰（易与 Git Forge 混淆） |
| **Paramify** | 4.5/5 | 4.0/5 | 3.5/5 | 4.0/5 | C 级 | 淘汰（过于轻量单薄） |
| **Unified Parameters**| 3.0/5 | 4.0/5 | 4.5/5 | 3.5/5 | B 级 | **已废弃 (Superseded)** |

---

## 6. Jenkinsfile DSL 体验终态

在选定 `Parameter Foundry` 后，流水线参数定义展示出兼具辨识度与极简美感的书写体验：

```groovy
parameters {
    // 强类型单选/多选参数（铸造标准化选项）
    foundryChoice(
        name: 'DEPLOY_ENV',
        choices: ['staging', 'production'],
        selectionMode: 'single',
        description: 'Target deployment cluster'
    )

    // 级联联动动态参数（依赖 DEPLOY_ENV 响应）
    foundryChoice(
        name: 'SERVICE_CLUSTER',
        provider: httpProvider(url: 'https://internal-api/clusters'),
        referencedParameters: 'DEPLOY_ENV',
        description: 'Dynamically populated service nodes'
    )

    // 复杂类型 Schema 结构化表单（铸造复杂 JSON payload）
    foundryForm(
        name: 'RELEASE_SPEC',
        schema: 'schemas/release-spec.json',
        description: 'Structured enterprise release specification'
    )
}
```

---

## 7. 落地实施路线 (Execution Roadmap)

1. **第一阶段：架构决策与理论基准归档（已就绪）**
   - 新增 `docs/research/naming-and-brand-evaluation.md`（本文档）；
   - 正式发布 `docs/decisions/0017-product-renaming-to-parameter-foundry.md`（英文正式 ADR）；
   - 更新 `docs/decisions/0001-product-name-and-identity.md` 标记状态为 Superseded；
   - 更新 `docs/README.md` 文档索引。
2. **第二阶段：代码与工程元数据全量迁移**
   - 修改 `pom.xml`：`artifactId` 改为 `parameter-foundry`，`name` 改为 `Parameter Foundry`；
   - 迁移 Java 源码包路径：`io.jenkins.plugins.unifiedparameters` -> `io.jenkins.plugins.parameterfoundry`；
   - 迁移 Jelly 与帮助文档路径：`src/main/resources/io/jenkins/plugins/parameterfoundry`；
   - 更新 Pipeline Symbols：`@Symbol("foundryChoice")` 与 `@Symbol("foundryForm")`；
   - 校验全部测试用例并通过 `mvn -B -ntp verify`。
3. **第三阶段：外部用户文档与生态资产同步**
   - 更新 `README.md`、`llms.txt`、`docs/guide/` 体系与 UI Lab 自动化配置。
