SHELL := /bin/bash

.PHONY: verify test format run gallery gallery-prepare gallery-clean

verify:
	mvn -B -ntp verify

test:
	mvn -B -ntp test

format:
	mvn -B -ntp spotless:apply

run:
	mvn -B -ntp hpi:run

gallery:
	$(MAKE) -C tools/ui-lab capture

gallery-prepare:
	$(MAKE) -C tools/ui-lab prepare

gallery-clean:
	$(MAKE) -C tools/ui-lab clean
