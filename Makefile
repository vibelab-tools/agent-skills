SHELL := /bin/bash

SKILLS := markitdown git-commit code-refactor video-understanding relay
LEGACY_RUNTIME_ROOT ?= $(HOME)/.vibe-coding-skill

.DEFAULT_GOAL := build

.PHONY: build install install-codex install-claude uninstall uninstall-codex uninstall-claude purge validate clean help $(SKILLS)

build:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: build"; \
		$(MAKE) -C "$$skill" build; \
	done

install:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: install"; \
		$(MAKE) -C "$$skill" install; \
	done

install-codex:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: install-codex"; \
		$(MAKE) -C "$$skill" install-codex; \
	done

install-claude:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: install-claude"; \
		$(MAKE) -C "$$skill" install-claude; \
	done

uninstall:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: uninstall"; \
		$(MAKE) -C "$$skill" uninstall; \
	done
	@rmdir "$(LEGACY_RUNTIME_ROOT)" 2>/dev/null || true

purge:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: purge"; \
		$(MAKE) -C "$$skill" purge; \
	done
	@rmdir "$(LEGACY_RUNTIME_ROOT)" 2>/dev/null || true

uninstall-codex:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: uninstall-codex"; \
		$(MAKE) -C "$$skill" uninstall-codex; \
	done

uninstall-claude:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: uninstall-claude"; \
		$(MAKE) -C "$$skill" uninstall-claude; \
	done

validate:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: validate"; \
		$(MAKE) -C "$$skill" validate; \
	done

clean:
	@for skill in $(SKILLS); do \
		echo "==> $$skill: clean"; \
		$(MAKE) -C "$$skill" clean; \
	done

help:
	@printf '%s\n' \
		"Targets:" \
		"  make                  Build every skill project." \
		"  make install          Install every skill for Codex and Claude Code." \
		"  make install-codex    Install every skill for Codex only." \
		"  make install-claude   Install every skill for Claude Code only." \
		"  make uninstall        Remove installed skill/plugin surfaces; preserve runtime configs." \
		"  make purge            Remove installed skill/plugin surfaces and runtime configs." \
		"  make uninstall-codex  Remove Codex surfaces only." \
		"  make uninstall-claude Remove Claude Code surfaces only." \
		"  make validate         Validate skill/plugin structures." \
		"  make clean            Remove build outputs where supported."
