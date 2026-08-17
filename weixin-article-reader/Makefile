SHELL := /bin/sh

SKILL_NAME := weixin-article-reader
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
VENV_DIR ?= $(TOOL_ROOT)/venv
BIN_DIR := $(TOOL_ROOT)/bin
BUILD_VENV ?= $(CURDIR)/.build/venv
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
RSYNC ?= rsync
CODEX ?= codex
MCP_SERVER_NAME := weixin-article-reader
LIVE_URLS ?=

ifeq ($(OS),Windows_NT)
VENV_PYTHON := $(VENV_DIR)/Scripts/python.exe
VENV_ARTICLE := $(VENV_DIR)/Scripts/weixin-article.exe
VENV_MCP := $(VENV_DIR)/Scripts/weixin-article-mcp.exe
BUILD_PYTHON := $(BUILD_VENV)/Scripts/python.exe
BUILD_ARTICLE := $(BUILD_VENV)/Scripts/weixin-article.exe
else
VENV_PYTHON := $(VENV_DIR)/bin/python
VENV_ARTICLE := $(VENV_DIR)/bin/weixin-article
VENV_MCP := $(VENV_DIR)/bin/weixin-article-mcp
BUILD_PYTHON := $(BUILD_VENV)/bin/python
BUILD_ARTICLE := $(BUILD_VENV)/bin/weixin-article
endif

.DEFAULT_GOAL := build
.NOTPARALLEL:

.PHONY: build install install-all install-runtime install-codex install-claude validate live-smoke uninstall uninstall-runtime uninstall-codex uninstall-claude purge clean help

build:
	@:

validate:
	@if [ -f "$(VALIDATOR)" ]; then \
		$(PYTHON) "$(VALIDATOR)" "$(CURDIR)"; \
	else \
		echo "Skipping validation: $(VALIDATOR) not found"; \
	fi
	@if [ ! -x "$(BUILD_PYTHON)" ]; then $(PYTHON) -m venv "$(BUILD_VENV)"; fi
	"$(BUILD_PYTHON)" -m pip install --disable-pip-version-check -q -e "$(CURDIR)"
	"$(BUILD_PYTHON)" -m compileall -q src
	PYTHONPATH=src "$(BUILD_PYTHON)" -m unittest discover -s tests -p 'test_*.py'
	chmod +x scripts/weixin-article scripts/weixin-article-mcp
	"$(BUILD_ARTICLE)" --help >/dev/null

live-smoke: validate
	@if [ -z "$(strip $(LIVE_URLS))" ]; then \
		echo "Set LIVE_URLS to one or more public mp.weixin.qq.com article URLs"; \
		exit 1; \
	fi
	@output_root=$$(mktemp -d /tmp/weixin-article-smoke.XXXXXX); \
		"$(BUILD_ARTICLE)" -o "$$output_root" $(LIVE_URLS); \
		find "$$output_root" -name article.md -o -name manifest.json | sort

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(BIN_DIR)"
	@if [ ! -x "$(VENV_PYTHON)" ]; then $(PYTHON) -m venv "$(VENV_DIR)"; fi
	"$(VENV_PYTHON)" -m pip install --disable-pip-version-check -U pip
	"$(VENV_PYTHON)" -m pip install --disable-pip-version-check -U "$(CURDIR)"
	@ln -sf "$(VENV_ARTICLE)" "$(BIN_DIR)/weixin-article"
	@ln -sf "$(VENV_MCP)" "$(BIN_DIR)/weixin-article-mcp"
	@"$(BIN_DIR)/weixin-article" --help >/dev/null
	@echo "Installed $(SKILL_NAME) runtime to $(TOOL_ROOT)"

install-codex: validate install-runtime
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude '.build/' \
		--exclude 'Makefile' \
		--exclude 'pyproject.toml' \
		--exclude 'src/' \
		--exclude 'tests/' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CODEX_INSTALL_DIR)/"
	@chmod +x "$(CODEX_INSTALL_DIR)/scripts/weixin-article" "$(CODEX_INSTALL_DIR)/scripts/weixin-article-mcp"
	@if ! command -v "$(CODEX)" >/dev/null 2>&1; then \
		echo "Codex CLI not found: cannot register $(MCP_SERVER_NAME) MCP server" >&2; \
		exit 1; \
	fi
	CODEX_HOME="$(CODEX_HOME)" "$(CODEX)" mcp add "$(MCP_SERVER_NAME)" -- "$(BIN_DIR)/weixin-article-mcp"
	@echo "Installed $(SKILL_NAME) to $(CODEX_INSTALL_DIR)"

install-claude: validate install-runtime
	@mkdir -p "$(CLAUDE_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude '.build/' \
		--exclude 'Makefile' \
		--exclude 'pyproject.toml' \
		--exclude 'src/' \
		--exclude 'tests/' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CLAUDE_INSTALL_DIR)/"
	@chmod +x "$(CLAUDE_INSTALL_DIR)/scripts/weixin-article" "$(CLAUDE_INSTALL_DIR)/scripts/weixin-article-mcp"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude uninstall-runtime

purge: uninstall

uninstall-runtime:
	rm -rf "$(TOOL_ROOT)"
	@echo "Uninstalled $(SKILL_NAME) runtime from $(TOOL_ROOT)"

uninstall-codex:
	@if command -v "$(CODEX)" >/dev/null 2>&1 && \
		CODEX_HOME="$(CODEX_HOME)" "$(CODEX)" mcp get "$(MCP_SERVER_NAME)" >/dev/null 2>&1; then \
		CODEX_HOME="$(CODEX_HOME)" "$(CODEX)" mcp remove "$(MCP_SERVER_NAME)"; \
	fi
	rm -rf "$(CODEX_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CODEX_INSTALL_DIR)"

uninstall-claude:
	rm -rf "$(CLAUDE_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CLAUDE_INSTALL_DIR)"

clean:
	rm -rf .build src/vibelab_weixin_article_reader.egg-info src/weixin_article_reader/__pycache__ tests/__pycache__

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                   Build the skill package (no-op)." \
		"  make validate          Validate the skill, install an isolated test environment, and run tests." \
		"  make live-smoke        Read LIVE_URLS with the tested CLI." \
		"  make install           Install the runtime and skill for Codex and Claude Code." \
		"  make install-runtime   Install the CLI and stdio MCP server into $(TOOL_ROOT)." \
		"  make install-codex     Install the Codex skill/runtime and register its MCP server." \
		"  make install-claude    Install the Claude Code skill and managed runtime." \
		"  make uninstall         Remove installed skills, Codex MCP registration, and runtime." \
		"  make clean             Remove local validation artifacts."
