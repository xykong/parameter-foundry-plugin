(function () {
  "use strict";

  function enhance() {
    document
      .querySelectorAll(
        '[data-unified-parameter="true"][data-renderer="accessible-choice-grid"]',
      )
      .forEach(function (field) {
        if (field.dataset.upAccessibleGrid === "true") {
          return;
        }
        field.dataset.upAccessibleGrid = "true";
        field.classList.add("up-accessible-choice-grid");
        field
          .querySelectorAll(".unified-parameters-options")
          .forEach(function (options) {
            options.classList.add("up-accessible-choice-grid__options");
          });
        field
          .querySelectorAll(".unified-parameters-native-list")
          .forEach(function (select) {
            select.classList.add("up-accessible-choice-grid__select");
          });
      });
  }

  document.addEventListener("DOMContentLoaded", function () {
    window.setTimeout(enhance, 0);
  });
})();
