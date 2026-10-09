(function () {
  "use strict";

  var MAX_RENDERED_OPTIONS = 160;
  var MAX_RADIO_OPTIONS = 60;
  var CHIP_COLLAPSE_THRESHOLD = 12;
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
    return new URL("parameter-foundry/api/v1/resolve", action);
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
        document.querySelectorAll("[data-parameter-foundry]"),
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
      // Legacy Git accepts literal revisions independently of the list's lifetime.
      snapshot.value = field.dataset.standardApiInputMode === "list-git-branches" ? "" : snapshotId || "";
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
          table: body.table || null,
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

  function createSearch(field, statusId) {
    var input = document.createElement("input");
    input.type = "search";
    input.className = "jenkins-input parameter-foundry-search";
    input.placeholder = localized(field, "searchPlaceholder", "Search label, value, description, group, or tag");
    input.setAttribute("aria-label", localized(field, "searchLabel", "Search options"));
    input.setAttribute("aria-describedby", statusId);
    return input;
  }

  /**
   * A selected-value list is a convenience, not the control: a table that is filled in one click can
   * produce dozens of chips, which used to push the table off screen. The region scrolls inside a
   * bounded box and collapses itself above the threshold, with the toggle keeping the full list one
   * click away.
   */
  function chipsRegion(field) {
    var region = document.createElement("div");
    region.className = "parameter-foundry-chips-region";
    region.dataset.upExpanded = "true";
    var chips = document.createElement("div");
    chips.className = "parameter-foundry-chips";
    chips.setAttribute("aria-label", localized(field, "selectedOptionsLabel", "Selected options"));
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "jenkins-button parameter-foundry-group-select parameter-foundry-chips-toggle";
    toggle.hidden = true;
    toggle.addEventListener("click", function () {
      region.dataset.upTouched = "true";
      region.dataset.upExpanded = region.dataset.upExpanded === "true" ? "false" : "true";
      syncChipRegion(field, region);
    });
    region.appendChild(chips);
    region.appendChild(toggle);
    return {region: region, chips: chips};
  }

  function syncChipRegion(field, region) {
    var chips = region.querySelector(".parameter-foundry-chips");
    var toggle = region.querySelector(".parameter-foundry-chips-toggle");
    var count = chips.childElementCount;
    if (!region.dataset.upTouched) {
      region.dataset.upExpanded = String(count <= CHIP_COLLAPSE_THRESHOLD);
    }
    var expanded = region.dataset.upExpanded === "true";
    chips.hidden = !expanded;
    toggle.hidden = count === 0;
    toggle.textContent = expanded
      ? localized(field, "hideSelected", "Hide selected")
      : formatMessage(localized(field, "showSelected", "Show selected ({0})"), [count]);
  }

  function createStatus() {
    var status = document.createElement("div");
    status.id = uniqueId("up-status");
    status.className = "parameter-foundry-status";
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    return status;
  }

  function localized(field, key, fallback) {
    var attribute = "up" + key.charAt(0).toUpperCase() + key.slice(1);
    return field.dataset[attribute] || fallback;
  }

  function selectedSummary(field, selected, total) {
    return localized(field, "selectionSummary", "Selected {0} of {1}")
      .replace("{0}", selected)
      .replace("{1}", total);
  }

  function formatMessage(template, values) {
    return values.reduce(function (result, value, index) {
      return result.replace("{" + index + "}", value);
    }, template);
  }

  function localizedError(error) {
    var message = error && error.message ? error.message : String(error);
    if ((navigator.language || "").toLowerCase().indexOf("zh") !== 0) {
      return message;
    }
    var translations = {
      "Dependency field is unavailable: ": "依赖参数不可用：",
      "Request failed with ": "请求失败，状态码：",
      "Reactive dependencies require a shared Provider":
        "Reactive 依赖必须配置共享 Provider",
      "Renderer is not valid for multiple choice: table":
        "多选参数不支持当前渲染器：table",
      "Details": "详情",
      "Machine value: ": "机器值：",
      "Unavailable: ": "不可用：",
      "Unable to resolve the parameter endpoint.":
        "无法确定参数请求地址。",
      "A newer dependency refresh superseded this response.":
        "更新的依赖刷新请求已替代当前响应。",
      "Dependencies changed; waiting to refresh...":
        "依赖项已变更，等待刷新……",
    };
    if (translations[message]) {
      return translations[message];
    }
    for (var prefix in translations) {
      if (prefix.endsWith(": ") && message.indexOf(prefix) === 0) {
        return translations[prefix] + message.slice(prefix.length);
      }
    }
    return message;
  }

  function parseJson(value, fallback) {
    try {
      return JSON.parse(value);
    } catch (error) {
      return fallback;
    }
  }

  function enhanceTable(field, options, initialValues) {
    var tableModel = parseJson(field.dataset.upResolvedTable || field.dataset.table || "", null);
    if (!tableModel || !tableModel.rows || !tableModel.columns || !tableModel.cells) {
      return false;
    }
    var selected = new Set(initialValues);
    var enhanced = document.createElement("div");
    enhanced.className = "parameter-foundry-enhanced parameter-foundry-table-enhanced";
    var status = createStatus();
    var chipRegion = chipsRegion(field);
    var chips = chipRegion.chips;
    var inputsByValue = new Map();
    var rowsByValue = new Map(
      tableModel.rows.map(function (row) {
        return [row.value, row];
      }),
    );
    var columnsByValue = new Map(
      tableModel.columns.map(function (column) {
        return [column.value, column];
      }),
    );
    var cellLabels = new Map(
      tableModel.cells.map(function (cell) {
        return [cell.value, cellCaption(cell)];
      }),
    );
    var cellsByValue = new Map(
      tableModel.cells.map(function (cell) {
        return [cell.value, cell];
      }),
    );
    var rowSelectionMode = tableModel.rowSelectionMode || "any";

    function rowCellValues(rowValue) {
      return tableModel.cells
        .filter(function (cell) {
          return cell.row === rowValue;
        })
        .map(function (cell) {
          return cell.value;
        });
    }

    function cellAt(rowValue, columnValue) {
      return cells.get(rowValue + "\u0000" + columnValue) || null;
    }

    function selectableRowValues(rowValue) {
      return rowCellValues(rowValue).filter(function (value) {
        var cell = cellsByValue.get(value);
        return cell && !cell.disabled;
      });
    }

    function columnCellValues(columnValue) {
      return tableModel.rows
        .map(function (row) {
          return cellAt(row.value, columnValue);
        })
        .filter(function (cell) {
          return cell && !cell.disabled;
        })
        .map(function (cell) {
          return cell.value;
        });
    }

    function rowHasSelection(rowValue) {
      return rowCellValues(rowValue).some(function (value) {
        return selected.has(value);
      });
    }

    function clearRow(values, rowValue, keepValue) {
      var next = new Set(values);
      rowCellValues(rowValue).forEach(function (value) {
        if (value !== keepValue) {
          next.delete(value);
        }
      });
      return next;
    }

    /** Repaints every control, the chips, and the summary from the current selection. */
    function sync(message) {
      inputsByValue.forEach(function (input, value) {
        input.checked = selected.has(value);
      });
      setSubmittedValues(field, selected, true);
      renderChips();
      updateStatus(message);
    }

    /**
     * Fills one row. With a rank constraint a row keeps a single cell, so the shortcut picks its
     * first selectable cell instead of violating the rule; a second click clears the row again.
     */
    function toggleRow(rowModel) {
      var caption = rowModel.label || rowModel.value;
      var values = selectableRowValues(rowModel.value);
      if (!values.length) {
        return;
      }
      var allSelected = values.every(function (value) {
        return selected.has(value);
      });
      if (allSelected) {
        rowCellValues(rowModel.value).forEach(function (value) {
          selected.delete(value);
        });
        sync(formatMessage(localized(field, "rowSelectionCleared", "Row {0} cleared."), [caption]));
        return;
      }
      if (rowSelectionMode === "any") {
        values.forEach(function (value) {
          selected.add(value);
        });
        sync(formatMessage(localized(field, "rowSelectionUpdated", "Row {0} selected."), [caption]));
        return;
      }
      // A rank constraint allows one cell per row, so the shortcut fills the row's rank instead of
      // breaking the rule the definition asked for.
      selected = clearRow(selected, rowModel.value, values[0]);
      selected.add(values[0]);
      sync(
        formatMessage(
          localized(field, "rowSelectionLimited", "Row {0}: one cell per row, the first available is selected."),
          [caption],
        ),
      );
    }

    /** Toggles every selectable cell of the table, which is the fastest way to reach a complement. */
    function invertSelection() {
      var changed = 0;
      tableModel.cells.forEach(function (cell) {
        if (cell.disabled) {
          return;
        }
        if (selected.has(cell.value)) {
          selected.delete(cell.value);
        } else {
          selected.add(cell.value);
        }
        changed += 1;
      });
      if (rowSelectionMode !== "any") {
        // Keep the table submittable: a rank constraint allows one cell per row.
        var rank = new Set();
        tableModel.rows.forEach(function (row) {
          var picked = selectableRowValues(row.value).filter(function (value) {
            return selected.has(value);
          });
          if (picked.length) {
            rank.add(picked[0]);
          }
        });
        selected = rank;
      }
      sync(formatMessage(localized(field, "selectionInverted", "Selection inverted. {0} cells changed."), [changed]));
    }

    /** Fills one column, replacing the cell of a row that already had one. A second click clears it. */
    function toggleColumn(columnModel) {
      var caption = columnModel.label || columnModel.value;
      var values = columnCellValues(columnModel.value);
      if (!values.length) {
        return;
      }
      var allSelected = values.every(function (value) {
        return selected.has(value);
      });
      values.forEach(function (value) {
        if (allSelected) {
          selected.delete(value);
          return;
        }
        if (rowSelectionMode !== "any") {
          selected = clearRow(selected, cellsByValue.get(value).row, value);
        }
        selected.add(value);
      });
      sync(
        allSelected
          ? formatMessage(localized(field, "columnSelectionCleared", "Column {0} cleared."), [caption])
          : formatMessage(localized(field, "columnSelectionUpdated", "Column {0} selected."), [caption]),
      );
    }

    /** Fills every row that has a selectable cell, keeping the cells a user already picked. */
    function selectAllRows() {
      tableModel.rows.forEach(function (row) {
        var values = selectableRowValues(row.value);
        if (!values.length) {
          return;
        }
        if (rowSelectionMode === "any") {
          values.forEach(function (value) {
            selected.add(value);
          });
        } else if (!rowHasSelection(row.value)) {
          selected.add(values[0]);
        }
      });
      sync(localized(field, "allCellsSelected", "Every row is filled."));
    }

    function clearAllCells() {
      var cleared = selected.size;
      selected.clear();
      sync(formatMessage(localized(field, "allSelectionsCleared", "All selections cleared. {0} changed."), [cleared]));
    }

    function toolbarButton(label, onClick) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "jenkins-button parameter-foundry-group-select";
    button.textContent = label;
    button.addEventListener("click", onClick);
    return button;
  }

  /**
   * Header cell of a table that is also a one-click shortcut: the caption keeps naming the row or
   * column, and the button selects or clears the whole line.
   */
  function headerAction(caption, ariaLabel, onClick) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "parameter-foundry-table-header-action";
    button.textContent = caption;
    button.setAttribute("aria-label", ariaLabel);
    button.title = ariaLabel;
    button.addEventListener("click", onClick);
    return button;
  }

  function cellCaption(cell) {
      if (cell.label && cell.label !== cell.value) {
        return cell.label;
      }
      var row = rowsByValue.get(cell.row);
      var column = columnsByValue.get(cell.column);
      return (row ? row.label || row.value : cell.row) + " / " + (column ? column.label || column.value : cell.column);
    }

    function renderChips() {
      removeChildren(chips);
      if (field.dataset.selectedDisplay !== "chips") {
        return;
      }
      sortedSetValues(selected).forEach(function (value) {
        var caption = cellLabels.get(value) || value;
        var chip = document.createElement("span");
        chip.className = "parameter-foundry-chip";
        chip.appendChild(document.createTextNode(caption));
        var remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "x";
        remove.setAttribute("aria-label", "Remove " + caption);
        remove.addEventListener("click", function () {
          selected.delete(value);
          var input = inputsByValue.get(value);
          if (input) {
            input.checked = false;
          }
          setSubmittedValues(field, selected, true);
          renderChips();
          updateStatus(formatMessage(localized(field, "removed", "Removed {0}."), [caption]));
        });
        chip.appendChild(remove);
        chips.appendChild(chip);
      });
      syncChipRegion(field, chipRegion.region);
    }

    function updateStatus(message) {
      status.textContent =
        (message ? message + " " : "") + selectedSummary(field, selected.size, tableModel.cells.length);
    }

    var toolbar = document.createElement("div");
    toolbar.className = "parameter-foundry-toolbar";
    toolbar.setAttribute("aria-label", "Table actions");
    toolbar.appendChild(
      toolbarButton(localized(field, "selectAllCells", "Select all"), function () {
        selectAllRows();
      }),
    );
    toolbar.appendChild(
      toolbarButton(localized(field, "invertSelection", "Invert"), function () {
        invertSelection();
      }),
    );
    toolbar.appendChild(
      toolbarButton(localized(field, "clearAll", "Clear all"), function () {
        clearAllCells();
      }),
    );
    var table = document.createElement("table");
    table.className = "parameter-foundry-table";
    if (tableModel.columnAxisLabel) {
      // The column caption names the axis, so the header row stays the machine values it offers.
      var caption = document.createElement("caption");
      caption.className = "parameter-foundry-table-caption";
      caption.textContent = tableModel.columnAxisLabel;
      table.appendChild(caption);
    }
    var head = document.createElement("thead");
    var headerRow = document.createElement("tr");
    var corner = document.createElement("th");
    corner.scope = "col";
    corner.textContent = tableModel.rowAxisLabel || "";
    headerRow.appendChild(corner);
    tableModel.columns.forEach(function (column) {
      var th = document.createElement("th");
      th.scope = "col";
      th.appendChild(
        headerAction(
          column.label || column.value,
          formatMessage(localized(field, "selectColumn", "Select column {0}"), [column.label || column.value]),
          function () {
            toggleColumn(column);
          },
        ),
      );
      headerRow.appendChild(th);
    });
    head.appendChild(headerRow);
    table.appendChild(head);
    var body = document.createElement("tbody");
    var cells = new Map(tableModel.cells.map(function (cell) {
      return [cell.row + "\u0000" + cell.column, cell];
    }));
    tableModel.rows.forEach(function (rowModel) {
      var tr = document.createElement("tr");
      var rowHeader = document.createElement("th");
      rowHeader.scope = "row";
      rowHeader.appendChild(
        headerAction(
          rowModel.label || rowModel.value,
          formatMessage(localized(field, "selectRow", "Select row {0}"), [rowModel.label || rowModel.value]),
          function () {
            toggleRow(rowModel);
          },
        ),
      );
      tr.appendChild(rowHeader);
      tableModel.columns.forEach(function (column) {
        var cell = cells.get(rowModel.value + "\u0000" + column.value);
        var td = document.createElement("td");
        if (!cell) {
          td.textContent = "-";
          td.setAttribute("aria-label", "Unavailable");
        } else {
          var input = document.createElement("input");
          input.type = field.dataset.multiple === "true" ? "checkbox" : "radio";
          input.name = field.dataset.parameterName;
          input.value = cell.value;
          input.checked = selected.has(cell.value);
          input.disabled = Boolean(cell.disabled);
          var cellLabel = (rowModel.label || rowModel.value) + " / " + (column.label || column.value);
          if (cell.disabled && cell.disabledReason) {
            cellLabel += " - Unavailable: " + cell.disabledReason;
            input.title = cell.disabledReason;
          }
          input.setAttribute("aria-label", cellLabel);
          input.addEventListener("change", function () {
            if (field.dataset.multiple !== "true") {
              selected.clear();
            }
            if (input.checked && rowSelectionMode !== "any") {
              selected = clearRow(selected, rowModel.value, cell.value);
            }
            if (input.checked) selected.add(cell.value); else selected.delete(cell.value);
            sync(formatMessage(localized(field, "selectionUpdated", "Selection updated."), []));
          });
          inputsByValue.set(cell.value, input);
          td.appendChild(input);
        }
        tr.appendChild(td);
      });
      body.appendChild(tr);
    });
    table.appendChild(body);
    enhanced.appendChild(toolbar);
    if (field.dataset.selectedDisplay === "chips") {
      enhanced.appendChild(chipRegion.region);
    }
    enhanced.appendChild(table);
    enhanced.appendChild(status);
    disableFallback(field);
    field.appendChild(enhanced);
    setSubmittedValues(field, selected);
    renderChips();
    updateStatus();
    return true;
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

  function detailsFor(option, detailsId, field) {
    var details = document.createElement("details");
    details.id = detailsId;
    details.className = "parameter-foundry-details";
    var summary = document.createElement("summary");
    summary.textContent = localized(field, "details", "Details");
    details.appendChild(summary);
    var machineValue = document.createElement("span");
    machineValue.textContent =
      localized(field, "machineValue", "Machine value: ") + option.value;
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

  function optionRow(field, option, selected, onChange, control) {
    var controlType = control && control.type ? control.type : "checkbox";
    var hasDetails =
      !option.pseudo &&
      (option.description || option.disabledReason || option.label !== option.value);
    var row = document.createElement("div");
    row.className = "parameter-foundry-option";
    row.setAttribute("aria-disabled", String(Boolean(option.disabled)));
    var label = document.createElement("label");
    var input = document.createElement("input");
    input.type = controlType;
    input.value = option.value;
    if (control && control.name) {
      input.name = control.name;
    }
    input.checked = selected.has(option.value);
    input.disabled = Boolean(option.disabled);
    var detailsId = uniqueId("up-option-details");
    if (hasDetails) {
      input.setAttribute("aria-describedby", detailsId);
    }
    input.addEventListener("change", function () {
      onChange(option.value, input.checked);
    });
    var copy = document.createElement("span");
    copy.className = "parameter-foundry-option-copy";
    var caption = document.createElement("strong");
    caption.textContent = option.label || option.value;
    copy.appendChild(caption);
    if (option.disabledReason) {
      var reason = document.createElement("small");
      reason.textContent = option.disabledReason;
      copy.appendChild(reason);
    }
    label.appendChild(input);
    label.appendChild(copy);
    row.appendChild(label);
    if (hasDetails) {
      row.appendChild(detailsFor(option, detailsId, field));
    }
    return row;
  }

  /**
   * Single-choice radio group for the enhanced path. The fallback markup already renders radios,
   * so Provider-backed and reactive parameters keep the renderer the definition asked for instead
   * of silently degrading to a select list.
   */
  function enhanceSingleRadio(field, options, initialValues) {
    var enhanced = document.createElement("div");
    enhanced.className = "parameter-foundry-enhanced parameter-foundry-radio";
    enhanced.dataset.upEnhanced = "true";
    enhanced.setAttribute("role", "radiogroup");
    enhanced.setAttribute(
      "aria-label",
      field.dataset.parameterName || "Select an option",
    );
    var status = createStatus();
    enhanced.setAttribute("aria-describedby", status.id);
    var groupName = uniqueId("up-radio-group");
    var selected = new Set(initialValues.slice(0, 1));
    var list = document.createElement("div");
    list.className = "parameter-foundry-options";

    function update(message) {
      setSubmittedValues(field, selected, true);
      status.textContent =
        (message ? message + " " : "") +
        selectedSummary(field, selected.size, options.length);
    }

    function select(value) {
      selected = new Set([value]);
      update(formatMessage(localized(field, "selectionUpdated", "Selection updated."), []));
    }

    if (field.dataset.required !== "true" && field.dataset.standardApiInputMode !== "extended-choice") {
      list.appendChild(
        optionRow(
          field,
          {
            value: "",
            label: localized(field, "noneOption", "None"),
            pseudo: true,
          },
          selected,
          select,
          {type: "radio", name: groupName},
        ),
      );
    }
    options.forEach(function (option) {
      list.appendChild(
        optionRow(field, option, selected, select, {
          type: "radio",
          name: groupName,
        }),
      );
    });
    enhanced.appendChild(list);
    enhanced.appendChild(status);
    disableFallback(field);
    field.appendChild(enhanced);
    update();
  }

  function gitSelectedOption(field, options) {
    var value = field.dataset.gitDefaultValue || "";
    if (field.dataset.gitSelectionMode === "default" && value.trim().length > 0) {
      return options.find(function (option) { return option.value === value; }) ||
        options.find(function (option) { return option.value.indexOf(value) !== -1; }) || options[0];
    }
    return options[0];
  }

  function enhanceSingle(field, options, initialValues) {
    var legacyGit = field.dataset.standardApiInputMode === "list-git-branches";
    var legacyExtended = field.dataset.standardApiInputMode === "extended-choice";
    var allowCustom = field.dataset.allowCustom === "true";
    var wantsRadio = field.dataset.renderer === "radio" && !allowCustom;
    if (wantsRadio && (legacyExtended || options.length <= MAX_RADIO_OPTIONS)) {
      enhanceSingleRadio(field, options, initialValues);
      return;
    }
    var enhanced = document.createElement("div");
    enhanced.className = "parameter-foundry-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(field, status.id);
    var select = document.createElement("select");
    select.className = "jenkins-select__input parameter-foundry-native-list";
    select.setAttribute(
      "aria-label",
      field.dataset.parameterName || "Select an option",
    );
    select.setAttribute("aria-describedby", status.id);
    var searchable =
      field.dataset.renderer === "searchable" ||
      wantsRadio ||
      (!legacyGit && !legacyExtended && options.length > MAX_RENDERED_OPTIONS) ||
      allowCustom;
    var customButton = null;
    var customValidation = null;
    if (searchable) {
      enhanced.appendChild(search);
    }
    if (wantsRadio) {
      // A radio group above the render limit degrades to the searchable list on purpose.
      var degraded = document.createElement("p");
      degraded.className = "parameter-foundry-notice";
      degraded.textContent = formatMessage(
        localized(
          field,
          "radioDegraded",
          "{0} options; using the searchable list instead of radio buttons.",
        ),
        [options.length],
      );
      enhanced.appendChild(degraded);
    }
    var selected = new Set(initialValues);

    function render() {
      var query = searchable ? search.value.toLocaleLowerCase() : "";
      var gitRegex;
      if (legacyGit) {
        try {
          gitRegex = new RegExp(searchable ? search.value.trim() : "", "i");
        } catch (_error) {
          status.textContent = "Invalid regular expression; keeping the previous selection.";
          return;
        }
      }
      var matches = options.filter(function (option) {
        return legacyGit ? gitRegex.test(option.label || option.value) : option.search.indexOf(query) !== -1;
      });
      if (legacyGit) {
        selected.clear();
        var gitSelected = gitSelectedOption(field, matches);
        if (gitSelected) selected.add(gitSelected.value);
      }
      removeChildren(select);
      if (field.dataset.required !== "true" && field.dataset.standardApiInputMode !== "list-git-branches" && field.dataset.standardApiInputMode !== "extended-choice") {
        var none = document.createElement("option");
        none.value = "";
        none.textContent = localized(field, "noneOption", "None");
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
          custom.textContent = formatMessage(localized(field, "customOption", "{0} (custom)"), [value]);
          custom.selected = true;
          select.appendChild(custom);
        }
      });
      (legacyGit || legacyExtended ? matches : matches.slice(0, MAX_RENDERED_OPTIONS)).forEach(function (option) {
        var element = document.createElement("option");
        element.value = option.value;
        element.textContent = option.label || option.value;
        element.disabled = option.disabled;
        element.title = option.disabledReason || option.description || "";
        element.selected = selected.has(option.value);
        select.appendChild(element);
      });
      if (searchable) {
        select.size = Math.min(8, Math.max(2, select.options.length));
      } else if (field.dataset.renderer === "list") {
        // A list box keeps the options visible, which is what the migrated node parameter wants.
        select.size = Math.min(12, Math.max(4, select.options.length));
      } else {
        // A plain select renderer stays a dropdown; only the searchable renderer lists matches.
        select.removeAttribute("size");
      }
      status.textContent =
        !legacyGit && !legacyExtended && matches.length > MAX_RENDERED_OPTIONS
          ? formatMessage(
              localized(field, "limitedSingleMatches", "{0} matches; showing {1}. Refine the search."),
              [matches.length, MAX_RENDERED_OPTIONS],
            )
          : formatMessage(localized(field, "matchesSummary", "{0} matching options."), [matches.length]);
      if (legacyGit) {
        setSubmittedValues(field, selected, true);
      }
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
          ? formatMessage(localized(field, "useCustomValue", 'Use custom value "{0}"'), [candidate])
          : localized(field, "enterCustomValue", "Enter a custom value");
        customButton.title = valid
          ? localized(field, "customValueHint", "Create a custom value distinct from the configured options.")
          : localized(field, "customValueInvalid", "The custom value does not satisfy the configured policy.");
        if (customValidation) {
          customValidation.textContent =
            candidate && !valid
              ? formatMessage(
                  localized(field, "customValueInvalidWithRule", "Value must match: {0}"),
                  [field.dataset.customPattern || localized(field, "customValuePolicy", "the configured policy")],
                )
              : "";
          customValidation.hidden = !candidate || valid;
        }
      }
    }

    if (searchable) {
      search.addEventListener("input", render);
      search.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && search.value) {
          search.value = "";
          render();
          event.preventDefault();
        } else if (event.key === "Enter") {
          // Never let Enter submit the build form: it either creates the custom value or
          // leaves the invalid candidate in place so the user can correct it.
          if (customButton && !customButton.disabled) {
            customButton.click();
          }
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
      var customAction = document.createElement("div");
      customAction.className = "parameter-foundry-custom-action";
      customButton = document.createElement("button");
      customButton.type = "button";
      customButton.className = "jenkins-button parameter-foundry-custom-value";
      customButton.setAttribute(
        "aria-label",
        localized(field, "useSearchAsCustomValue", "Use search text as a custom value"),
      );
      customButton.addEventListener("click", function () {
        if (customButton.disabled) {
          return;
        }
        selected.clear();
        selected.add(search.value);
        setSubmittedValues(field, selected, true);
        render();
        status.textContent = formatMessage(
          localized(field, "customValueSelected", 'Custom value "{0}" selected.'),
          [search.value],
        );
      });
      customValidation = document.createElement("div");
      customValidation.className = "parameter-foundry-custom-validation";
      customValidation.setAttribute("role", "alert");
      customValidation.hidden = true;
      customAction.appendChild(customButton);
      customAction.appendChild(customValidation);
      enhanced.appendChild(customAction);
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
    enhanced.className = "parameter-foundry-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(field, status.id);
    var select = document.createElement("select");
    select.multiple = true;
    select.className = "jenkins-select__input parameter-foundry-native-list";
    select.setAttribute(
      "aria-label",
      field.dataset.parameterName || "Select options",
    );
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
      select.size = Math.min(10, Math.max(1, select.options.length));
      status.textContent = selectedSummary(field, selected.size, options.length);
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
      status.textContent = selectedSummary(field, selected.size, options.length);
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
    if (field.dataset.renderer === "table" && enhanceTable(field, options, initialValues)) {
      return;
    }
    if (field.dataset.renderer === "list") {
      enhanceMultipleList(field, options, initialValues);
      return;
    }

    var selected = new Set(initialValues);
    var collapsedGroups = new Set();
    var enhanced = document.createElement("div");
    enhanced.className = "parameter-foundry-enhanced";
    enhanced.dataset.upEnhanced = "true";
    var status = createStatus();
    var search = createSearch(field, status.id);
    var chipRegion = chipsRegion(field);
    var chips = chipRegion.chips;
    var toolbar = document.createElement("div");
    toolbar.className = "parameter-foundry-toolbar";
    toolbar.setAttribute("aria-label", "Selection actions");
    var list = document.createElement("div");
    list.id = uniqueId("up-options");
    list.className = "parameter-foundry-options";
    list.setAttribute("aria-label", localized(field, "availableOptionsLabel", "Available options"));
    search.setAttribute("aria-controls", list.id);

    function filtered() {
      var query = search.value.toLocaleLowerCase();
      return options.filter(function (option) {
        return option.search.indexOf(query) !== -1;
      });
    }

    function renderChips() {
      removeChildren(chips);
      if (field.dataset.selectedDisplay !== "chips") {
        return;
      }
      sortedSetValues(selected).forEach(function (value) {
        var option = options.find(function (candidate) {
          return candidate.value === value;
        });
        var caption = option ? option.label : value;
        var chip = document.createElement("span");
        chip.className = "parameter-foundry-chip";
        chip.appendChild(document.createTextNode(caption));
        var remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "x";
        remove.setAttribute("aria-label", "Remove " + caption);
        remove.addEventListener("click", function () {
          selected.delete(value);
          sync(true, formatMessage(localized(field, "removed", "Removed {0}."), [caption]));
        });
        chip.appendChild(remove);
        chips.appendChild(chip);
      });
      syncChipRegion(field, chipRegion.region);
    }

    function updateStatus(message) {
      status.textContent =
        (message ? message + " " : "") +
        selectedSummary(field, selected.size, options.length);
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
      sync(
        true,
        formatMessage(localized(field, "selectionChanged", "{0} changed; {1} disabled skipped."), [
          affected,
          skipped,
        ]),
      );
    }

    function groupHeader(groupName, groupOptions) {
      var heading = document.createElement("div");
      heading.className = "parameter-foundry-group";
      var collapse = createToolbarButton(groupName || "Other", function () {
        if (collapsedGroups.has(groupName)) {
          collapsedGroups.delete(groupName);
        } else {
          collapsedGroups.add(groupName);
        }
        renderList();
      });
      collapse.className = "parameter-foundry-group-toggle";
      collapse.setAttribute("aria-expanded", String(!collapsedGroups.has(groupName)));
      var selectGroup = createToolbarButton(localized(field, "selectGroup", "Select group"), function () {
        changeSelection(groupOptions, "select", localized(field, "groupSelectionUpdated", "Group selection updated."));
      });
      selectGroup.className = "jenkins-button parameter-foundry-group-select";
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
        group.className = "parameter-foundry-group-section";
        group.setAttribute("aria-label", groupName || "Other options");
        group.appendChild(groupHeader(groupName, groupOptions));
        if (!collapsedGroups.has(groupName) || search.value) {
          groupOptions.forEach(function (option) {
            group.appendChild(
              optionRow(field, option, selected, function (value, checked) {
                if (checked) {
                  selected.add(value);
                } else {
                  selected.delete(value);
                }
                sync(false, localized(field, "selectionUpdated", "Selection updated."));
              }),
            );
          });
        }
        list.appendChild(group);
      });
      if (matches.length === 0) {
        var empty = document.createElement("p");
        empty.className = "parameter-foundry-empty";
        empty.textContent = localized(field, "noOptionsMatch", "No options match this search.");
        list.appendChild(empty);
      }
      status.textContent =
        matches.length > MAX_RENDERED_OPTIONS
          ? formatMessage(
              localized(
                field,
                "limitedMatches",
                "{0} matches; showing {1}. Refine the search. {2} selected.",
              ),
              [matches.length, MAX_RENDERED_OPTIONS, selected.size],
            )
          : selectedSummary(field, selected.size, options.length);
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
      createToolbarButton(localized(field, "selectVisible", "Select visible"), function () {
        changeSelection(
          filtered(),
          "select",
          localized(field, "visibleSelectionUpdated", "Visible selection updated."),
        );
      }),
    );
    toolbar.appendChild(
      createToolbarButton(localized(field, "invertVisible", "Invert visible"), function () {
        changeSelection(
          filtered(),
          "invert",
          localized(field, "visibleSelectionInverted", "Visible selection inverted."),
        );
      }),
    );
    toolbar.appendChild(
      createToolbarButton(localized(field, "clearVisible", "Clear visible"), function () {
        changeSelection(
          filtered(),
          "clear",
          localized(field, "visibleSelectionCleared", "Visible selection cleared."),
        );
      }),
    );
    toolbar.appendChild(
      createToolbarButton(localized(field, "clearAll", "Clear all"), function () {
        var affected = selected.size;
        selected.clear();
        sync(
          true,
          formatMessage(localized(field, "allSelectionsCleared", "All selections cleared. {0} changed."), [
            affected,
          ]),
        );
      }),
    );

    search.addEventListener("input", renderList);
    search.addEventListener("keydown", function (event) {
      if (event.key === "Backspace" && search.value === "" && selected.size > 0) {
        var values = sortedSetValues(selected);
        var removed = values[values.length - 1];
        selected.delete(removed);
        sync(true, formatMessage(localized(field, "removed", "Removed {0}."), [removed]));
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

    if (field.dataset.selectedDisplay === "chips") {
      enhanced.appendChild(chipRegion.region);
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

  function updateBuildAvailability(form) {
    if (!form) {
      return;
    }
    var providerFields = Array.prototype.slice.call(
      form.querySelectorAll('[data-parameter-foundry][data-provider-backed="true"]'),
    ).filter(function (field) {
      return field.dataset.standardApiInputMode !== "list-git-branches";
    });
    if (!providerFields.length) {
      return;
    }
    var blocked = providerFields.some(function (field) {
      return !["ready", "fallback", "empty"].includes(field.dataset.upState);
    });
    Array.prototype.forEach.call(
      form.querySelectorAll("button.jenkins-button--primary, input[type='submit']"),
      function (button) {
        if (!button.dataset.upProviderGate) {
          button.dataset.upProviderGate = "true";
          button.dataset.upProviderOriginalDisabled = String(button.disabled);
        }
        var originalDisabled = button.dataset.upProviderOriginalDisabled === "true";
        button.disabled = originalDisabled || blocked;
        if (blocked) {
          button.title = "Waiting for Provider options to finish loading.";
        } else if (!originalDisabled) {
          button.removeAttribute("title");
        }
      },
    );
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
    updateBuildAvailability(field.closest("form"));
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
        if (resolved.table) {
          // A Provider-supplied matrix replaces the definition copy for this render.
          field.dataset.upResolvedTable = JSON.stringify(resolved.table);
        } else {
          delete field.dataset.upResolvedTable;
        }
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
        if (field.dataset.allowCustom !== "true" && field.dataset.standardApiInputMode !== "list-git-branches") {
          initialValues = initialValues.filter(function (value) {
            return knownValues.has(value);
          });
        }
        if (initialValues.length === 0 && resolved.defaultValue != null) {
          initialValues = Array.isArray(resolved.defaultValue)
            ? resolved.defaultValue.slice()
            : [resolved.defaultValue];
        }
        if (field.dataset.standardApiInputMode === "list-git-branches" && !preserveSelection) {
          var gitSelected = gitSelectedOption(field, options);
          initialValues = gitSelected ? [gitSelected.value] : [];
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
          "[data-up-enhanced] .parameter-foundry-status",
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
        updateBuildAvailability(field.closest("form"));
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
        updateBuildAvailability(field.closest("form"));
        showRefreshStatus(
          field,
          preserveSelection
            ? "Options could not be refreshed; the visible selection is stale: " +
                localizedError(error)
            : "Options could not be enhanced: " + localizedError(error),
          "alert",
        );
      });
  }

  function scheduleReactiveRefresh(changedName) {
    Array.prototype.forEach.call(
      document.querySelectorAll("[data-parameter-foundry]"),
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
        showRefreshStatus(
          field,
          localizedError(new Error("Dependencies changed; waiting to refresh...")),
        );
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
      document.querySelectorAll("[data-parameter-foundry]"),
      function (field) {
        bridgeFallbackValues(field);
        updateBuildAvailability(field.closest("form"));
        var radioOverflow =
          field.dataset.renderer === "radio" &&
          Number(field.dataset.optionCount || "0") > MAX_RADIO_OPTIONS;
        if (
          field.dataset.renderer !== "radio" ||
          radioOverflow ||
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
