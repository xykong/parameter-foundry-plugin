SHELL := /bin/bash

.PHONY: verify verify-container test format run e2e source-canary gallery gallery-prepare gallery-clean

verify:
	mvn -B -ntp verify

verify-container:
	docker run --rm \
		-v "$(CURDIR):/workspace" \
		-v "$(HOME)/.m2:/root/.m2" \
		-w /workspace \
		maven:3.9.11-eclipse-temurin-21 \
		mvn -B -ntp clean verify

test:
	mvn -B -ntp verify -Dspotbugs.skip=true -Dspotless.check.skip=true

format:
	mvn -B -ntp spotless:apply

run:
	mvn -B -ntp -pl plugin hpi:run

e2e:
	uv run tools/e2e/run-e2e.py

source-canary:
	python3 tools/source-canary/run-source-canary.py

gallery:
	$(MAKE) -C tools/ui-lab capture

gallery-prepare:
	$(MAKE) -C tools/ui-lab prepare

gallery-clean:
	$(MAKE) -C tools/ui-lab clean
