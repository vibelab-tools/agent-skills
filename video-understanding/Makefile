SHELL := /bin/sh

SKILL_NAME := video-understanding
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
LEGACY_TOOL_PARENT ?= $(HOME)/.vibe-coding-skill
LEGACY_TOOL_ROOT ?= $(HOME)/.vibe-coding-skill/$(SKILL_NAME)
CONFIG_FILE := $(TOOL_ROOT)/config.json
CONFIG_EXAMPLE := $(TOOL_ROOT)/config.example.json
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
RSYNC ?= rsync
FFMPEG ?= ffmpeg

.DEFAULT_GOAL := build
.NOTPARALLEL:

.PHONY: build install install-all install-runtime install-codex install-claude validate uninstall uninstall-runtime uninstall-codex uninstall-claude clean help

build:
	@:

validate:
	@if [ -f "$(VALIDATOR)" ]; then \
		$(PYTHON) "$(VALIDATOR)" "$(CURDIR)"; \
	else \
		echo "Skipping validation: $(VALIDATOR) not found"; \
	fi
	$(PYTHON) -m py_compile scripts/analyze_video.py
	chmod +x scripts/analyze-video
	chmod 644 scripts/analyze_video.py
	scripts/analyze-video --help >/dev/null

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(TOOL_ROOT)/frames"
	cp config.example.json "$(CONFIG_EXAMPLE)"
	@if [ ! -f "$(CONFIG_FILE)" ]; then \
		cp config.example.json "$(CONFIG_FILE)"; \
		echo "Created $(CONFIG_FILE)"; \
	else \
		echo "Preserved existing $(CONFIG_FILE)"; \
	fi
	@if ! command -v "$(FFMPEG)" >/dev/null 2>&1; then \
		echo "ffmpeg is required for video-understanding frame extraction. Install ffmpeg or run make FFMPEG=/path/to/ffmpeg install-runtime"; \
		exit 1; \
	fi
	@echo "Installed $(SKILL_NAME) runtime to $(TOOL_ROOT)"

install-codex: validate install-runtime
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'README.md' \
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
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CLAUDE_INSTALL_DIR)/"
	@chmod 644 "$(CLAUDE_INSTALL_DIR)/scripts/analyze_video.py"
	@chmod +x "$(CLAUDE_INSTALL_DIR)/scripts/analyze-video"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude uninstall-runtime

uninstall-runtime:
	rm -rf "$(TOOL_ROOT)" "$(LEGACY_TOOL_ROOT)"
	@rmdir "$(LEGACY_TOOL_PARENT)" 2>/dev/null || true
	@echo "Uninstalled $(SKILL_NAME) runtime from $(TOOL_ROOT)"
	@echo "Removed legacy runtime at $(LEGACY_TOOL_ROOT) if present"

uninstall-codex:
	rm -rf "$(CODEX_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CODEX_INSTALL_DIR)"

uninstall-claude:
	rm -rf "$(CLAUDE_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CLAUDE_INSTALL_DIR)"

clean:
	rm -rf scripts/__pycache__

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                   Build the skill package (no-op)." \
		"  make validate          Validate the skill and compile the script." \
		"  make install           Install runtime plus Codex and Claude Code skill copies." \
		"  make install-runtime   Create runtime config directory and check ffmpeg." \
		"  make install-codex     Install to $(CODEX_INSTALL_DIR)." \
		"  make install-claude    Install to $(CLAUDE_INSTALL_DIR)." \
		"  make uninstall         Remove installed copies and runtime." \
		"  make uninstall-runtime Remove $(TOOL_ROOT)." \
		"  make uninstall-codex   Remove $(CODEX_INSTALL_DIR)." \
		"  make uninstall-claude  Remove $(CLAUDE_INSTALL_DIR)." \
		"" \
		"Variables:" \
		"  CODEX_HOME=$(CODEX_HOME)" \
		"  CLAUDE_HOME=$(CLAUDE_HOME)" \
		"  TOOL_ROOT=$(TOOL_ROOT)" \
		"  FFMPEG=$(FFMPEG)"
