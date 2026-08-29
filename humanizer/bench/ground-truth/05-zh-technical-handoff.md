# 事实清单：05-zh-technical-handoff

本清单于任何测试输出生成前冻结。

| ID | 类型 | 必须保留的内容 |
| --- | --- | --- |
| C1 | 操作边界 | 当前步骤不直接操作或修改生产环境。 |
| C2 | 安全边界 | 生成不含 `Secret` 的候选发布包，也不会把 `Secret` 写入包内。 |
| C3 | 环境 | 校验使用 `staging-eu2` Kubernetes context。 |
| C4 | 命令 | 命令必须原样保留：`kubectl apply --server-side --dry-run=server -f dist/`。 |
| C5 | 文件清单 | 三套 manifest 分别是副本数 0 的停写清单、副本数 1 的 canary 清单、副本数 4 的正式清单。 |
| C6 | 文件清单 | 候选包还包含数据库迁移 `Job`、27 个 `.sql` 迁移文件和覆盖全部文件的 `SHA256SUMS`。 |
| C7 | 停止条件 | server-side dry-run 如果报告字段不兼容或资源冲突，必须停止。 |
| C8 | 后续动作 | 停止后先修正候选包，再重新校验。 |

任何生产执行、Secret 注入、不同副本数、不同文件数、命令改写或绕过失败继续发布的表述，均属于硬失败。
