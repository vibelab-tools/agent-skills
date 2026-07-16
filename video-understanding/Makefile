SHELL := /bin/sh

SKILL_NAME := video-understanding
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
LEGACY_TOOL_PARENT ?= $(HOME)/.vibe-coding-skill
LEGACY_TOOL_ROOT ?= $(HOME)/.vibe-coding-skill/$(SKILL_NAME)
CONFIG_FILE := $(TOOL_ROOT)/config.json
CONFIG_EXAMPLE := $(TOOL_ROOT)/config.example.json
VENV_DIR ?= $(TOOL_ROOT)/venv
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
RSYNC ?= rsync
FFMPEG ?= ffmpeg
FFPROBE ?= ffprobe
YT_DLP_PACKAGE ?= yt-dlp[default,curl-cffi]

ifeq ($(OS),Windows_NT)
VENV_PYTHON := $(VENV_DIR)/Scripts/python.exe
VENV_YT_DLP := $(VENV_DIR)/Scripts/yt-dlp.exe
else
VENV_PYTHON := $(VENV_DIR)/bin/python
VENV_YT_DLP := $(VENV_DIR)/bin/yt-dlp
endif

.DEFAULT_GOAL := build
.NOTPARALLEL:

.PHONY: build install install-all install-runtime install-codex install-claude validate uninstall uninstall-runtime uninstall-codex uninstall-claude purge clean help

build:
	@:

validate:
	@if [ -f "$(VALIDATOR)" ]; then \
		$(PYTHON) "$(VALIDATOR)" "$(CURDIR)"; \
	else \
		echo "Skipping validation: $(VALIDATOR) not found"; \
	fi
	$(PYTHON) -m py_compile scripts/analyze_video.py
	$(PYTHON) -m unittest discover -s tests -p 'test_*.py'
	chmod +x scripts/analyze-video
	chmod 644 scripts/analyze_video.py
	scripts/analyze-video --help >/dev/null

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(TOOL_ROOT)/runs"
	@if [ ! -x "$(VENV_PYTHON)" ]; then \
		$(PYTHON) -m venv "$(VENV_DIR)"; \
	fi
	"$(VENV_PYTHON)" -m pip install -U pip
	"$(VENV_PYTHON)" -m pip uninstall -y boto3 botocore s3transfer >/dev/null 2>&1 || true
	"$(VENV_PYTHON)" -m pip install -U "$(YT_DLP_PACKAGE)"
	@test -x "$(VENV_YT_DLP)"
	cp config.example.json "$(CONFIG_EXAMPLE)"
	@if [ -f "$(CONFIG_FILE)" ]; then \
		scripts/analyze-video --config "$(CONFIG_FILE)" --migrate-config >/dev/null; \
		echo "Migrated existing $(CONFIG_FILE) to the local-evidence schema"; \
	else \
		cp config.example.json "$(CONFIG_FILE)"; \
		chmod 600 "$(CONFIG_FILE)"; \
		echo "Created $(CONFIG_FILE)"; \
	fi
	@if ! command -v "$(FFMPEG)" >/dev/null 2>&1; then \
		echo "ffmpeg is required. Install ffmpeg or run make FFMPEG=/path/to/ffmpeg install-runtime"; \
		exit 1; \
	fi
	@if ! command -v "$(FFPROBE)" >/dev/null 2>&1; then \
		echo "ffprobe is required. Install ffmpeg or run make FFPROBE=/path/to/ffprobe install-runtime"; \
		exit 1; \
	fi
	@if command -v deno >/dev/null 2>&1; then \
		echo "Detected yt-dlp JavaScript runtime: deno"; \
	elif command -v node >/dev/null 2>&1; then \
		echo "Detected yt-dlp JavaScript runtime: node"; \
	else \
		echo "No Deno or Node runtime detected; some JavaScript-protected sites may fail"; \
	fi
	@echo "Installed yt-dlp: $$('$(VENV_YT_DLP)' --version)"
	@echo "Installed $(SKILL_NAME) runtime to $(TOOL_ROOT)"

install-codex: validate install-runtime
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'README.md' \
		--exclude 'tests/' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CODEX_INSTALL_DIR)/"
	@chmod 644 "$(CODEX_INSTALL_DIR)/scripts/analyze_video.py"
	@chmod +x "$(CODEX_INSTALL_DIR)/scripts/analyze-video"
	@echo "Installed $(SKILL_NAME) to $(CODEX_INSTALL_DIR)"

install-claude: validate install-runtime
	@mkdir -p "$(CLAUDE_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'README.md' \
		--exclude 'tests/' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CLAUDE_INSTALL_DIR)/"
	@chmod 644 "$(CLAUDE_INSTALL_DIR)/scripts/analyze_video.py"
	@chmod +x "$(CLAUDE_INSTALL_DIR)/scripts/analyze-video"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude uninstall-runtime

uninstall-runtime:
	rm -rf "$(TOOL_ROOT)/runs" "$(CONFIG_EXAMPLE)" "$(VENV_DIR)" "$(LEGACY_TOOL_ROOT)/frames" "$(LEGACY_TOOL_ROOT)/config.example.json"
	@rmdir "$(TOOL_ROOT)" 2>/dev/null || true
	@rmdir "$(LEGACY_TOOL_ROOT)" 2>/dev/null || true
	@rmdir "$(LEGACY_TOOL_PARENT)" 2>/dev/null || true
	@echo "Uninstalled $(SKILL_NAME) runtime from $(TOOL_ROOT); preserved config.json if present"

purge: uninstall
	rm -f "$(CONFIG_FILE)" "$(LEGACY_TOOL_ROOT)/config.json"
	rm -rf "$(TOOL_ROOT)" "$(LEGACY_TOOL_ROOT)"
	@rmdir "$(LEGACY_TOOL_PARENT)" 2>/dev/null || true
	@echo "Purged $(SKILL_NAME) runtime config from $(CONFIG_FILE)"

uninstall-codex:
	rm -rf "$(CODEX_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CODEX_INSTALL_DIR)"

uninstall-claude:
	rm -rf "$(CLAUDE_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CLAUDE_INSTALL_DIR)"

clean:
	rm -rf scripts/__pycache__ tests/__pycache__

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                   Build the skill package (no-op)." \
		"  make validate          Validate metadata, compile code, and run tests." \
		"  make install           Install yt-dlp runtime plus Codex and Claude Code copies." \
		"  make install-runtime   Install yt-dlp and create/migrate runtime config." \
		"  make install-codex     Install to $(CODEX_INSTALL_DIR)." \
		"  make install-claude    Install to $(CLAUDE_INSTALL_DIR)." \
		"  make uninstall         Remove installed copies and runtime; preserve config.json." \
		"  make purge             Run uninstall and remove config.json." \
		"" \
		"Variables:" \
		"  CODEX_HOME=$(CODEX_HOME)" \
		"  CLAUDE_HOME=$(CLAUDE_HOME)" \
		"  TOOL_ROOT=$(TOOL_ROOT)" \
		"  YT_DLP_PACKAGE=$(YT_DLP_PACKAGE)" \
		"  FFMPEG=$(FFMPEG)" \
		"  FFPROBE=$(FFPROBE)"
