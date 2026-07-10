SHELL := /bin/sh

SKILL_NAME := osint-person-investigation
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
TOOL_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
LEGACY_TOOL_PARENT ?= $(HOME)/.vibe-coding-skill
LEGACY_TOOL_ROOT ?= $(HOME)/.vibe-coding-skill/$(SKILL_NAME)
VENV_DIR ?= $(TOOL_ROOT)/venv
BIN_DIR := $(TOOL_ROOT)/bin
VENDOR_DIR := $(TOOL_ROOT)/vendor
STATE_DIR := $(TOOL_ROOT)/state
CONFIG_FILE := $(TOOL_ROOT)/config.json
CONFIG_EXAMPLE := $(TOOL_ROOT)/config.example.json
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PYTHON ?= python3
PYTHON_CANDIDATES ?= python3.12 python3 /opt/homebrew/bin/python3.12 /usr/local/bin/python3.12 /usr/local/bin/python3
DETECTED_RUNTIME_PYTHON := $(shell for py in $(PYTHON_CANDIDATES); do \
	if command -v "$$py" >/dev/null 2>&1; then \
		resolved=$$(command -v "$$py"); \
	elif [ -x "$$py" ]; then \
		resolved="$$py"; \
	else \
		continue; \
	fi; \
	if "$$resolved" -c 'import sys, hashlib; assert sys.version_info[:2] == (3, 12); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		printf '%s\n' "$$resolved"; exit 0; \
	fi; \
done)
OSINT_RUNTIME_PYTHON ?= $(DETECTED_RUNTIME_PYTHON)
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
	chmod +x scripts/osint-search
	chmod +x scripts/install-osint-tools.py
	chmod +x scripts/merge-config-defaults.py
	scripts/osint-search --help >/dev/null
	scripts/install-osint-tools.py --help >/dev/null
	scripts/merge-config-defaults.py --help >/dev/null

install: install-all

install-all: install-runtime install-codex install-claude

install-runtime:
	@mkdir -p "$(TOOL_ROOT)" "$(BIN_DIR)"
	@if [ -z "$(OSINT_RUNTIME_PYTHON)" ]; then \
		echo "No suitable Python runtime found. Install Python 3.12.x or run make OSINT_RUNTIME_PYTHON=/path/to/python3.12 install-runtime"; \
		exit 1; \
	fi
	@if ! "$(OSINT_RUNTIME_PYTHON)" -c 'import sys, hashlib; assert sys.version_info[:2] == (3, 12); hashlib.blake2b(b"x")' >/dev/null 2>&1; then \
		echo "OSINT_RUNTIME_PYTHON=$(OSINT_RUNTIME_PYTHON) is not suitable. It must be Python 3.12.x with working hashlib.blake2b."; \
		exit 1; \
	fi
	@if [ -x "$(VENV_PYTHON)" ] && ! "$(VENV_PYTHON)" -c 'import sys; assert sys.version_info[:2] == (3, 12)' >/dev/null 2>&1; then \
		echo "Recreating $(VENV_DIR): existing Python runtime is not Python 3.12.x"; \
		rm -rf "$(VENV_DIR)"; \
	fi
	@if ! command -v git >/dev/null 2>&1; then \
		echo "git is required to install managed OSINT tools"; \
		exit 1; \
	fi
	cp config.example.json "$(CONFIG_EXAMPLE)"
	@if [ ! -f "$(CONFIG_FILE)" ]; then \
		cp config.example.json "$(CONFIG_FILE)"; \
		chmod 600 "$(CONFIG_FILE)"; \
		echo "Created $(CONFIG_FILE)"; \
	else \
		echo "Preserved existing $(CONFIG_FILE)"; \
		$(PYTHON) scripts/merge-config-defaults.py "$(CONFIG_FILE)" config.example.json; \
	fi
	cp scripts/osint-search.py "$(BIN_DIR)/osint-search.py"
	cp scripts/osint-search "$(BIN_DIR)/osint-search"
	rm -rf "$(BIN_DIR)/providers"
	cp -R scripts/providers "$(BIN_DIR)/providers"
	$(OSINT_RUNTIME_PYTHON) scripts/install-osint-tools.py \
		--tool-root "$(TOOL_ROOT)" \
		--venv "$(VENV_DIR)" \
		--bin-dir "$(BIN_DIR)" \
		--vendor-dir "$(VENDOR_DIR)" \
		--python "$(OSINT_RUNTIME_PYTHON)"
	chmod 644 "$(BIN_DIR)/osint-search.py"
	chmod +x "$(BIN_DIR)/osint-search" "$(BIN_DIR)/sherlock" "$(BIN_DIR)/maigret" "$(BIN_DIR)/theHarvester" "$(BIN_DIR)/photon"
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
	@chmod 644 "$(CODEX_INSTALL_DIR)/scripts/osint-search.py"
	@chmod +x "$(CODEX_INSTALL_DIR)/scripts/osint-search"
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
	@chmod 644 "$(CLAUDE_INSTALL_DIR)/scripts/osint-search.py"
	@chmod +x "$(CLAUDE_INSTALL_DIR)/scripts/osint-search"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude uninstall-runtime

uninstall-runtime:
	rm -rf "$(BIN_DIR)" "$(CONFIG_EXAMPLE)" "$(VENV_DIR)" "$(VENDOR_DIR)" "$(STATE_DIR)" "$(LEGACY_TOOL_ROOT)/bin" "$(LEGACY_TOOL_ROOT)/config.example.json"
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
		"  make validate          Validate the skill structure and search wrapper." \
		"  make install           Install runtime plus Codex and Claude Code skill copies." \
		"  make install-runtime   Create runtime config and install osint-search wrapper." \
		"                         Also installs managed OSINT tools into an isolated venv." \
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
		"  OSINT_RUNTIME_PYTHON=$(OSINT_RUNTIME_PYTHON)"
