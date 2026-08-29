# 发布候选包校验

候选发布包必须包含三套 manifest：副本数为 0 的停写清单、副本数为 1 的 canary 清单，以及副本数为 4 的正式清单；同时还要包含数据库迁移 `Job`、27 个 `.sql` 迁移文件和覆盖全部文件的 `SHA256SUMS`。候选包中不得包含任何 `Secret`。

生成候选包后，切换到 `staging-eu2` Kubernetes context，执行：

```bash
kubectl apply --server-side --dry-run=server -f dist/
```

这一步不会直接操作生产环境，也不会修改生产环境。通过 server-side dry-run，可以让配置走真实的 API 校验链路，确认其能够无缝通过，为后续生产发布建立可验证、可追溯、可复现的交付闭环。

如果校验报告字段不兼容或资源冲突，应立即停止，先修正候选包，再重新执行校验。
