SHELL := /bin/bash

SERVICE_ROOT ?= $(HOME)/.vibe-coding-skill/relay
RUNTIME_DIR ?= $(SERVICE_ROOT)/runtime
DAEMON_INSTALL_DIR := $(SERVICE_ROOT)/daemon
BIN_DIR := $(SERVICE_ROOT)/bin
CODEX_MARKETPLACE_ROOT ?= $(SERVICE_ROOT)/codex-marketplace
CODEX_MARKETPLACE_NAME ?= vibelab-tools
CLAUDE_MARKETPLACE_ROOT ?= $(SERVICE_ROOT)/claude-marketplace
CLAUDE_MARKETPLACE_NAME ?= vibelab-tools
PLUGIN_NAME ?= relay
NODE ?= node

.DEFAULT_GOAL := build

.PHONY: all build install install-all install-codex install-claude install-daemon install-service install-codex-plugin install-codex-hooks install-claude-plugin install-settings daemon start stop status restart uninstall uninstall-codex uninstall-claude uninstall-service validate clean help

all: build

build:
	cd daemon && pnpm install && pnpm run build

install: install-all

install-all:
	$(MAKE) build
	$(MAKE) install-daemon
	$(MAKE) install-codex-plugin
	$(MAKE) install-codex-hooks
	$(MAKE) install-claude-plugin
	$(MAKE) install-settings
	$(MAKE) install-service

install-codex:
	$(MAKE) build
	$(MAKE) install-daemon
	$(MAKE) install-codex-plugin
	$(MAKE) install-codex-hooks
	$(MAKE) install-settings
	$(MAKE) install-service

install-claude:
	$(MAKE) build
	$(MAKE) install-daemon
	$(MAKE) install-claude-plugin
	$(MAKE) install-settings
	$(MAKE) install-service

install-daemon:
	mkdir -p "$(DAEMON_INSTALL_DIR)"
	rsync -a --delete daemon/dist/ "$(DAEMON_INSTALL_DIR)/dist/"
	cp daemon/package.json daemon/pnpm-lock.yaml daemon/pnpm-workspace.yaml "$(DAEMON_INSTALL_DIR)/"
	cd "$(DAEMON_INSTALL_DIR)" && pnpm install --prod --frozen-lockfile
	mkdir -p "$(BIN_DIR)" "$(RUNTIME_DIR)"
	cp scripts/relay-daemon.mjs scripts/relay-service.mjs "$(BIN_DIR)/"
	cp scripts/codex-tmux.sh "$(BIN_DIR)/codex-tmux"
	chmod +x "$(BIN_DIR)/relay-daemon.mjs" "$(BIN_DIR)/relay-service.mjs" "$(BIN_DIR)/codex-tmux"

install-codex-plugin:
	mkdir -p "$(CODEX_MARKETPLACE_ROOT)/plugins" "$(CODEX_MARKETPLACE_ROOT)/.agents/plugins"
	rsync -a --delete plugins/relay/ "$(CODEX_MARKETPLACE_ROOT)/plugins/relay/"
	cp codex-marketplace/.agents/plugins/marketplace.json "$(CODEX_MARKETPLACE_ROOT)/.agents/plugins/marketplace.json"
	rm -f "$(CODEX_MARKETPLACE_ROOT)/marketplace.json"

install-codex-hooks:
	@if codex plugin marketplace list 2>/dev/null | awk -v name="$(CODEX_MARKETPLACE_NAME)" 'NR > 1 && $$1 == name {print $$2}' | grep -Fvx "$(CODEX_MARKETPLACE_ROOT)" >/dev/null; then \
		codex plugin remove "$(PLUGIN_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
		codex plugin marketplace remove "$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true; \
	fi
	if ! codex plugin marketplace list 2>/dev/null | awk -v name="$(CODEX_MARKETPLACE_NAME)" 'NR > 1 && $$1 == name {print $$2}' | grep -Fx "$(CODEX_MARKETPLACE_ROOT)" >/dev/null; then \
		codex plugin marketplace add "$(CODEX_MARKETPLACE_ROOT)"; \
	fi
	codex plugin remove "$(PLUGIN_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true
	codex plugin add "$(PLUGIN_NAME)@$(CODEX_MARKETPLACE_NAME)"

