SHELL := /bin/bash

MVN ?= mvn
PYTHON ?= python3
RSYNC ?= rsync

CODEX_HOME ?= $(HOME)/.codex
CLAUDE_HOME ?= $(HOME)/.claude
SKILL_NAME := code-refactor
SKILL_SRC := skill/$(SKILL_NAME)
CODEX_SKILL_DEST := $(CODEX_HOME)/skills/$(SKILL_NAME)
CLAUDE_SKILL_DEST := $(CLAUDE_HOME)/skills/$(SKILL_NAME)
TARGET_JAR := target/code-refactor-tools-0.1.0-SNAPSHOT.jar
SKILL_JAR := $(SKILL_SRC)/assets/code-refactor-tools.jar
SKILL_VALIDATOR := $(CODEX_HOME)/skills/.system/skill-creator/scripts/quick_validate.py

.DEFAULT_GOAL := build

.PHONY: build all compile package references bundle validate install install-all install-codex install-claude uninstall uninstall-codex uninstall-claude clean help

build: compile

all: build

compile: references
	$(MVN) -q package

package: compile

references:
	$(PYTHON) scripts/generate-smell-refactoring-reference.py

bundle: compile
	mkdir -p "$(dir $(SKILL_JAR))"
	cp "$(TARGET_JAR)" "$(SKILL_JAR)"

validate: bundle
	@if [ -f "$(SKILL_VALIDATOR)" ]; then \
		$(PYTHON) "$(SKILL_VALIDATOR)" "$(SKILL_SRC)"; \
	else \
		echo "Skipping validation: $(SKILL_VALIDATOR) not found"; \
	fi

install: install-all

install-all: install-codex install-claude

install-codex: validate
	mkdir -p "$(dir $(CODEX_SKILL_DEST))"
	$(RSYNC) -a --delete --exclude='.DS_Store' "$(SKILL_SRC)/" "$(CODEX_SKILL_DEST)/"
	chmod +x "$(CODEX_SKILL_DEST)"/scripts/*
	@echo "Installed $(SKILL_NAME) to $(CODEX_SKILL_DEST)"

install-claude: validate
	mkdir -p "$(dir $(CLAUDE_SKILL_DEST))"
	$(RSYNC) -a --delete --exclude='.DS_Store' "$(SKILL_SRC)/" "$(CLAUDE_SKILL_DEST)/"
	chmod +x "$(CLAUDE_SKILL_DEST)"/scripts/*
	@echo "Installed $(SKILL_NAME) to $(CLAUDE_SKILL_DEST)"

uninstall: uninstall-codex uninstall-claude

uninstall-codex:
	rm -rf "$(CODEX_SKILL_DEST)"
	@echo "Uninstalled $(SKILL_NAME) from $(CODEX_SKILL_DEST)"

uninstall-claude:
	rm -rf "$(CLAUDE_SKILL_DEST)"
	@echo "Uninstalled $(SKILL_NAME) from $(CLAUDE_SKILL_DEST)"

clean:
	$(MVN) -q clean

help:
	@printf '%s\n' \
		"Targets:" \
		"  make             Build the Maven package." \
		"  make build       Build the Maven package." \
		"  make compile     Build the Maven package." \
			"  make references  Regenerate generated skill reference files." \
			"  make bundle      Build and copy the jar into $(SKILL_SRC)/assets." \
			"  make validate    Build, bundle, and validate the skill." \
			"  make install     Install the bundled skill for Codex and Claude Code." \
			"  make install-codex     Install the skill to $(CODEX_SKILL_DEST)." \
			"  make install-claude    Install the skill to $(CLAUDE_SKILL_DEST)." \
			"  make uninstall         Remove both installed copies." \
			"  make uninstall-codex   Remove $(CODEX_SKILL_DEST)." \
			"  make uninstall-claude  Remove $(CLAUDE_SKILL_DEST)." \
			"  make clean       Remove Maven build output." \
			"" \
			"Variables:" \
			"  CODEX_HOME=$(CODEX_HOME)" \
			"  CLAUDE_HOME=$(CLAUDE_HOME)"
