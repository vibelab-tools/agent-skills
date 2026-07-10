SHELL := /bin/sh

SKILL_NAME := markdown-image-hosting
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
LEGACY_TOOL_PARENT ?= $(HOME)/.vibe-coding-skill
LEGACY_TOOL_ROOT ?= $(HOME)/.vibe-coding-skill/$(SKILL_NAME)
VENV_DIR ?= $(TOOL_ROOT)/venv
BIN_DIR := $(TOOL_ROOT)/bin
CONFIG_FILE := $(TOOL_ROOT)/config.json
CONFIG_EXAMPLE := $(TOOL_ROOT)/config.example.json
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
PYTHON_CANDIDATES ?= python3 python python3.14 python3.13 python3.12 python3.11 python3.10 /opt/homebrew/bin/python3.14 /opt/homebrew/bin/python3.13 /opt/homebrew/bin/python3.12 /opt/homebrew/bin/python3.11 /opt/homebrew/bin/python3 /usr/local/bin/python3.14 /usr/local/bin/python3.13 /usr/local/bin/python3.12 /usr/local/bin/python3.11 /usr/local/bin/python3 /usr/bin/python3
DETECTED_RUNTIME_PYTHON := $(shell for py in $(PYTHON_CANDIDATES); do \
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
MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON ?= $(DETECTED_RUNTIME_PYTHON)
BOTO3_PACKAGE ?= boto3
RSYNC ?= rsync

ifeq ($(OS),Windows_NT)
VENV_PYTHON := $(VENV_DIR)/Scripts/python.exe
else
VENV_PYTHON := $(VENV_DIR)/bin/python
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
	find scripts -name '*.py' -exec $(PYTHON) -m py_compile {} +
	chmod +x scripts/upload-doc-images
	PYTHON=$(PYTHON) scripts/upload-doc-images --help >/dev/null

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(BIN_DIR)"
	@if [ -z "$(MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON)" ]; then \
		echo "No suitable Python runtime found. Install Python 3.10+ or run make MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON=/path/to/python install-runtime"; \
		exit 1; \
	fi
	@if ! "$(MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON)" -c 'import sys, hashlib; assert sys.version_info >= (3, 10); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		echo "MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON=$(MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON) is not suitable. It must be Python 3.10+ with working hashlib.blake2b."; \
		exit 1; \
	fi
	@if [ -x "$(VENV_PYTHON)" ] && ! "$(VENV_PYTHON)" -c 'import sys, hashlib; assert sys.version_info >= (3, 10); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		echo "Recreating $(VENV_DIR): existing Python runtime is not suitable"; \
		rm -rf "$(VENV_DIR)"; \
	fi
	@if [ ! -x "$(VENV_PYTHON)" ]; then \
		$(MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON) -m venv "$(VENV_DIR)"; \
	fi
	"$(VENV_PYTHON)" -m pip install -U pip
	"$(VENV_PYTHON)" -m pip install -U "$(BOTO3_PACKAGE)"
	cp config.example.json "$(CONFIG_EXAMPLE)"
	@if [ ! -f "$(CONFIG_FILE)" ]; then \
		cp config.example.json "$(CONFIG_FILE)"; \
		chmod 600 "$(CONFIG_FILE)"; \
		echo "Created $(CONFIG_FILE)"; \
	else \
		echo "Preserved existing $(CONFIG_FILE)"; \
	fi
	cp scripts/upload_doc_images.py "$(BIN_DIR)/upload_doc_images.py"
	cp scripts/upload-doc-images "$(BIN_DIR)/upload-doc-images"
	rm -rf "$(BIN_DIR)/image_hosts"
	cp -R scripts/image_hosts "$(BIN_DIR)/image_hosts"
	chmod 644 "$(BIN_DIR)/upload_doc_images.py"
	chmod +x "$(BIN_DIR)/upload-doc-images"
	@"$(BIN_DIR)/upload-doc-images" --help >/dev/null
	@echo "Installed $(SKILL_NAME) runtime to $(TOOL_ROOT)"

install-codex: validate install-runtime
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CODEX_INSTALL_DIR)/"
	@chmod +x "$(CODEX_INSTALL_DIR)/scripts/upload-doc-images"
	@echo "Installed $(SKILL_NAME) to $(CODEX_INSTALL_DIR)"

install-claude: validate install-runtime
	@mkdir -p "$(CLAUDE_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude '.DS_Store' \
		--exclude '__pycache__/' \
		./ "$(CLAUDE_INSTALL_DIR)/"
	@chmod +x "$(CLAUDE_INSTALL_DIR)/scripts/upload-doc-images"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude uninstall-runtime

uninstall-runtime:
	rm -rf "$(BIN_DIR)" "$(CONFIG_EXAMPLE)" "$(VENV_DIR)" "$(LEGACY_TOOL_ROOT)/bin" "$(LEGACY_TOOL_ROOT)/config.example.json"
	@rmdir "$(TOOL_ROOT)" 2>/dev/null || true
	@rmdir "$(LEGACY_TOOL_ROOT)" 2>/dev/null || true
	@rmdir "$(LEGACY_TOOL_PARENT)" 2>/dev/null || true
	@echo "Uninstalled $(SKILL_NAME) runtime from $(TOOL_ROOT); preserved config.json if present"
	@echo "Removed legacy runtime at $(LEGACY_TOOL_ROOT) if present"

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
	find scripts -type d -name '__pycache__' -prune -exec rm -rf {} +

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                   Build the skill package (no-op)." \
		"  make validate          Validate the skill structure and uploader." \
		"  make install           Install runtime plus Codex and Claude Code skill copies." \
		"  make install-runtime   Create runtime config and install boto3 into a venv." \
		"  make install-codex     Install the skill to $(CODEX_INSTALL_DIR)." \
		"  make install-claude    Install the skill to $(CLAUDE_INSTALL_DIR)." \
		"  make uninstall         Remove installed copies and runtime; preserve config.json." \
		"  make purge             Run uninstall and remove config.json." \
		"  make uninstall-runtime Remove runtime files under $(TOOL_ROOT); preserve config.json." \
		"  make uninstall-codex   Remove $(CODEX_INSTALL_DIR)." \
		"  make uninstall-claude  Remove $(CLAUDE_INSTALL_DIR)." \
		"" \
		"Variables:" \
		"  CODEX_HOME=$(CODEX_HOME)" \
		"  CLAUDE_HOME=$(CLAUDE_HOME)" \
		"  TOOL_ROOT=$(TOOL_ROOT)" \
		"  MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON=$(MARKDOWN_IMAGE_HOSTING_RUNTIME_PYTHON)" \
		"  BOTO3_PACKAGE=$(BOTO3_PACKAGE)"
