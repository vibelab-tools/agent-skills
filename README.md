# VibeLab Agent Skills

A local collection of agent skills and plugins for Codex, Claude Code, and
other tools that can read the Agent Skills directory format.

The repository is organized as a small workspace. The root `Makefile` delegates
to each skill project so the collection can be built, installed, validated, and
removed with the same target names.

## Skills

| Skill | Purpose | Details |
| --- | --- | --- |
| `markitdown` | Converts documents, media, archives, and supported URLs to Markdown with Microsoft MarkItDown before an agent reasons over them. | [markitdown/README.md](markitdown/README.md) |
| `git-commit` | Drafts, reviews, validates, and creates Conventional Commits 1.0.0 messages from real repository changes. | [git-commit/README.md](git-commit/README.md) |
| `code-refactor` | Provides parser-backed code complexity, smell detection, and bounded refactoring planning through a packaged Java CLI. | [code-refactor/README.md](code-refactor/README.md) |
| `video-understanding` | Analyzes local videos by sampling timestamped frames for agent inspection or sending sampled frames to configured OpenAI-compatible or Gemini vision endpoints. | [video-understanding/README.md](video-understanding/README.md) |
| `relay` | Runs a shared IM relay service and installs Codex plus Claude Code plugin entry points for Telegram, DingTalk, and Feishu workflows. | [relay/README.md](relay/README.md) |

## Install Layout

Regular skills are installed into the native skill directories for each agent:

- Codex: `~/.codex/skills/<skill-name>`
- Claude Code: `~/.claude/skills/<skill-name>`

Skills that need tool dependencies may also install a shared runtime under
`~/.vibelab-tools/agent-skills/<skill-name>`. For example, `markitdown` installs
`markitdown[all]` into `~/.vibelab-tools/agent-skills/markitdown/venv` and exposes a
managed executable at `~/.vibelab-tools/agent-skills/markitdown/bin/markitdown`. It
also installs `markitdown-assets`, which writes local image assets and a manifest
for image-heavy PDF, PPTX, DOCX, XLSX, HTML, EPUB, ZIP, and standalone image
inputs. The installer accepts any Python 3.10+ runtime that passes capability
checks, including pyenv-managed interpreters, and supports
`RUNTIME_PYTHON=/path/to/python` for explicit selection.

`video-understanding` installs runtime configuration under
`~/.vibelab-tools/agent-skills/video-understanding`. Its core dependency is `ffmpeg`,
which is used to extract timestamped frames for both local frame fallback and
provider-backed multimodal analysis.

`relay` is different because it is a long-running service. Its daemon, runtime
files, service controller, and plugin marketplaces are installed under:

```text
~/.vibelab-tools/agent-skills/relay/
```

Codex and Claude Code then register plugin entry points from that shared relay
installation.

## Make Targets

Every subproject supports the same core targets:

```bash
make                  # build every skill project
make install          # install for Codex and Claude Code
make install-codex    # install Codex surfaces only
make install-claude   # install Claude Code surfaces only
make uninstall        # remove installed surfaces
make uninstall-codex  # remove Codex surfaces only
make uninstall-claude # remove Claude Code surfaces only
make validate         # validate skill/plugin structures where supported
make clean            # remove local build outputs where supported
```

For no-build skills, `build` is intentionally a no-op. `code-refactor` builds a
Maven package and bundles a JAR into its installable skill snapshot. `relay`
builds the Node.js daemon and, on install, starts the platform service.

## Platform Notes

The skill metadata and Markdown instructions are portable across Codex and
Claude Code. Executable scripts still depend on the host shell and runtime.

`relay` uses platform-native user services:

- macOS: launchd LaunchAgent
- Linux: `systemd --user`
- Windows: Task Scheduler

The current relay hook scripts are Bash scripts, so Windows Native usage should
run them through WSL or Git Bash until PowerShell hook equivalents are added.

## License

MIT. See [LICENSE](LICENSE).
