(function () {
  "use strict";

  function enhance() {
    document
      .querySelectorAll(
        '[data-parameter-foundry="true"][data-renderer="accessible-choice-grid"]',
      )
      .forEach(function (field) {
        if (field.dataset.upAccessibleGrid === "true") {
          return;
        }
        field.dataset.upAccessibleGrid = "true";
        field.classList.add("up-accessible-choice-grid");
        field
          .querySelectorAll(".parameter-foundry-options")
          .forEach(function (options) {
            options.classList.add("up-accessible-choice-grid__options");
          });
        field
          .querySelectorAll(".parameter-foundry-native-list")
          .forEach(function (select) {
            select.classList.add("up-accessible-choice-grid__select");
          });
      });
  }

  document.addEventListener("DOMContentLoaded", function () {
    window.setTimeout(enhance, 0);
  });
})();
