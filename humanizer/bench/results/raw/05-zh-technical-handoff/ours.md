# 发布候选包校验

下一步不要直接操作生产环境。应先生成不含 `Secret` 的候选发布包，然后切换到 `staging-eu2` Kubernetes context（kubectl 当前使用的集群上下文），执行以下命令：

```bash
kubectl apply --server-side --dry-run=server -f dist/
```

该命令只在真实 API 校验链路中检查配置，不会实际应用资源。

候选包必须包含三套 manifest（资源配置文件）：副本数为 0 的停写清单、副本数为 1 的 canary 清单（灰度发布清单），以及副本数为 4 的正式清单。此外，还必须包含数据库迁移 `Job`、27 个 `.sql` 迁移文件，以及覆盖全部文件的 `SHA256SUMS`。

此步骤不会修改生产环境，也不会将任何 `Secret` 写入候选包。如果 server-side dry-run（服务端模拟执行）报告字段不兼容或资源冲突，应立即停止，修正候选包后再重新校验。校验通过后，才能为后续生产发布提供可验证、可追溯、可复现的基础。
