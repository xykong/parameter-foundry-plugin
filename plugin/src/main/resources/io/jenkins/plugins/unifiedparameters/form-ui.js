(function () {
  "use strict";

  function create(tag, className, text) {
    var element = document.createElement(tag);
    if (className) {
      element.className = className;
    }
    if (text != null) {
      element.textContent = text;
    }
    return element;
  }

  function parseJson(value, fallback) {
    try {
      return JSON.parse(value);
    } catch (_error) {
      return fallback;
    }
  }

  function message(form, name, fallback) {
    return form.dataset["up" + name.charAt(0).toUpperCase() + name.slice(1)] || fallback;
  }

  function initialValue(field) {
    if (field.defaultValue == null) {
      if (field.type === "boolean") {
        return false;
      }
      if (field.type === "array" || field.type === "multi-choice") {
        return [];
      }
      if (field.type === "object") {
        return {};
      }
      return null;
    }
    if (
      field.type === "object" ||
      field.type === "array" ||
      field.type === "multi-choice"
    ) {
      return parseJson(field.defaultValue, field.type === "object" ? {} : []);
    }
    if (field.type === "boolean") {
      return field.defaultValue === "true";
    }
    if (field.type === "integer" || field.type === "decimal") {
      return Number(field.defaultValue);
    }
    return field.defaultValue;
  }

  function enhance(form) {
    var schemaSource = form.querySelector("[data-up-form-schema]");
    var fallback = form.querySelector("[data-up-form-fallback]");
    var target = form.querySelector("[data-up-form-enhanced]");
    var status = form.querySelector(".up-form-status");
    var schema = parseJson(schemaSource.value, null);
    if (!schema || !Array.isArray(schema.fields)) {
      status.textContent = message(form, "schemaLoadError", "The form schema could not be loaded.");
      status.setAttribute("role", "alert");
      return;
    }
    var value = parseJson(fallback.querySelector("textarea").value, {});
    var controls = new Map();

    function synchronize() {
      var encoded = JSON.stringify(value);
      var hidden = form.querySelector("[data-up-form-value]");
      if (!hidden) {
        hidden = document.createElement("input");
        hidden.type = "hidden";
        hidden.name = "value";
        hidden.dataset.upFormValue = "true";
        form.appendChild(hidden);
      }
      hidden.value = encoded;
      status.textContent = message(form, "valueSynchronized", "Form value is synchronized as canonical JSON.");
      applyConditions();
    }

    function fieldControl(field) {
      var wrapper = create("div", "up-form-field");
      wrapper.dataset.upFormField = field.name;
      var label = create("label", null, field.label || field.name);
      wrapper.appendChild(label);
      var control;
      var current = Object.prototype.hasOwnProperty.call(value, field.name)
        ? value[field.name]
        : initialValue(field);
      value[field.name] = current;

      if (field.type === "boolean") {
        control = document.createElement("input");
        control.type = "checkbox";
        control.checked = Boolean(current);
        control.addEventListener("change", function () {
          value[field.name] = control.checked;
          synchronize();
        });
      } else if (field.type === "choice" || field.type === "multi-choice") {
        control = document.createElement("select");
        control.className = "jenkins-select__input";
        control.multiple = field.type === "multi-choice";
        (field.choices || []).forEach(function (option) {
          var item = document.createElement("option");
          item.value = option.value;
          item.textContent = option.label || option.value;
          item.disabled = Boolean(option.disabled);
          item.selected = control.multiple
            ? Array.isArray(current) && current.indexOf(option.value) !== -1
            : current === option.value;
          control.appendChild(item);
        });
        control.addEventListener("change", function () {
          value[field.name] = control.multiple
            ? Array.prototype.filter
                .call(control.options, function (option) {
                  return option.selected;
                })
                .map(function (option) {
                  return option.value;
                })
            : control.value;
          synchronize();
        });
      } else if (field.type === "content") {
        control = create("div", "up-form-content", current || "");
      } else if (field.type === "object" || field.type === "array") {
        control = document.createElement("textarea");
        control.className = "jenkins-input";
        control.rows = 5;
        control.value = JSON.stringify(
          current == null ? (field.type === "object" ? {} : []) : current,
          null,
          2,
        );
        control.addEventListener("input", function () {
          var decoded = parseJson(control.value, undefined);
          if (decoded !== undefined) {
            value[field.name] = decoded;
            control.removeAttribute("aria-invalid");
            synchronize();
          } else {
            control.setAttribute("aria-invalid", "true");
            status.textContent = message(form, "nestedJsonInvalid", "Nested JSON is invalid for {0}.").replace("{0}", field.name);
          }
        });
      } else {
        control = document.createElement(
          field.type === "text" ? "textarea" : "input",
        );
        control.className = "jenkins-input";
        if (field.type === "integer" || field.type === "decimal") {
          control.type = "number";
          if (field.minimum != null) control.min = field.minimum;
          if (field.maximum != null) control.max = field.maximum;
          control.step = field.type === "integer" ? "1" : "any";
        } else if (field.type === "date" || field.type === "time") {
          control.type = field.type;
        } else if (field.secret) {
          control.type = "password";
        } else if (control.tagName === "INPUT") {
          control.type = "text";
        }
        control.value = current == null ? "" : current;
        control.required = Boolean(field.required);
        if (field.minLength >= 0) control.minLength = field.minLength;
        if (field.maxLength >= 0) control.maxLength = field.maxLength;
        if (field.pattern) control.pattern = field.pattern;
        control.addEventListener("input", function () {
          if (field.type === "integer") {
            value[field.name] = control.value === "" ? null : Number.parseInt(control.value, 10);
          } else if (field.type === "decimal") {
            value[field.name] = control.value === "" ? null : Number(control.value);
          } else {
            value[field.name] = control.value || null;
          }
          synchronize();
        });
      }
      control.setAttribute("aria-label", field.label || field.name);
      wrapper.appendChild(control);
      if (field.description) {
        wrapper.appendChild(create("small", null, field.description));
      }
      controls.set(field.name, wrapper);
      return wrapper;
    }

    function layoutNode(layout) {
      var element =
        layout.type === "collapsible" || layout.type === "tabs"
          ? create("details", "up-form-layout up-form-" + layout.type)
          : create("section", "up-form-layout up-form-" + layout.type);
      element.dataset.upFormLayout = layout.id;
      if (layout.visibleWhenField) {
        element.dataset.visibleWhenField = layout.visibleWhenField;
        element.dataset.visibleWhenValue = layout.visibleWhenValue || "";
      }
      if (element.tagName === "DETAILS") {
        element.open = !layout.collapsed;
        element.appendChild(create("summary", null, layout.title || layout.id));
      } else if (layout.title) {
        element.appendChild(create("h3", null, layout.title));
      }
      var body = create(
        "div",
        layout.type === "grid" ? "up-form-fields up-form-grid" : "up-form-fields",
      );
      if (layout.type === "grid") {
        body.style.setProperty("--up-form-columns", String(layout.columns || 1));
      }
      (layout.fieldNames || []).forEach(function (name) {
        if (controls.has(name)) {
          body.appendChild(controls.get(name));
        }
      });
      (layout.children || []).forEach(function (child) {
        body.appendChild(layoutNode(child));
      });
      element.appendChild(body);
      return element;
    }

    function applyConditions() {
      target.querySelectorAll("[data-visible-when-field]").forEach(function (layout) {
        var actual = value[layout.dataset.visibleWhenField];
        layout.hidden = String(actual == null ? "" : actual) !== layout.dataset.visibleWhenValue;
      });
    }

    schema.fields.forEach(fieldControl);
    if (schema.layouts && schema.layouts.length) {
      schema.layouts.forEach(function (layout) {
        target.appendChild(layoutNode(layout));
      });
    }
    var unplaced = create("div", "up-form-fields");
    controls.forEach(function (control) {
      if (!control.parentElement) {
        unplaced.appendChild(control);
      }
    });
    if (unplaced.children.length) {
      target.appendChild(unplaced);
    }
    fallback.hidden = true;
    fallback.querySelector("textarea").disabled = true;
    fallback.querySelector("textarea").removeAttribute("name");
    synchronize();
    form.dataset.upFormInteractive = "true";
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-up-form]").forEach(enhance);
  });
})();
