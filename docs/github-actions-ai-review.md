# GitHub Actions AI Code Review 使用说明

这份说明对应仓库里的工作流文件 [`/.github/workflows/code-review-ci.yml`](../.github/workflows/code-review-ci.yml)。

## 功能概述

当 Pull Request 目标分支为 `main` 或 `v2`，并且 PR 处于非 Draft 状态时，GitHub Actions 会自动：

1. 使用 GitHub App 生成短期 `installation token`
2. 拉取 PR 的 diff
3. 调用配置的大模型接口做代码评审
4. 将评审结果回写到 PR
5. 如果发现“必须修改”项，则提交 `REQUEST_CHANGES` 并让 job 失败，阻止合并

## 需要准备的 GitHub App

建议新建一个专门用于代码评审的 GitHub App。

### 权限建议

- `Pull requests: Read & write`
- `Contents: Read`

如果你的组织还有更严格的安全要求，可以再按实际需要缩小权限范围，但至少要保证能够读取 PR 内容并写回 review。

### 需要记录的三个值

创建并安装 GitHub App 后，请准备下面三个值：

- `GH_APP_ID`：GitHub App 的 App ID
- `GH_APP_PRIVATE_KEY`：GitHub App 生成的私钥内容
- `GH_APP_INSTALLATION_ID`：这个 App 安装到当前仓库后的 installation id

## 需要配置的 Secrets

在仓库的 `Settings -> Secrets and variables -> Actions` 中配置下面这些 secrets：

- `GH_APP_ID`
- `GH_APP_PRIVATE_KEY`
- `GH_APP_INSTALLATION_ID`
- `JT_OPENAI_API_KEY`
- `JT_OPENAI_API_URL`

可选项：

- `AI_REVIEW_PROMPT`：自定义评审提示词，模板中用 `{code_diff}` 占位

建议配置的 repository variables：

- `AI_REVIEW_MODEL`：默认模型名，例如 `deepseek-reasoner`
- `AI_REVIEW_MAX_DIFF_CHARS`：单次评审允许传给模型的 diff 最大字符数，例如 `40000`

## 工作流触发条件

当前工作流只会在以下场景运行：

- `pull_request_target`
- PR 类型为 `opened`、`synchronize`、`reopened`、`ready_for_review`
- 目标分支为 `main` 或 `v2`
- PR 不是 Draft

## 代码评审流程

工作流内部的执行顺序如下：

1. 读取 PR 上下文
2. 使用 GitHub App 私钥生成 JWT
3. 向 GitHub API 兑换 `installation token`
4. 使用 `installation token` 获取 PR diff
5. 将 diff 送入大模型接口
6. 解析模型输出，判断是否存在“Must Fix”
7. 提交 GitHub Review 或普通评论

如果结果包含“必须修改”项：

- 会提交 `REQUEST_CHANGES`
- job 会返回失败

如果没有“必须修改”项：

- 会提交普通 review comment
- job 正常结束

## 自定义提示词

如果你想调整评审风格，可以通过 `AI_REVIEW_PROMPT` 自定义提示词。

注意：

- 模板中必须保留 `{code_diff}` 占位符
- 工作流会把该占位符替换成实际 diff
- 建议保留“必须修改”和“建议优化”两个章节，方便自动判断

## 常见问题

### 1. 为什么没有触发评审？

请检查：

- PR 的目标分支是不是 `main` 或 `v2`
- PR 是否还是 Draft
- Workflow 是否已经在仓库中启用
- GitHub App 是否已经安装到当前仓库

### 2. 为什么提示缺少 GitHub App 凭据？

请确认下面三个 secrets 是否都已填写：

- `GH_APP_ID`
- `GH_APP_PRIVATE_KEY`
- `GH_APP_INSTALLATION_ID`

### 3. 为什么提示大模型接口不可用？

请确认：

- `JT_OPENAI_API_KEY` 是否有效
- `JT_OPENAI_API_URL` 是否可访问
- 你的模型供应商是否支持 OpenAI 兼容接口

### 4. 为什么 review 提交失败？

如果 GitHub review API 返回错误，工作流会自动退回为普通 PR comment。
如果连 comment 也失败，通常是 GitHub App 没有 `Pull requests: write` 权限，或者 installation id 不正确。

## 推荐的最小配置清单

如果你想快速跑起来，最少只需要配置：

- `GH_APP_ID`
- `GH_APP_PRIVATE_KEY`
- `GH_APP_INSTALLATION_ID`
- `JT_OPENAI_API_KEY`
- `JT_OPENAI_API_URL`

然后确认 GitHub App 已安装到目标仓库，且具备 `Pull requests: write` 权限。
