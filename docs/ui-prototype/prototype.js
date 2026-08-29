const options = [
  {
    value: "android",
    label: "Android",
    description: "Google Play client build",
    group: "Mobile",
    tags: ["mobile", "release"],
    selected: true,
    disabled: false,
  },
  {
    value: "ios",
    label: "iOS",
    description: "App Store client build",
    group: "Mobile",
    tags: ["mobile", "release"],
    selected: true,
    disabled: false,
  },
  {
    value: "ohos",
    label: "HarmonyOS",
    description: "HarmonyOS application package",
    group: "Mobile",
    tags: ["mobile"],
    selected: false,
    disabled: false,
  },
  {
    value: "legacy-mobile",
    label: "Legacy Mobile",
    description: "Retired build chain",
    group: "Mobile",
    tags: ["retired"],
    selected: false,
    disabled: true,
    disabledReason: "Toolchain retired",
  },
  {
    value: "web",
    label: "Web",
    description: "Browser application bundle",
    group: "Browser",
    tags: ["browser", "release"],
    selected: false,
    disabled: false,
  },
  {
    value: "admin",
    label: "Admin Console",
    description: "Internal administration UI",
    group: "Browser",
    tags: ["browser", "internal"],
    selected: false,
    disabled: false,
  },
];

const selected = new Set(options.filter((option) => option.selected).map((option) => option.value));
const groupsElement = document.querySelector("#option-groups");
const chipsElement = document.querySelector("#selected-chips");
const countElement = document.querySelector("#selection-count");
const searchElement = document.querySelector("#option-search");
const errorElement = document.querySelector("#target-error");
const jsonElement = document.querySelector("#json-preview");
const envElement = document.querySelector("#env-preview");
const stateElement = document.querySelector("#state-select");
const optionStateElement = document.querySelector("#option-state");
const resultElement = document.querySelector("#build-result");
const formElement = document.querySelector("#build-form");

let previewState = "ready";

function visibleOptions() {
  const query = searchElement.value.trim().toLocaleLowerCase();
  if (!query) {
    return options;
  }
  return options.filter((option) => {
    const searchable = [
      option.value,
      option.label,
      option.description,
      option.group,
      ...option.tags,
    ].join(" ").toLocaleLowerCase();
    return searchable.includes(query);
  });
}

function canonicalValues() {
  return options
    .filter((option) => selected.has(option.value))
    .map((option) => option.value)
    .sort();
}

function renderGroups() {
  const visible = visibleOptions();
  const grouped = new Map();
  for (const option of visible) {
    const group = grouped.get(option.group) ?? [];
    group.push(option);
    grouped.set(option.group, group);
  }
  groupsElement.replaceChildren();

  for (const [group, groupOptions] of grouped) {
    const section = document.createElement("section");
    section.className = "option-group";

    const heading = document.createElement("div");
    heading.className = "group-heading";
    const title = document.createElement("strong");
    title.textContent = group;
    const count = document.createElement("span");
    count.textContent = `${groupOptions.length} options`;
    heading.append(title, count);

    const list = document.createElement("div");
    list.className = "option-list";

    for (const option of groupOptions) {
      const row = document.createElement("label");
      row.className = `option-row${option.disabled ? " disabled" : ""}`;

      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.value = option.value;
      checkbox.checked = selected.has(option.value);
      checkbox.disabled = option.disabled || previewState !== "ready";
      checkbox.addEventListener("change", () => {
        if (checkbox.checked) {
          selected.add(option.value);
        } else {
          selected.delete(option.value);
        }
        update();
      });

      const copy = document.createElement("span");
      const label = document.createElement("strong");
      label.textContent = option.label;
      const description = document.createElement("small");
      description.textContent = option.disabled
        ? `${option.description}. Disabled: ${option.disabledReason}.`
        : option.description;
      const value = document.createElement("span");
      value.className = "machine-value";
      value.textContent = option.value;
      copy.append(label, description, value);
      row.append(checkbox, copy);
      list.append(row);
    }

    section.append(heading, list);
    groupsElement.append(section);
  }
}

function renderChips() {
  chipsElement.replaceChildren();
  for (const value of canonicalValues()) {
    const option = options.find((candidate) => candidate.value === value);
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.append(document.createTextNode(option.label));

    const remove = document.createElement("button");
    remove.type = "button";
    remove.textContent = "×";
    remove.setAttribute("aria-label", `Remove ${option.label}`);
    remove.disabled = previewState !== "ready";
    remove.addEventListener("click", () => {
      selected.delete(value);
      update();
    });
    chip.append(remove);
    chipsElement.append(chip);
  }
}

function renderPreview() {
  const channel = formElement.elements.channel.value;
  const values = canonicalValues();
  jsonElement.textContent = JSON.stringify({
    CHANNEL: channel,
    TARGETS: values,
  }, null, 2);
  envElement.textContent = `CHANNEL=${channel}\nTARGETS=${values.join(",")}`;
  countElement.textContent = `${values.length} selected`;
}

function renderState() {
  const messages = {
    ready: "",
    empty: "Provider resolved successfully but returned no options. No build can be scheduled while TARGETS is required.",
    error: "Provider could not be resolved. Retry after checking the governed Provider health page.",
    config: "Configuration error: the explicit default references a disabled or missing machine value.",
  };
  optionStateElement.className = "option-state";
  if (previewState !== "ready") {
    optionStateElement.classList.add("visible", previewState);
    optionStateElement.textContent = messages[previewState];
  } else {
    optionStateElement.textContent = "";
  }
  groupsElement.hidden = previewState !== "ready";
  chipsElement.hidden = previewState !== "ready";
  document.querySelectorAll(".batch-actions button").forEach((button) => {
    button.disabled = previewState !== "ready";
  });
}

