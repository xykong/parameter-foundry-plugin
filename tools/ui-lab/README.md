# Jenkins 参数插件 UI Lab

本目录使用真实 Jenkins Core、真实插件和真实 Job 生成参数插件 UI 截图。

它不是手工 HTML mock，也不连接生产 Jenkins。所有截图来自一次性本地 Docker Controller，
使用虚拟参数和无业务动作 Job。

## 当前范围

当前覆盖全部通用候选：

- Jenkins Core Parameters。
- Extended Choice Parameter。
- Active Choices。
- Extensible Choice Parameter。
- Editable Choice。
- REST List Parameter。
- Custom Checkbox Parameter。
- Multiselect Parameter。
- Validating String Parameter。
- HTML Parameters。
- JSON Parameter。
- Json Editor Parameter。

每个 profile 至少生成：

- Freestyle 配置页面。
- Freestyle 默认参数页面。
- Freestyle 用户交互状态。
- Pipeline 默认参数页面。
- Pipeline Rebuild 页面。

另外覆盖真实的：

- 正则校验失败。
- Reactive 依赖参数刷新。
- Reactive 请求进行中。
- Provider 空结果。
- Provider 解析错误。

生成结果：

- `../../docs/ui-gallery/index.html`：可筛选的静态 Gallery。
- `../../docs/ui-gallery/README.md`：仓库内可直接浏览的缩略图索引。
- `../../docs/ui-gallery/manifest.json`：Jenkins Core、插件版本、场景和截图元数据。
- `../../docs/ui-gallery/images/`：真实 PNG 截图。

## 安全边界

- Controller 只绑定 `127.0.0.1:18080`。
- 不读取生产配置、Job、凭据或 Cookie。
- 每个插件或 Core 基线使用独立的临时 Jenkins Home。
- Extended Choice Parameter 仅加载虚拟静态选项。
- REST 数据来自同一临时 Controller 的 `userContent` 测试 JSON。
- Lab 结束后自动删除容器和临时 Home。
- 下载的插件和临时运行数据保存在 `.cache/`，不提交 Git。

## 使用方式

准备所有 profile 的插件：

```bash
make prepare
```

生成完整 Gallery：

```bash
make capture
```

查看：

```bash
open ../../docs/ui-gallery/index.html
```

清理临时容器和缓存 Home：

```bash
make clean
```

## 复现约束

- Jenkins Core：`2.568.2-lts-jdk21`。
- 目标插件版本固定在各 profile 的 `plugins.txt`。
- 传递依赖由 Jenkins Plugin Installation Manager 解析。
- 截图使用本机 Google Chrome 的 headless 模式。
- 默认 viewport 为 `1440x1100`。

插件版本或 Jenkins Core 变化后，应重新生成 Gallery，并评审视觉和行为差异。

## Built-in Foundry file gate

Run `uv run tools/ui-lab/scripts/check-foundry-files.py` from the repository root.
It builds the exact HPI, starts a disposable localhost controller and verifies
real browser upload in all three modes, workspace filename semantics, Pipeline
stash/temporary files, download, restart/Rebuild, automatic Freestyle delivery,
configurable limits, Core clone/apply/build/rollback, duplicate-property
normalization, legacy Rebuild rejection/fresh upload, mobile overflow and Typed
JSON rejection. Results are written to `docs/testing/results/foundry-file-modes-ui-lab.json`;
desktop/mobile, configuration and Core migration screenshots join the gallery. No source File Parameters HPI
is installed, so the gate also proves independent operation.

## Active Choices data mapping regression

Use a fresh loopback controller with Parameter Foundry and `uno-choice:2.8.10`.
The standalone initializer `scripts/active-choices-mapping-init.groovy` refuses
a nonempty controller, sets zero executors and creates four synthetic jobs with
no builders. It is separate from the multi-profile gallery seed.

Run `uv run tools/ui-lab/scripts/check-active-choices-mapping.py` with the
controller listening on localhost:8080. The check compares native source
cascades with Foundry mappings at desktop and mobile sizes, exercises manual
choices, ordinary retention, empty defaults, dependency errors, rapid changes
and failed-request recovery, and never submits a build. Artifacts are written
to the ignored `.cache/active-choices-mapping` directory.

## Whole-response REST browser gate

After a complete `mvn -B -ntp verify`, run:

```bash
uv run tools/ui-lab/scripts/check-raw-rest-provider.py --hpi plugin/target/parameter-foundry.hpi
```

This gate pins Playwright 1.63.0. If its Chromium archive is absent, install it
with `uv run --no-project --with playwright==1.63.0 playwright install chromium`
before running the gate.

The gate copies and hashes the exact local Maven test dependency archives, checks
the loaded HPI hash and Jenkins 2.568.3 version, and uses a fresh internal Docker
network. A fixed nginx reverse proxy exposes a random host port on localhost;
the controller stays on the internal network, has zero executors, and creates
three synthetic parameter-only Jobs. The cached `nginx:1.30.5-alpine` image must
already exist (the runner refuses to pull it). The mock service runs
inside that same owned container at its non-loopback private address; network
policy is not replaced or relaxed for loopback. No production configuration,
credentials or cookies are read.

The browser checks configuration save/roundtrip, whole-body API values and
source UI values at 1440 and 390 pixels, non-filterable dropdowns, explicit
HTTP failure choices, configured fallback followed by recovery, and keyboard
selection. The DNS case additionally saves `DNS_UNRESOLVED`, checks its declared
UI/native default, restores only the owned hostname in that same container’s
`/etc/hosts`, and verifies recovery with the same Connection Profile. This gate
requires the DNS declaration support added in 0.24. The fixture JVM alone uses
`raw-rest-dns.security` with positive/negative DNS TTL zero so recovery is
immediate; production JVM DNS caches are untouched. DNS/error/recovery/keyboard
cases cover desktop only. It blocks build/rebuild POSTs and verifies no builds
or queued tasks.
Screenshots, archive hashes and JSON results stay in the ignored timestamped
`tools/ui-lab/.cache/raw-rest-*` directory for inspection. Cleanup checks the
original created resource IDs before attempting each removal, and records
results in `cleanup.json`. Check that receipt for cleanup errors; unrelated
containers are not touched.
