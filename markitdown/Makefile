SHELL := /bin/sh

SKILL_NAME := markitdown
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
LEGACY_TOOL_PARENT ?= $(HOME)/.vibe-coding-skill
LEGACY_TOOL_ROOT ?= $(HOME)/.vibe-coding-skill/$(SKILL_NAME)
VENV_DIR ?= $(TOOL_ROOT)/venv
BIN_DIR := $(TOOL_ROOT)/bin
MARKITDOWN_PACKAGE ?= markitdown[all]
ASSETS_SCRIPT := scripts/markitdown-assets.py
ASSETS_WRAPPER := scripts/markitdown-assets
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
PYTHON_CANDIDATES ?= python3 python python3.14 python3.13 python3.12 python3.11 python3.10 /opt/homebrew/bin/python3.14 /opt/homebrew/bin/python3.13 /opt/homebrew/bin/python3.12 /opt/homebrew/bin/python3.11 /opt/homebrew/bin/python3 /usr/local/bin/python3.14 /usr/local/bin/python3.13 /usr/local/bin/python3.12 /usr/local/bin/python3.11 /usr/local/bin/python3 /usr/bin/python3
RUNTIME_PYTHON ?= $(shell for py in $(PYTHON_CANDIDATES); do \
	if command -v "$$py" >/dev/null 2>&1; then \
		resolved=$$(command -v "$$py"); \
	elif [ -x "$$py" ]; then \
		resolved="$$py"; \
	else \
		continue; \
	fi; \
	if "$$resolved" -c 'import sys, hashlib; assert sys.version_info >= (3, 10); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		printf '%s\n' "$$resolved"; exit 0; \
	fi; \
done)
RSYNC ?= rsync

ifeq ($(OS),Windows_NT)
VENV_BIN := $(VENV_DIR)/Scripts
VENV_PYTHON := $(VENV_BIN)/python.exe
MARKITDOWN_BIN := $(VENV_BIN)/markitdown.exe
else
VENV_BIN := $(VENV_DIR)/bin
VENV_PYTHON := $(VENV_BIN)/python
MARKITDOWN_BIN := $(VENV_BIN)/markitdown
endif

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
	$(PYTHON) -m py_compile "$(ASSETS_SCRIPT)"
	chmod +x "$(ASSETS_WRAPPER)"
	"$(ASSETS_WRAPPER)" --help >/dev/null

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(BIN_DIR)"
	@if [ -z "$(RUNTIME_PYTHON)" ]; then \
		echo "No suitable Python runtime found. Install Python 3.10+ or run make RUNTIME_PYTHON=/path/to/python install-runtime"; \
		exit 1; \
	fi
	@if ! "$(RUNTIME_PYTHON)" -c 'import sys, hashlib; assert sys.version_info >= (3, 10); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		echo "RUNTIME_PYTHON=$(RUNTIME_PYTHON) is not suitable. It must be Python 3.10+ with working hashlib.blake2b."; \
		exit 1; \
	fi
	@if [ -x "$(VENV_PYTHON)" ] && ! "$(VENV_PYTHON)" -c 'import sys, hashlib; assert sys.version_info >= (3, 10); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		echo "Recreating $(VENV_DIR): existing Python runtime is not suitable"; \
		rm -rf "$(VENV_DIR)"; \
	fi
	@if [ ! -x "$(VENV_PYTHON)" ]; then \
		$(RUNTIME_PYTHON) -m venv "$(VENV_DIR)"; \
	fi
	"$(VENV_PYTHON)" -m pip install -U pip
	"$(VENV_PYTHON)" -m pip install -U "$(MARKITDOWN_PACKAGE)"
	@ln -sf "$(MARKITDOWN_BIN)" "$(BIN_DIR)/markitdown"
	cp "$(ASSETS_SCRIPT)" "$(BIN_DIR)/markitdown-assets.py"
	cp "$(ASSETS_WRAPPER)" "$(BIN_DIR)/markitdown-assets"
	chmod +x "$(BIN_DIR)/markitdown-assets.py" "$(BIN_DIR)/markitdown-assets"
	@"$(MARKITDOWN_BIN)" --help >/dev/null
	@"$(BIN_DIR)/markitdown-assets" --help >/dev/null
	@echo "Installed $(MARKITDOWN_PACKAGE) runtime to $(TOOL_ROOT)"

install-codex: validate install-runtime
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'README.md' \
		--exclude '.DS_Store' \
		./ "$(CODEX_INSTALL_DIR)/"
	@echo "Installed $(SKILL_NAME) to $(CODEX_INSTALL_DIR)"

install-claude: validate install-runtime
	@mkdir -p "$(CLAUDE_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'README.md' \
		--exclude '.DS_Store' \
		./ "$(CLAUDE_INSTALL_DIR)/"
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
		"  make validate          Validate the skill structure." \
		"  make install           Install the runtime and skill for Codex and Claude Code." \
		"  make install-runtime   Install MarkItDown into an isolated runtime venv." \
		"                         Also installs markitdown-assets for local image assets." \
		"  make install-codex     Install the skill to $(CODEX_INSTALL_DIR)." \
		"  make install-claude    Install the skill to $(CLAUDE_INSTALL_DIR)." \
		"  make uninstall         Remove both installed copies and the runtime." \
		"  make uninstall-runtime Remove $(TOOL_ROOT)." \
		"  make uninstall-codex   Remove $(CODEX_INSTALL_DIR)." \
		"  make uninstall-claude  Remove $(CLAUDE_INSTALL_DIR)." \
		"" \
		"Variables:" \
		"  CODEX_HOME=$(CODEX_HOME)" \
		"  CLAUDE_HOME=$(CLAUDE_HOME)" \
		"  TOOL_ROOT=$(TOOL_ROOT)" \
		"  RUNTIME_PYTHON=$(RUNTIME_PYTHON)" \
		"  MARKITDOWN_PACKAGE=$(MARKITDOWN_PACKAGE)"
