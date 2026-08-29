# 发布候选包校验

下一步不要直接操作生产环境，而是先完成一套可验证、可追溯、可复现的发布准备流程。先生成不含 `Secret` 的候选发布包，然后切换到 `staging-eu2` Kubernetes context，执行：

```bash
kubectl apply --server-side --dry-run=server -f dist/
```

这一步会通过真实的 Kubernetes API 校验链路检查配置，确认候选包能够顺利通过校验。

候选包必须包含三套不同阶段使用的 manifest：副本数为 0 的停写清单、副本数为 1 的 canary 清单，以及副本数为 4 的正式清单。此外，还必须包含数据库迁移 `Job`、27 个 `.sql` 迁移文件，以及覆盖全部文件的 `SHA256SUMS`。

需要注意，这一步不会修改生产环境，也不会将任何 `Secret` 写入候选包。如果 server-side dry-run 报告字段不兼容或资源冲突，应立即停止，修正候选包后再重新校验。只有校验通过，才能为后续生产发布提供可靠的交付基础。
