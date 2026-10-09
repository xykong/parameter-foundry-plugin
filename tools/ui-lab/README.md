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
real browser upload, Pipeline stash and temporary-file access, download bytes,
Rebuild, Freestyle wrapping, mobile overflow and Typed JSON rejection. Results
are written to `docs/testing/results/foundry-files-ui-lab.json`; desktop and
mobile screenshots join the Foundry UI gallery. No source File Parameters HPI
is installed, so the gate also proves independent operation.