function validate() {
  if (previewState !== "ready") {
    errorElement.textContent = "Resolve the current option state before submitting.";
    return false;
  }
  if (selected.size === 0) {
    errorElement.textContent = "Select at least one target. [UNIFIED_PARAMETER_MIN_SELECTIONS]";
    return false;
  }
  errorElement.textContent = "";
  return true;
}

function update() {
  renderState();
  renderGroups();
  renderChips();
  renderPreview();
  resultElement.textContent = "";
  if (selected.size > 0 && previewState === "ready") {
    errorElement.textContent = "";
  }
}

searchElement.addEventListener("input", renderGroups);

document.querySelector(".batch-actions").addEventListener("click", (event) => {
  const action = event.target.dataset.action;
  if (!action || previewState !== "ready") {
    return;
  }
  const visibleEnabled = visibleOptions().filter((option) => !option.disabled);
  if (action === "select") {
    visibleEnabled.forEach((option) => selected.add(option.value));
  } else if (action === "clear") {
    visibleEnabled.forEach((option) => selected.delete(option.value));
  } else if (action === "clear-all") {
    selected.clear();
  } else if (action === "invert") {
    visibleEnabled.forEach((option) => {
      if (selected.has(option.value)) {
        selected.delete(option.value);
      } else {
        selected.add(option.value);
      }
    });
  }
  update();
});

stateElement.addEventListener("change", () => {
  previewState = stateElement.value;
  update();
});

formElement.addEventListener("change", (event) => {
  if (event.target.name === "channel") {
    renderPreview();
  }
});

formElement.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!validate()) {
    errorElement.focus();
    return;
  }
  resultElement.textContent = "Validated. A real plugin would now schedule the build and return a queue item.";
});

const viewButtons = document.querySelectorAll("[data-view]");
viewButtons.forEach((button) => {
  button.addEventListener("click", () => {
    viewButtons.forEach((candidate) => {
      const active = candidate === button;
      candidate.classList.toggle("active", active);
      candidate.setAttribute("aria-selected", String(active));
      document.querySelector(`#${candidate.dataset.view}`).hidden = !active;
    });
  });
});

const configForm = document.querySelector("#config-form");
const configSnippet = document.querySelector("#config-snippet");
const configValidation = document.querySelector("#config-validation");
const configCompatibility = document.querySelector("#config-compatibility");

function groovyQuote(value) {
  return `'${value.replaceAll("\\", "\\\\").replaceAll("'", "\\'")}'`;
}

function configOptions() {
  return [...document.querySelectorAll(".config-option")].map((row) => {
    const fields = row.querySelectorAll("input");
    return {
      value: fields[0].value,
      label: fields[1].value,
      group: fields[2].value,
      selected: fields[3].checked,
      disabled: fields[4].checked,
    };
  });
}

function renderConfigPreview() {
  const name = document.querySelector("#config-name").value.trim();
  const renderer = document.querySelector("#config-renderer").value;
  const defaultMode = document.querySelector("#config-default-mode").value;
  const minimum = Number(document.querySelector("#config-min").value);
  const apiMode = document.querySelector("#config-api-mode").value;
  const exportMode = document.querySelector("#config-export").value;
  const configuredOptions = configOptions();
  const values = configuredOptions.map((option) => option.value);
  const duplicate = values.find((value, index) => values.indexOf(value) !== index);
  const invalidDefault = configuredOptions.find((option) => option.selected && option.disabled);
  const invalidName = !/^[A-Za-z_][A-Za-z0-9_]*$/.test(name);

  if (invalidName) {
    configValidation.textContent = "Parameter name must be a stable environment-compatible identifier.";
    configValidation.classList.add("invalid");
  } else if (duplicate) {
    configValidation.textContent = `Duplicate machine value: ${duplicate}`;
    configValidation.classList.add("invalid");
  } else if (invalidDefault) {
    configValidation.textContent = `Disabled option cannot be a default: ${invalidDefault.value}`;
    configValidation.classList.add("invalid");
  } else {
    configValidation.textContent = "Configuration is valid. Machine values are unique.";
    configValidation.classList.remove("invalid");
  }

  const optionLines = configuredOptions.map((option) => {
    const attributes = [
      `value: ${groovyQuote(option.value)}`,
      `label: ${groovyQuote(option.label)}`,
      `group: ${groovyQuote(option.group)}`,
    ];
    if (option.disabled) {
      attributes.push("disabled: true");
    }
    return `        [${attributes.join(", ")}]`;
  });
  const defaultValues = configuredOptions
    .filter((option) => option.selected)
    .map((option) => option.value)
    .sort()
    .map(groovyQuote);

  const lines = [
    "unifiedMultiChoice(",
    `    name: ${groovyQuote(name)},`,
    "    choices: [",
    optionLines.join(",\n"),
    "    ],",
    `    defaultMode: ${groovyQuote(defaultMode)},`,
  ];
  if (defaultMode === "explicit") {
    lines.push(`    defaultValues: [${defaultValues.join(", ")}],`);
  }
  lines.push(
    `    renderer: ${groovyQuote(renderer)},`,
    `    minSelections: ${minimum},`,
    `    standardApiInputMode: ${groovyQuote(apiMode)},`,
    `    environmentExport: [mode: ${groovyQuote(exportMode)}]`,
    ")",
  );
  configSnippet.textContent = lines.join("\n");

  const apiLabels = {
    repeated: "Repeated form fields",
    "json-array": "One JSON Array field",
    delimited: "Delimited compatibility field",
  };
  configCompatibility.textContent = apiLabels[apiMode];
}

configForm.addEventListener("input", renderConfigPreview);
configForm.addEventListener("change", renderConfigPreview);

update();
renderConfigPreview();
