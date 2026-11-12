# Build wrapper for the Shared Data Sources IntelliJ plugin.
# All final artifacts are collected in ./out/

OUT_DIR := out
DIST_DIR := build/distributions

ifeq ($(OS),Windows_NT)
GRADLE := cmd //c gradlew.bat
else
GRADLE := ./gradlew
endif

.PHONY: all build plugin package run test clean distclean

all: plugin

## Build the plugin ZIP and copy it to ./out/
build: plugin

plugin:
	$(GRADLE) buildPlugin
	@mkdir -p $(OUT_DIR)
	@cp $(DIST_DIR)/*.zip $(OUT_DIR)/
	@echo "Artifacts copied to ./$(OUT_DIR)/:"
	@ls $(OUT_DIR)/*.zip

package: plugin

## Launch a sandbox IDE with the plugin installed
run:
	$(GRADLE) runIde

test:
	$(GRADLE) test

clean:
	$(GRADLE) clean
	rm -rf $(OUT_DIR)

distclean: clean
	rm -rf .gradle .intellijPlatform
