# 发布候选包校验

下一步不要直接操作生产环境。先生成不含 `Secret` 的候选发布包，然后切换到 `staging-eu2` Kubernetes context，执行 `kubectl apply --server-side --dry-run=server -f dist/`。这会通过真实的 API 校验链路检查配置。

候选包必须包含三套 manifest：副本数为 0 的停写清单、副本数为 1 的 canary 清单，以及副本数为 4 的正式清单。此外，还必须包含数据库迁移 `Job`、27 个 `.sql` 迁移文件，以及覆盖全部文件的 `SHA256SUMS`。

此步骤不会修改生产环境，也不会把任何 `Secret` 写入候选包。如果 server-side dry-run 报告字段不兼容或资源冲突，应立即停止，修正候选包后再重新校验。