install-claude-plugin:
	mkdir -p "$(CLAUDE_MARKETPLACE_ROOT)"
	rsync -a --delete .claude-plugin/ "$(CLAUDE_MARKETPLACE_ROOT)/.claude-plugin/"
	mkdir -p "$(CLAUDE_MARKETPLACE_ROOT)/plugins"
	rsync -a --delete plugins/relay/ "$(CLAUDE_MARKETPLACE_ROOT)/plugins/relay/"
	claude plugin validate "$(CLAUDE_MARKETPLACE_ROOT)"
	@if claude plugin marketplace list --json 2>/dev/null | jq -r --arg name "$(CLAUDE_MARKETPLACE_NAME)" --arg root "$(CLAUDE_MARKETPLACE_ROOT)" '.[] | select(.name == $$name and .path != $$root) | .name' | grep -Fx "$(CLAUDE_MARKETPLACE_NAME)" >/dev/null; then \
		claude plugin uninstall --scope user "$(PLUGIN_NAME)@$(CLAUDE_MARKETPLACE_NAME)" 2>/dev/null || true; \
		claude plugin marketplace remove "$(CLAUDE_MARKETPLACE_NAME)" 2>/dev/null || true; \
	fi
	if ! claude plugin marketplace list --json 2>/dev/null | jq -r --arg name "$(CLAUDE_MARKETPLACE_NAME)" --arg root "$(CLAUDE_MARKETPLACE_ROOT)" '.[] | select(.name == $$name and .path == $$root) | .name' | grep -Fx "$(CLAUDE_MARKETPLACE_NAME)" >/dev/null; then \
		claude plugin marketplace add "$(CLAUDE_MARKETPLACE_ROOT)"; \
	fi
	claude plugin uninstall --scope user "$(PLUGIN_NAME)@$(CLAUDE_MARKETPLACE_NAME)" 2>/dev/null || true
	claude plugin install "$(PLUGIN_NAME)@$(CLAUDE_MARKETPLACE_NAME)"

install-settings:
	mkdir -p "$(HOME)/.codex" "$(RUNTIME_DIR)"
	node scripts/merge-codex-relay-settings.mjs "$(RUNTIME_DIR)"

install-service:
	$(NODE) "$(BIN_DIR)/relay-service.mjs" install "$(SERVICE_ROOT)"

start:
	$(NODE) "$(BIN_DIR)/relay-service.mjs" start "$(SERVICE_ROOT)"

stop:
	$(NODE) "$(BIN_DIR)/relay-service.mjs" stop "$(SERVICE_ROOT)"

status:
	$(NODE) "$(BIN_DIR)/relay-service.mjs" status "$(SERVICE_ROOT)"

restart:
	$(NODE) "$(BIN_DIR)/relay-service.mjs" restart "$(SERVICE_ROOT)"

daemon:
	$(MAKE) install

uninstall:
	$(MAKE) uninstall-service
	$(MAKE) uninstall-codex
	$(MAKE) uninstall-claude
	rm -rf "$(SERVICE_ROOT)"

uninstall-codex:
	codex plugin remove "$(PLUGIN_NAME)@$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true
	codex plugin marketplace remove "$(CODEX_MARKETPLACE_NAME)" 2>/dev/null || true
	rm -rf "$(CODEX_MARKETPLACE_ROOT)"
	node scripts/remove-codex-relay-settings.mjs "$(RUNTIME_DIR)"

uninstall-claude:
	claude plugin uninstall --scope user "$(PLUGIN_NAME)@$(CLAUDE_MARKETPLACE_NAME)" 2>/dev/null || true
	claude plugin marketplace remove "$(CLAUDE_MARKETPLACE_NAME)" 2>/dev/null || true
	rm -rf "$(CLAUDE_MARKETPLACE_ROOT)"

uninstall-service:
	@if [ -x "$(BIN_DIR)/relay-service.mjs" ]; then \
		$(NODE) "$(BIN_DIR)/relay-service.mjs" uninstall "$(SERVICE_ROOT)"; \
	else \
		$(NODE) scripts/relay-service.mjs uninstall "$(SERVICE_ROOT)" || true; \
	fi

validate:
	claude plugin validate .
	claude plugin validate plugins/relay

clean:
	rm -rf daemon/dist worker/dist

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                  Build the daemon." \
		"  make install          Install Codex + Claude plugin surfaces and start service." \
		"  make install-codex    Install Codex surface and start shared service." \
		"  make install-claude   Install Claude surface and start shared service." \
		"  make uninstall        Stop service and remove both plugin surfaces." \
		"  make uninstall-codex  Remove Codex plugin surface only." \
		"  make uninstall-claude Remove Claude plugin surface only." \
		"  make start|stop|status|restart  Control the platform service." \
		"  make validate         Validate plugin manifests." \
		"" \
		"Variables:" \
		"  SERVICE_ROOT=$(SERVICE_ROOT)"
