SHELL := /bin/sh

SKILL_NAME := humanizer
CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
SERVICE_ROOT ?= $(HOME)/.vibelab-tools/agent-skills/$(SKILL_NAME)
CODEX_INSTALL_DIR := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_INSTALL_DIR := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
CODEX_MARKETPLACE_ROOT ?= $(SERVICE_ROOT)/codex-marketplace
CODEX_MARKETPLACE_NAME ?= vibelab-humanizer
CODEX_PLUGIN_DIR := $(CODEX_MARKETPLACE_ROOT)/plugins/$(SKILL_NAME)
VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py
PLUGIN_VALIDATOR := $(CODEX_HOME)/skills/.system/plugin-creator/scripts/validate_plugin.py
PYTHON ?= python3
RSYNC ?= rsync
CODEX ?= codex

.DEFAULT_GOAL := build

.PHONY: build install install-all install-codex install-codex-skill install-codex-plugin install-claude validate uninstall uninstall-codex uninstall-claude purge clean help

build:
	@:

validate:
	@if [ -f "$(VALIDATOR)" ]; then \
		$(PYTHON) "$(VALIDATOR)" "$(CURDIR)"; \
	else \
		echo "Skipping structure validation: $(VALIDATOR) not found"; \
	fi
	@if [ -f "$(PLUGIN_VALIDATOR)" ]; then \
		$(PYTHON) "$(PLUGIN_VALIDATOR)" "$(CURDIR)"; \
	else \
		echo "Skipping plugin validation: $(PLUGIN_VALIDATOR) not found"; \
	fi
	$(PYTHON) -m unittest discover -s tests -p 'test_*.py'
	cd bench && $(PYTHON) run_benchmark.py verify
	cd bench/forward && $(PYTHON) verify.py

install: install-all

install-all:
	$(MAKE) install-codex
	$(MAKE) install-claude

install-codex:
	$(MAKE) validate
	$(MAKE) install-codex-skill
	$(MAKE) install-codex-plugin

install-codex-skill:
	@mkdir -p "$(CODEX_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.codex-plugin/' \
		--exclude 'codex-marketplace/' \
		--exclude 'hooks/' \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'bench/' \
		--exclude 'tests/' \
		--exclude '__pycache__/' \
		--exclude '*.pyc' \
		--exclude '.DS_Store' \
		./ "$(CODEX_INSTALL_DIR)/"
	@echo "Installed $(SKILL_NAME) to $(CODEX_INSTALL_DIR)"

install-codex-plugin:
	@mkdir -p "$(CODEX_PLUGIN_DIR)" "$(CODEX_MARKETPLACE_ROOT)/.agents/plugins"
	$(RSYNC) -a --delete \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'bench/' \
		--exclude 'codex-marketplace/' \
		--exclude 'tests/' \
		--exclude '__pycache__/' \
		--exclude '*.pyc' \
		--exclude '.DS_Store' \
		./ "$(CODEX_PLUGIN_DIR)/"
	cp codex-marketplace/.agents/plugins/marketplace.json "$(CODEX_MARKETPLACE_ROOT)/.agents/plugins/marketplace.json"
	@if $(CODEX) plugin marketplace list 2>/dev/null | awk -v name="$(CODEX_MARKETPLACE_NAME)" 'NR > 1 && $$1 == name {print $$2}' | grep -Fvx "$(CODEX_MARKETPLACE_ROOT)" >/dev/null; then \
		$(CODEX) plugin remove "$(SKILL_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
		$(CODEX) plugin marketplace remove "$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
	fi
	@if ! $(CODEX) plugin marketplace list 2>/dev/null | awk -v name="$(CODEX_MARKETPLACE_NAME)" 'NR > 1 && $$1 == name {print $$2}' | grep -Fx "$(CODEX_MARKETPLACE_ROOT)" >/dev/null; then \
		$(CODEX) plugin marketplace add "$(CODEX_MARKETPLACE_ROOT)"; \
	fi
	$(CODEX) plugin remove "$(SKILL_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true
	$(CODEX) plugin add "$(SKILL_NAME)@$(CODEX_MARKETPLACE_NAME)"
	@echo "Installed $(SKILL_NAME) plugin from $(CODEX_MARKETPLACE_ROOT)"

install-claude: validate
	@mkdir -p "$(CLAUDE_INSTALL_DIR)"
	$(RSYNC) -a --delete \
		--exclude '.codex-plugin/' \
		--exclude 'codex-marketplace/' \
		--exclude 'hooks/' \
		--exclude '.git/' \
		--exclude 'Makefile' \
		--exclude 'bench/' \
		--exclude 'tests/' \
		--exclude '__pycache__/' \
		--exclude '*.pyc' \
		--exclude '.DS_Store' \
		./ "$(CLAUDE_INSTALL_DIR)/"
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_INSTALL_DIR)"

uninstall: uninstall-codex uninstall-claude

purge: uninstall

uninstall-codex:
	@if $(CODEX) plugin marketplace list 2>/dev/null | awk -v name="$(CODEX_MARKETPLACE_NAME)" 'NR > 1 && $$1 == name {print $$2}' | grep -Fx "$(CODEX_MARKETPLACE_ROOT)" >/dev/null; then \
		$(CODEX) plugin remove "$(SKILL_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
		$(CODEX) plugin marketplace remove "$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
	fi
	rm -rf "$(CODEX_INSTALL_DIR)"
	rm -rf "$(CODEX_MARKETPLACE_ROOT)"
	@echo "Uninstalled $(SKILL_NAME) from $(CODEX_INSTALL_DIR)"

uninstall-claude:
	rm -rf "$(CLAUDE_INSTALL_DIR)"
	@echo "Uninstalled $(SKILL_NAME) from $(CLAUDE_INSTALL_DIR)"

clean:
	rm -rf bench/__pycache__ bench/forward/__pycache__ hooks/__pycache__ scripts/__pycache__ tests/__pycache__

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                   Build the skill package (no-op)." \
		"  make validate          Validate the skill and run focused tests." \
		"  make install           Install the skill for Codex and Claude Code." \
		"  make install-codex     Install the Codex skill and pre-draft hook plugin." \
		"  make install-claude    Install the skill to $(CLAUDE_INSTALL_DIR)." \
		"  make uninstall         Remove both installed copies." \
		"  make purge             Same as uninstall; this skill has no runtime config." \
		"  make uninstall-codex   Remove $(CODEX_INSTALL_DIR)." \
		"  make uninstall-claude  Remove $(CLAUDE_INSTALL_DIR)."
