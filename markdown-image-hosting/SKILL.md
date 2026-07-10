---
name: markdown-image-hosting
description: Upload local images referenced by Markdown documentation to a configured image host and rewrite Markdown image links to hosted URLs. Use when Codex needs to publish docs containing local image paths, screenshots, generated diagrams, or exported assets; supports configurable image hosts, with S3-compatible storage as the first backend.
---

# Markdown Image Hosting

Use this skill when a Markdown document contains local image references that
should be uploaded to an image host before publishing.

## Workflow

1. Find Markdown image links such as `![Alt](./images/flow.png)`.
2. Leave existing `http://`, `https://`, `data:`, anchor, and mail links alone.
3. Run the bundled uploader in dry-run mode first:

```bash
~/.vibelab-tools/agent-skills/markdown-image-hosting/bin/upload-doc-images \
  path/to/document.md
```

4. Inspect the JSON summary and confirm the local files and generated object
   keys are correct.
5. Upload and rewrite the Markdown file:

```bash
~/.vibelab-tools/agent-skills/markdown-image-hosting/bin/upload-doc-images \
  path/to/document.md \
  --write
```

Use `--output updated.md` instead of `--write` when the original file should
remain unchanged.

## Configuration

The runtime config is created by `make install-runtime`:

```text
~/.vibelab-tools/agent-skills/markdown-image-hosting/config.json
```

The `active_host` selects a configured image host from `image_hosts`. The
`s3-compatible` backend uses boto3 and works with AWS S3, Cloudflare R2, MinIO,
and other S3-compatible object stores.

Keep secrets out of Markdown and tracked files. Prefer `access_key_id_env` and
`secret_access_key_env` in config, then set those environment variables in the
shell or secret manager used to run the uploader.

## Notes

- The uploader only rewrites Markdown image links that point to existing local
  files.
- Generated object keys include a short content hash to reduce collisions.
- The script prints a JSON summary for both dry-run and upload runs.
- Add new image hosts under `scripts/image_hosts/` and register them in
  `scripts/upload_doc_images.py`.
