SHELL := /bin/bash

.PHONY: prepare capture clean status

prepare:
	uv run --script scripts/manage-lab.py prepare

capture:
	uv run --script scripts/capture-gallery.py

clean:
	uv run --script scripts/manage-lab.py clean

status:
	uv run --script scripts/manage-lab.py status
