(function () {
  "use strict";

  var PREVIEW_LIMIT = 20;

  function localized(text, zh) {
    return (navigator.language || "").toLowerCase().indexOf("zh") === 0 ? zh : text;
  }

  function headers() {
    var result = {"Content-Type": "application/json"};
    var name = document.head.dataset.crumbHeader;
    var value = document.head.dataset.crumbValue;
    if (name && value) {
      result[name] = value;
    }
    return result;
  }

  function endpointFor(panel) {
    var form = panel.closest("form");
    if (form) {
      var action = new URL(form.action || window.location.href, window.location.href);
      if (/\/configSubmit\/?$/.test(action.pathname)) {
        action.pathname = action.pathname.replace(
          /\/configSubmit\/?$/,
          "/parameter-foundry/api/v1/import/preview",
        );
        action.search = "";
        return action;
      }
    }
    return panel.dataset.endpoint ? new URL(panel.dataset.endpoint, window.location.href) : null;
  }

  function field(chunk, name) {
    return chunk.querySelector(
      '[name="' + name + '"], [name$=".' + name + '"], [name$="[' + name + ']"]',
    );
  }

  function fieldValue(chunk, name) {
    var input = field(chunk, name);
    return input ? input.value || "" : "";
  }

  function optionFromChunk(chunk) {
    var disabled = field(chunk, "disabled");
    return {
      value: fieldValue(chunk, "value"),
      label: fieldValue(chunk, "label"),
      description: fieldValue(chunk, "description"),
      group: fieldValue(chunk, "group"),
      tags: fieldValue(chunk, "tagsText")
        .split(",")
        .map(function (tag) {
          return tag.trim();
        })
        .filter(Boolean),
      disabled: Boolean(disabled && disabled.checked),
      disabledReason: fieldValue(chunk, "disabledReason"),
    };
  }

  function editorFor(panel) {
    return panel.closest(".up-parameter-config").querySelector(".up-option-editor");
  }

  function currentOptions(panel) {
    return Array.prototype.map.call(
      editorFor(panel).querySelectorAll(".repeated-chunk:not(.to-be-removed)"),
      optionFromChunk,
    );
  }

  function removeExisting(editor) {
    Array.prototype.forEach.call(
      editor.querySelectorAll(".repeated-chunk:not(.to-be-removed) .repeatable-delete"),
      function (button) {
        button.click();
      },
    );
  }

  function fillChunk(chunk, option) {
    var fields = {
      value: option.value,
      label: option.label,
      description: option.description,
      group: option.group,
      tagsText: (option.tags || []).join(", "),
      disabledReason: option.disabledReason,
    };
    Object.keys(fields).forEach(function (name) {
      var input = field(chunk, name);
      if (input) {
        input.value = fields[name] || "";
        input.dispatchEvent(new Event("change", {bubbles: true}));
      }
    });
    var disabled = field(chunk, "disabled");
    if (disabled) {
      disabled.checked = Boolean(option.disabled);
      disabled.dispatchEvent(new Event("change", {bubbles: true}));
    }
  }

  function applyOptions(panel, options) {
    var container = editorFor(panel).querySelector(".repeated-container");
    removeExisting(container);
    options.forEach(function (option) {
      container.querySelector(".repeatable-add").click();
      var chunks = container.querySelectorAll(".repeated-chunk:not(.to-be-removed)");
      fillChunk(chunks[chunks.length - 1], option);
    });
  }

  function comparable(option) {
    return JSON.stringify({
      label: option.label || "",
      description: option.description || "",
      group: option.group || "",
      tags: option.tags || [],
      disabled: Boolean(option.disabled),
      disabledReason: option.disabledReason || "",
    });
  }

  function diff(existing, imported) {
    var before = new Map();
    var after = new Map();
    existing.forEach(function (option) {
      before.set(option.value, comparable(option));
    });
    imported.forEach(function (option) {
      after.set(option.value, comparable(option));
    });
    var added = 0;
    var removed = 0;
    var changed = 0;
    after.forEach(function (value, key) {
      if (!before.has(key)) {
        added += 1;
      } else if (before.get(key) !== value) {
        changed += 1;
      }
    });
    before.forEach(function (_value, key) {
      if (!after.has(key)) {
        removed += 1;
      }
    });
    return {added: added, removed: removed, changed: changed};
  }

  function quote(value) {
    return (
      "'" +
      String(value)
        .replace(/\\/g, "\\\\")
        .replace(/'/g, "\\'") +
      "'"
    );
  }

  function optionLiteral(option) {
    var properties = ["value: " + quote(option.value)];
    if (option.label && option.label !== option.value) {
      properties.push("label: " + quote(option.label));
    }
    if (option.group) {
      properties.push("group: " + quote(option.group));
    }
    if (option.disabled) {
      properties.push("disabled: true");
    }
    return "[" + properties.join(", ") + "]";
  }

  function configValue(panel, name, fallback) {
    var root = panel.closest(".up-parameter-config");
    var input = root.querySelector('[name="parameter.' + name + '"]');
    return input ? input.value : fallback;
  }

  function artifacts(panel, options) {
    var kind = panel.dataset.parameterKind;
    var name = configValue(panel, "name", kind === "multiple" ? "TARGETS" : "CHANNEL");
    var values = options
      .filter(function (option) {
        return !option.disabled;
      })
      .slice(0, kind === "multiple" ? 2 : 1)
      .map(function (option) {
        return option.value;
      })
      .sort();
    var shown = options.slice(0, PREVIEW_LIMIT).map(optionLiteral);
    var omitted = options.length - shown.length;
    var choices =
      "[" +
      shown.join(", ") +
      (omitted > 0 ? ", /* " + omitted + " more imported options */" : "") +
      "]";
    var pipeline =
      "foundryChoice(\n  name: " +
      quote(name) +
      (kind === "multiple" ? ",\n  selectionMode: 'multiple'" : "") +
      ",\n  choices: " +
      choices +
      "\n)";
    var typed = {values: {}};
    typed.values[name] = kind === "multiple" ? values : values[0] || null;
    var standard;
    if (kind === "multiple") {
      var mode = configValue(panel, "standardApiInputMode", "repeated");
      var delimiter = configValue(panel, "delimiter", ",");
      if (mode === "json-array") {
        standard = encodeURIComponent(name) + "=" + encodeURIComponent(JSON.stringify(values));
      } else if (mode === "delimited") {
        standard = encodeURIComponent(name) + "=" + encodeURIComponent(values.join(delimiter));
      } else {
        standard = values
          .map(function (value) {
            return encodeURIComponent(name) + "=" + encodeURIComponent(value);
          })
          .join("&");
      }
    } else {
      standard = encodeURIComponent(name) + "=" + encodeURIComponent(values[0] || "");
    }
    var output =
      kind === "multiple"
        ? values.join(configValue(panel, "delimiter", ","))
        : values[0] || "";
    return (
      "Pipeline\n" +
      pipeline +
      "\n\nStandard REST form body\n" +
      standard +
      "\n\nTyped REST body\n" +
      JSON.stringify(typed, null, 2) +
      "\n\nEnvironment export preview\n" +
      name +
      "=" +
      output
    );
  }

  function renderRows(container, options) {
    while (container.firstChild) {
      container.removeChild(container.firstChild);
    }
    var table = document.createElement("table");
    table.className = "jenkins-table up-import-table";
    var head = document.createElement("thead");
    var heading = document.createElement("tr");
    ["Value", "Label", "Group", "State"].forEach(function (caption) {
      var cell = document.createElement("th");
      cell.textContent = caption;
      heading.appendChild(cell);
    });
    head.appendChild(heading);
    table.appendChild(head);
    var body = document.createElement("tbody");
    options.slice(0, PREVIEW_LIMIT).forEach(function (option) {
      var row = document.createElement("tr");
      [
        option.value,
        option.label || option.value,
        option.group || "-",
        option.disabled ? "Disabled" : "Enabled",
      ].forEach(function (value) {
        var cell = document.createElement("td");
        cell.textContent = value;
        row.appendChild(cell);
      });
      body.appendChild(row);
    });
    table.appendChild(body);
    container.appendChild(table);
    if (options.length > PREVIEW_LIMIT) {
      var note = document.createElement("p");
      note.textContent =
        "Showing " + PREVIEW_LIMIT + " of " + options.length + " parsed options.";
      container.appendChild(note);
    }
  }

  function initialize(panel) {
    var textarea = panel.querySelector("textarea");
    var status = panel.querySelector("[data-up-import-status]");
    var rows = panel.querySelector("[data-up-import-rows]");
    var generated = panel.querySelector("[data-up-import-generated]");
    var previewButton = panel.querySelector("[data-up-import-preview-button]");
    var applyButton = panel.querySelector("[data-up-import-apply-button]");
    var imported = [];

    textarea.addEventListener("input", function () {
      imported = [];
      applyButton.disabled = true;
      status.textContent = localized(
        "Preview the edited input before applying it.",
        "应用前请先预览已编辑的输入。",
      );
    });

    previewButton.addEventListener("click", function () {
      var endpoint = endpointFor(panel);
      if (!endpoint) {
      status.textContent = localized(
        "Unable to determine the protected import preview endpoint.",
        "无法确定受保护的导入预览地址。",
      );
        return;
      }
      status.textContent = localized(
        "Parsing import on the Jenkins controller...",
        "正在 Jenkins 控制器上解析导入内容……",
      );
      applyButton.disabled = true;
      fetch(endpoint, {
        method: "POST",
        credentials: "same-origin",
        headers: headers(),
        body: JSON.stringify({content: textarea.value}),
      })
        .then(function (response) {
          return response.json().then(function (body) {
            if (!response.ok || !body.ok) {
              throw new Error(
                body.message ||
                  localized("Import preview failed.", "导入预览失败。"),
              );
            }
            imported = body.options;
            var changes = diff(currentOptions(panel), imported);
            status.textContent =
              body.count +
              " option(s) parsed. " +
              changes.added +
              " added, " +
              changes.changed +
              " changed, " +
              changes.removed +
              " removed.";
            renderRows(rows, imported);
            generated.textContent = artifacts(panel, imported);
            applyButton.disabled = false;
          });
        })
        .catch(function (error) {
          imported = [];
          status.textContent = error.message;
          while (rows.firstChild) {
            rows.removeChild(rows.firstChild);
          }
          generated.textContent = "";
        });
    });
    applyButton.addEventListener("click", function () {
      applyOptions(panel, imported);
      status.textContent =
        imported.length + " option(s) applied. Save the Job to persist this change.";
      applyButton.disabled = true;
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    Array.prototype.forEach.call(
      document.querySelectorAll("[data-up-import-panel]"),
      initialize,
    );
  });
})();
