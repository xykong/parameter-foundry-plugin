(function () {
  "use strict";

  var MAX_RENDERED_OPTIONS = 160;
  var REACTIVE_DEBOUNCE_MS = 250;
  var nextId = 0;
  var refreshStates = new Map();

  if (!window.fetch || !window.URL || !window.Set || !window.Map) {
    return;
  }

  function uniqueId(prefix) {
    nextId += 1;
    return prefix + "-" + nextId;
  }

  function endpointFor(field) {
    var form = field.closest("form");
    if (!form) {
      return null;
    }
    var action = new URL(form.action || window.location.href, window.location.href);
    var jobPath = action.pathname.replace(/\/build\/?$/, "/");
    jobPath = jobPath.replace(/\/\d+\/rebuild\/configSubmit\/?$/, "/");
    if (jobPath === action.pathname) {
      return null;
    }
    action.pathname = jobPath;
    action.search = "";
    return new URL("unified-parameters/api/v1/resolve", action);
  }

  function crumbHeaders() {
    var headers = {"Content-Type": "application/json"};
    var headerName = document.head.dataset.crumbHeader;
    var value = document.head.dataset.crumbValue;
    if (headerName && value) {
      headers[headerName] = value;
    }
    return headers;
  }

  function dependencyNames(field) {
    return (field.dataset.dependsOn || "")
      .split(",")
      .map(function (value) {
        return value.trim();
      })
      .filter(Boolean);
  }

  function selectedValuesForField(field) {
    var generated = Array.prototype.map.call(
      field.querySelectorAll("[data-up-generated-value]"),
      function (input) {
        return input.value;
      },
    );
    if (generated.length) {
      return generated;
    }
    return Array.from(
      new Set(
        Array.prototype.map
          .call(
            field.querySelectorAll(
              "[data-up-fallback-json-value], input[data-up-native-value]:checked, " +
                "select[data-up-native-value] option:checked",
            ),
            function (node) {
              return node.value;
            },
          )
          .filter(Boolean),
      ),
    );
  }

  function dependencyPayload(field) {
    var dependencies = {};
    dependencyNames(field).forEach(function (name) {
      var dependency = Array.prototype.find.call(
        document.querySelectorAll("[data-unified-parameter]"),
        function (candidate) {
          return candidate.dataset.parameterName === name;
        },
      );
      if (!dependency) {
        throw new Error("Dependency field is unavailable: " + name);
      }
      var values = selectedValuesForField(dependency);
      dependencies[name] =
        dependency.dataset.multiple === "true" ? values : values[0] || null;
    });
    return dependencies;
  }

  function updateProviderContext(field, dependencies, snapshotId) {
    var snapshot = field.querySelector("[data-up-snapshot-id]");
    var dependencyJson = field.querySelector("[data-up-dependencies-json]");
    if (snapshot) {
      snapshot.value = snapshotId || "";
    }
    if (dependencyJson) {
      dependencyJson.value = JSON.stringify(dependencies || {});
    }
  }

  function resolveOptions(field, signal, version) {
    var endpoint = endpointFor(field);
    if (!endpoint) {
      return Promise.reject(new Error("Unable to resolve the parameter endpoint."));
    }
    var dependencies = dependencyPayload(field);
    updateProviderContext(field, dependencies, "");
    return fetch(endpoint, {
      method: "POST",
      credentials: "same-origin",
      headers: crumbHeaders(),
      signal: signal,
      body: JSON.stringify({
        parameter: field.dataset.parameterName,
        dependencies: dependencies,
        version: version,
      }),
    }).then(function (response) {
      return response.json().then(function (body) {
        if (!response.ok || !body.ok) {
          var firstError = body.errors && body.errors.length ? body.errors[0] : null;
          throw new Error(firstError ? firstError.message : "Request failed with " + response.status);
        }
        if (body.version !== version) {
          var stale = new Error("A newer dependency refresh superseded this response.");
          stale.name = "StaleResponseError";
          throw stale;
        }
        updateProviderContext(field, dependencies, body.snapshotId);
        return {
          options: body.options || [],
          defaultValue: body.default,
          snapshotId: body.snapshotId,
          fallback: Boolean(body.fallback),
          fallbackReason: body.fallbackReason,
        };
      });
    });
  }

  function normalizedSearch(option) {
    return [option.value, option.label, option.description, option.group]
      .concat(option.tags || [])
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase();
  }

  function sortedSetValues(values) {
    return Array.from(values).sort();
  }

  function removeChildren(node) {
    while (node.firstChild) {
      node.removeChild(node.firstChild);
    }
  }

  function setSubmittedValues(field, values, notify) {
    Array.prototype.forEach.call(
      field.querySelectorAll("[data-up-generated-value]"),
      function (node) {
        node.remove();
      },
    );
    sortedSetValues(values).forEach(function (value) {
      var input = document.createElement("input");
      input.type = "hidden";
      input.name = "value";
      input.value = value;
      input.dataset.upGeneratedValue = "true";
      field.appendChild(input);
    });
    if (notify) {
      field.dispatchEvent(
        new CustomEvent("up:value-changed", {
          bubbles: true,
          detail: {parameter: field.dataset.parameterName},
        }),
      );
    }
  }

  function createSearch(statusId) {
    var input = document.createElement("input");
    input.type = "search";
    input.className = "jenkins-input unified-parameters-search";
    input.placeholder = "Search label, value, description, group, or tag";
    input.setAttribute("aria-label", "Search options");
    input.setAttribute("aria-describedby", statusId);
    return input;
  }

  function createStatus() {
    var status = document.createElement("div");
    status.id = uniqueId("up-status");
    status.className = "unified-parameters-status";
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    return status;
  }

  function disableFallback(field) {
    var fallback = field.querySelector("[data-up-fallback]");
    Array.prototype.forEach.call(fallback.querySelectorAll("input, select"), function (control) {
      control.disabled = true;
      control.removeAttribute("name");
    });
    fallback.hidden = true;
  }

  function bridgeFallbackValues(field) {
    var fallback = field.querySelector("[data-up-fallback]");

    function sync(notify) {
      Array.prototype.forEach.call(
        fallback.querySelectorAll("[data-up-fallback-json-value]"),
        function (input) {
          input.remove();
        },
      );
      var values = Array.prototype.map
        .call(
          fallback.querySelectorAll(
            "input[data-up-native-value]:checked, select[data-up-native-value] option:checked",
          ),
          function (control) {
            return control.value;
          },
        )
        .filter(function (value) {
          return field.dataset.multiple === "true" ? Boolean(value) : true;
        });
      if (field.dataset.multiple !== "true" && values.length === 0) {
        values.push("");
      }
      values.forEach(function (value) {
        var input = document.createElement("input");
        input.type = "hidden";
        input.name = "value";
        input.value = value;
        input.dataset.upFallbackJsonValue = "true";
        fallback.appendChild(input);
      });
      if (notify) {
        field.dispatchEvent(
          new CustomEvent("up:value-changed", {
            bubbles: true,
            detail: {parameter: field.dataset.parameterName},
          }),
        );
      }
    }

    Array.prototype.forEach.call(
      fallback.querySelectorAll("[data-up-native-value]"),
      function (control) {
        control.addEventListener("change", function () {
          sync(true);
        });
      },
    );
    sync(false);
  }

  function detailsFor(option, detailsId) {
    var details = document.createElement("details");
    details.id = detailsId;
    details.className = "unified-parameters-details";
    var summary = document.createElement("summary");
    summary.textContent = "Details";
    details.appendChild(summary);
    var machineValue = document.createElement("span");
    machineValue.textContent = "Machine value: " + option.value;
    details.appendChild(machineValue);
    if (option.description) {
      var description = document.createElement("span");
      description.textContent = option.description;
      details.appendChild(description);
    }
    if (option.disabledReason) {
      var disabledReason = document.createElement("span");
      disabledReason.textContent = "Unavailable: " + option.disabledReason;
      details.appendChild(disabledReason);
    }
    return details;
  }

  function optionRow(option, selected, onChange) {
    var row = document.createElement("div");
    row.className = "unified-parameters-option";
    row.setAttribute("aria-disabled", String(Boolean(option.disabled)));
    var label = document.createElement("label");
    var checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = selected.has(option.value);
    checkbox.disabled = option.disabled;
    var detailsId = uniqueId("up-option-details");
    if (option.description || option.disabledReason || option.label !== option.value) {
      checkbox.setAttribute("aria-describedby", detailsId);
    }
    checkbox.addEventListener("change", function () {
      onChange(option.value, checkbox.checked);
    });
    var copy = document.createElement("span");
    copy.className = "unified-parameters-option-copy";
    var caption = document.createElement("strong");
    caption.textContent = option.label || option.value;
    copy.appendChild(caption);
    if (option.disabledReason) {
      var reason = document.createElement("small");
      reason.textContent = option.disabledReason;
      copy.appendChild(reason);
    }
    label.appendChild(checkbox);
    label.appendChild(copy);
    row.appendChild(label);
    if (option.description || option.disabledReason || option.label !== option.value) {
      row.appendChild(detailsFor(option, detailsId));
    }
    return row;
  }

  function enhanceSingle(field, options, initialValues) {
    var enhanced = document.createElement("div");
    enhanced.className = "unified-parameters-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(status.id);
    var select = document.createElement("select");
    select.className = "jenkins-select__input unified-parameters-native-list";
    select.setAttribute("aria-describedby", status.id);
    var allowCustom = field.dataset.allowCustom === "true";
    var searchable =
      field.dataset.renderer === "searchable" ||
      options.length > MAX_RENDERED_OPTIONS ||
      allowCustom;
    var customButton = null;
    if (searchable) {
      select.size = 8;
      enhanced.appendChild(search);
    }
    var selected = new Set(initialValues);

    function render() {
      var query = searchable ? search.value.toLocaleLowerCase() : "";
      var matches = options.filter(function (option) {
        return option.search.indexOf(query) !== -1;
      });
      removeChildren(select);
      if (field.dataset.required !== "true") {
        var none = document.createElement("option");
        none.value = "";
        none.textContent = "None";
        none.selected = selected.size === 0;
        select.appendChild(none);
      }
      sortedSetValues(selected).forEach(function (value) {
        var known = options.some(function (option) {
          return option.value === value;
        });
        if (!known) {
          var custom = document.createElement("option");
          custom.value = value;
          custom.textContent = value + " (custom)";
          custom.selected = true;
          select.appendChild(custom);
        }
      });
      matches.slice(0, MAX_RENDERED_OPTIONS).forEach(function (option) {
        var element = document.createElement("option");
        element.value = option.value;
        element.textContent = option.label || option.value;
        element.disabled = option.disabled;
        element.title = option.disabledReason || option.description || "";
        element.selected = selected.has(option.value);
        select.appendChild(element);
      });
      status.textContent =
        matches.length > MAX_RENDERED_OPTIONS
          ? matches.length + " matches; showing " + MAX_RENDERED_OPTIONS + ". Refine the search."
          : matches.length + " option(s).";
      if (customButton) {
        var candidate = search.value;
        var maximum = Number(field.dataset.customMaxLength || "256");
        var valid = candidate.length > 0 && candidate.length <= maximum;
        if (valid && field.dataset.customPattern) {
          try {
            valid = new RegExp("^(?:" + field.dataset.customPattern + ")$").test(
              candidate,
            );
          } catch (_error) {
            valid = false;
          }
        }
        customButton.disabled = !valid;
        customButton.textContent = candidate
          ? 'Use custom value "' + candidate + '"'
          : "Enter a custom value";
        customButton.title = valid
          ? "Create a custom value distinct from the configured options."
          : "The custom value does not satisfy the configured policy.";
      }
    }

    if (searchable) {
      select.size = Math.min(
        8,
        Math.max(3, options.length + (field.dataset.required === "true" ? 0 : 1)),
      );
      search.addEventListener("input", render);
      search.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && search.value) {
          search.value = "";
          render();
          event.preventDefault();
        } else if (
          event.key === "Enter" &&
          customButton &&
          !customButton.disabled
        ) {
          customButton.click();
          event.preventDefault();
        }
      });
    }
    select.addEventListener("change", function () {
      selected.clear();
      if (select.value) {
        selected.add(select.value);
      }
      setSubmittedValues(field, selected, true);
    });
    enhanced.appendChild(select);
    if (allowCustom) {
      customButton = document.createElement("button");
      customButton.type = "button";
      customButton.className = "jenkins-button unified-parameters-custom-value";
      customButton.setAttribute(
        "aria-label",
        "Use search text as a custom value",
      );
      customButton.addEventListener("click", function () {
        if (customButton.disabled) {
          return;
        }
        selected.clear();
        selected.add(search.value);
        setSubmittedValues(field, selected, true);
        render();
        status.textContent = 'Custom value "' + search.value + '" selected.';
      });
      enhanced.appendChild(customButton);
    }
    enhanced.appendChild(status);
    disableFallback(field);
    field.appendChild(enhanced);
    setSubmittedValues(field, selected);
    render();
  }

  function createToolbarButton(caption, action) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-button";
    button.textContent = caption;
    button.addEventListener("click", action);
    return button;
  }

  function enhanceMultipleList(field, options, initialValues) {
    var selected = new Set(initialValues);
    var enhanced = document.createElement("div");
    enhanced.className = "unified-parameters-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(status.id);
    var select = document.createElement("select");
    select.multiple = true;
    select.size = Math.min(10, Math.max(4, options.length));
    select.className = "jenkins-select__input unified-parameters-native-list";
    select.setAttribute("aria-describedby", status.id);

    function filtered() {
      var query = search.value.toLocaleLowerCase();
      return options.filter(function (option) {
        return option.search.indexOf(query) !== -1;
      });
    }

    function render() {
      var matches = filtered();
      removeChildren(select);
      matches.slice(0, MAX_RENDERED_OPTIONS).forEach(function (option) {
        var item = document.createElement("option");
        item.value = option.value;
        item.textContent = option.label || option.value;
        item.disabled = option.disabled;
        item.selected = selected.has(option.value);
        item.title = option.disabledReason || option.description || "";
        select.appendChild(item);
      });
      status.textContent = matches.length + " option(s), " + selected.size + " selected.";
    }

    search.addEventListener("input", render);
    search.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && search.value) {
        search.value = "";
        render();
        event.preventDefault();
      }
    });
    select.addEventListener("change", function () {
      Array.prototype.forEach.call(select.options, function (option) {
        if (option.selected) {
          selected.add(option.value);
        } else {
          selected.delete(option.value);
        }
      });
      setSubmittedValues(field, selected, true);
      status.textContent = filtered().length + " option(s), " + selected.size + " selected.";
    });
    enhanced.appendChild(search);
    enhanced.appendChild(select);
    enhanced.appendChild(status);
    disableFallback(field);
    field.appendChild(enhanced);
    setSubmittedValues(field, selected);
    render();
  }

  function enhanceMultiple(field, options, initialValues) {
    if (field.dataset.renderer === "list") {
      enhanceMultipleList(field, options, initialValues);
      return;
    }

    var selected = new Set(initialValues);
    var collapsedGroups = new Set();
    var enhanced = document.createElement("div");
    enhanced.className = "unified-parameters-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(status.id);
    var chips = document.createElement("div");
    chips.className = "unified-parameters-chips";
    chips.setAttribute("aria-label", "Selected options");
    var toolbar = document.createElement("div");
    toolbar.className = "unified-parameters-toolbar";
    toolbar.setAttribute("aria-label", "Selection actions");
    var list = document.createElement("div");
    list.id = uniqueId("up-options");
    list.className = "unified-parameters-options";
    list.setAttribute("aria-label", "Available options");
    search.setAttribute("aria-controls", list.id);

    function filtered() {
      var query = search.value.toLocaleLowerCase();
      return options.filter(function (option) {
        return option.search.indexOf(query) !== -1;
      });
    }

    function renderChips() {
      removeChildren(chips);
      if (field.dataset.renderer !== "chips") {
        return;
      }
      sortedSetValues(selected).forEach(function (value) {
        var option = options.find(function (candidate) {
          return candidate.value === value;
        });
        var caption = option ? option.label : value;
        var chip = document.createElement("span");
        chip.className = "unified-parameters-chip";
        chip.appendChild(document.createTextNode(caption));
        var remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "x";
        remove.setAttribute("aria-label", "Remove " + caption);
        remove.addEventListener("click", function () {
          selected.delete(value);
          sync(true, "Removed " + caption + ".");
        });
        chip.appendChild(remove);
        chips.appendChild(chip);
      });
    }

    function updateStatus(message) {
      status.textContent =
        (message ? message + " " : "") +
        filtered().length +
        " option(s), " +
        selected.size +
        " selected.";
    }

    function changeSelection(values, mode, message) {
      var affected = 0;
      var skipped = 0;
      values.forEach(function (option) {
        if (option.disabled) {
          skipped += 1;
          return;
        }
        var before = selected.has(option.value);
        if (mode === "select") {
          selected.add(option.value);
        } else if (mode === "clear") {
          selected.delete(option.value);
        } else if (before) {
          selected.delete(option.value);
        } else {
          selected.add(option.value);
        }
        if (before !== selected.has(option.value)) {
          affected += 1;
        }
      });
      sync(true, message + " " + affected + " changed; " + skipped + " disabled skipped.");
    }

    function groupHeader(groupName, groupOptions) {
      var heading = document.createElement("div");
      heading.className = "unified-parameters-group";
      var collapse = createToolbarButton(groupName || "Other", function () {
        if (collapsedGroups.has(groupName)) {
          collapsedGroups.delete(groupName);
        } else {
          collapsedGroups.add(groupName);
        }
        renderList();
      });
      collapse.className = "unified-parameters-group-toggle";
      collapse.setAttribute("aria-expanded", String(!collapsedGroups.has(groupName)));
      var selectGroup = createToolbarButton("Select group", function () {
        changeSelection(groupOptions, "select", "Group selection updated.");
      });
      selectGroup.className = "jenkins-button unified-parameters-group-select";
      heading.appendChild(collapse);
      heading.appendChild(selectGroup);
      return heading;
    }

    function renderList() {
      var matches = filtered();
      var rendered = matches.slice(0, MAX_RENDERED_OPTIONS);
      var grouped = new Map();
      rendered.forEach(function (option) {
        var group = option.group || "";
        if (!grouped.has(group)) {
          grouped.set(group, []);
        }
        grouped.get(group).push(option);
      });
      removeChildren(list);
      grouped.forEach(function (groupOptions, groupName) {
        var group = document.createElement("section");
        group.className = "unified-parameters-group-section";
        group.setAttribute("aria-label", groupName || "Other options");
        group.appendChild(groupHeader(groupName, groupOptions));
        if (!collapsedGroups.has(groupName) || search.value) {
          groupOptions.forEach(function (option) {
            group.appendChild(
              optionRow(option, selected, function (value, checked) {
                if (checked) {
                  selected.add(value);
                } else {
                  selected.delete(value);
                }
                sync(false, "Selection updated.");
              }),
            );
          });
        }
        list.appendChild(group);
      });
      if (matches.length === 0) {
        var empty = document.createElement("p");
        empty.className = "unified-parameters-empty";
        empty.textContent = "No options match this search.";
        list.appendChild(empty);
      }
      status.textContent =
        matches.length > MAX_RENDERED_OPTIONS
          ? matches.length +
            " matches; showing " +
            MAX_RENDERED_OPTIONS +
            ". Refine the search to reach other options. " +
            selected.size +
            " selected."
          : matches.length + " option(s), " + selected.size + " selected.";
    }

    function sync(rerender, message) {
      setSubmittedValues(field, selected, Boolean(message));
      renderChips();
      if (rerender !== false) {
        renderList();
        if (message) {
          updateStatus(message);
        }
      } else {
        updateStatus(message);
      }
    }

    toolbar.appendChild(
      createToolbarButton("Select visible", function () {
        changeSelection(filtered(), "select", "Visible selection updated.");
      }),
    );
    toolbar.appendChild(
      createToolbarButton("Invert visible", function () {
        changeSelection(filtered(), "invert", "Visible selection inverted.");
      }),
    );
    toolbar.appendChild(
      createToolbarButton("Clear visible", function () {
        changeSelection(filtered(), "clear", "Visible selection cleared.");
      }),
    );
    toolbar.appendChild(
      createToolbarButton("Clear all", function () {
        var affected = selected.size;
        selected.clear();
        sync(true, "All selections cleared. " + affected + " changed.");
      }),
    );

    search.addEventListener("input", renderList);
    search.addEventListener("keydown", function (event) {
      if (event.key === "Backspace" && search.value === "" && selected.size > 0) {
        var values = sortedSetValues(selected);
        var removed = values[values.length - 1];
        selected.delete(removed);
        sync(true, "Removed " + removed + ".");
        event.preventDefault();
      } else if (event.key === "Escape" && search.value) {
        search.value = "";
        renderList();
        event.preventDefault();
      }
    });
    list.addEventListener("keydown", function (event) {
      if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
        return;
      }
      var controls = Array.prototype.filter.call(
        list.querySelectorAll('input[type="checkbox"]:not(:disabled)'),
        function (control) {
          return control.offsetParent !== null;
        },
      );
      if (!controls.length) {
        return;
      }
      var current = controls.indexOf(document.activeElement);
      var target = current;
      if (event.key === "Home") {
        target = 0;
      } else if (event.key === "End") {
        target = controls.length - 1;
      } else if (event.key === "ArrowDown") {
        target = Math.min(controls.length - 1, Math.max(0, current + 1));
      } else if (event.key === "ArrowUp") {
        target = Math.max(0, current < 0 ? 0 : current - 1);
      }
      controls[target].focus();
      event.preventDefault();
    });

    if (field.dataset.renderer === "chips") {
      enhanced.appendChild(chips);
    }
    enhanced.appendChild(search);
    enhanced.appendChild(toolbar);
    enhanced.appendChild(list);
    enhanced.appendChild(status);
    disableFallback(field);
    field.appendChild(enhanced);
    sync(true);
  }

  function removeRefreshStatus(field) {
    Array.prototype.forEach.call(
      field.querySelectorAll("[data-up-refresh-status]"),
      function (status) {
        status.remove();
      },
    );
  }

  function showRefreshStatus(field, message, role) {
    removeRefreshStatus(field);
    var status = createStatus();
    status.dataset.upRefreshStatus = "true";
    status.textContent = message;
    if (role) {
      status.setAttribute("role", role);
    }
    field.appendChild(status);
    return status;
  }

  function refresh(field, preserveSelection) {
    var initialValues = selectedValuesForField(field);
    var previousValues = sortedSetValues(new Set(initialValues));
    var state = refreshStates.get(field) || {version: 0, controller: null, timer: null};
    state.version += 1;
    if (state.controller) {
      state.controller.abort();
    }
    state.controller = new AbortController();
    refreshStates.set(field, state);
    var version = state.version;
    field.dataset.upState = preserveSelection ? "stale" : "loading";
    showRefreshStatus(
      field,
      preserveSelection ? "Dependencies changed; refreshing options..." : "Loading options...",
    );
    resolveOptions(field, state.controller.signal, version)
      .then(function (resolved) {
        if (refreshStates.get(field).version !== version) {
          return;
        }
        var modelAvailableAt = performance.now();
        var options = resolved.options.map(function (option) {
          option.search = normalizedSearch(option);
          return option;
        });
        var knownValues = new Set(
          options
            .filter(function (option) {
              return !option.disabled;
            })
            .map(function (option) {
              return option.value;
            }),
        );
        if (field.dataset.allowCustom !== "true") {
          initialValues = initialValues.filter(function (value) {
            return knownValues.has(value);
          });
        }
        if (initialValues.length === 0 && resolved.defaultValue != null) {
          initialValues = Array.isArray(resolved.defaultValue)
            ? resolved.defaultValue.slice()
            : [resolved.defaultValue];
        }
        removeRefreshStatus(field);
        Array.prototype.forEach.call(
          field.querySelectorAll("[data-up-enhanced]"),
          function (enhanced) {
            enhanced.remove();
          },
        );
        if (field.dataset.multiple === "true") {
          enhanceMultiple(field, options, initialValues);
        } else {
          enhanceSingle(field, options, initialValues);
        }
        var interactiveStatus = field.querySelector(
          "[data-up-enhanced] .unified-parameters-status",
        );
        if (resolved.fallback && interactiveStatus) {
          interactiveStatus.textContent =
            "Using last-known-good options: " +
            (resolved.fallbackReason || "Provider refresh failed.");
        }
        field.dataset.upState = resolved.fallback
          ? "fallback"
          : options.length
            ? "ready"
            : "empty";
        field.dataset.upEnhancementMs = String(performance.now() - modelAvailableAt);
        field.dataset.upInteractive = "true";
        var refreshedValues = sortedSetValues(new Set(selectedValuesForField(field)));
        if (
          preserveSelection &&
          JSON.stringify(previousValues) !== JSON.stringify(refreshedValues)
        ) {
          field.dispatchEvent(
            new CustomEvent("up:value-changed", {
              bubbles: true,
              detail: {parameter: field.dataset.parameterName},
            }),
          );
        }
      })
      .catch(function (error) {
        if (error.name === "AbortError" || error.name === "StaleResponseError") {
          return;
        }
        field.dataset.upState = preserveSelection ? "stale" : "error";
        showRefreshStatus(
          field,
          preserveSelection
            ? "Options could not be refreshed; the visible selection is stale: " +
                error.message
            : "Options could not be enhanced: " + error.message,
          "alert",
        );
      });
  }

  function scheduleReactiveRefresh(changedName) {
    Array.prototype.forEach.call(
      document.querySelectorAll("[data-unified-parameter]"),
      function (field) {
        if (dependencyNames(field).indexOf(changedName) === -1) {
          return;
        }
        var state = refreshStates.get(field) || {
          version: 0,
          controller: null,
          timer: null,
        };
        if (state.timer) {
          window.clearTimeout(state.timer);
        }
        if (state.controller) {
          state.controller.abort();
        }
        field.dataset.upState = "loading";
        showRefreshStatus(field, "Dependencies changed; waiting to refresh...");
        state.timer = window.setTimeout(function () {
          state.timer = null;
          refresh(field, true);
        }, REACTIVE_DEBOUNCE_MS);
        refreshStates.set(field, state);
      },
    );
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.addEventListener("up:value-changed", function (event) {
      scheduleReactiveRefresh(event.detail.parameter);
    });
    Array.prototype.forEach.call(
      document.querySelectorAll("[data-unified-parameter]"),
      function (field) {
        bridgeFallbackValues(field);
        if (
          field.dataset.renderer !== "radio" ||
          field.dataset.providerBacked === "true" ||
          dependencyNames(field).length
        ) {
          refresh(field, false);
        } else {
          field.dataset.upState = "ready";
        }
      },
    );
  });
})();
