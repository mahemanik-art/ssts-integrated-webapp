/*
 * User form: phone and postal address are NOT stored for a parent account,
 * because ssts_families is the authoritative source for a parent's contact
 * details (it holds both parents). UserAdminService enforces the rule on the
 * server; this only makes the form honest about it, so an admin changing the
 * user type to 'parent' sees the fields grey out instead of silently losing
 * what they typed.
 *
 * The server is the enforcement point -- this is presentational only.
 * House style: IIFE, 'use strict', plain DOM, no framework, no build step.
 */
(function () {
  'use strict';

  var PARENT = 'parent';

  function ready(fn) {
    if (document.readyState !== 'loading') {
      fn();
    } else {
      document.addEventListener('DOMContentLoaded', fn);
    }
  }

  ready(function () {
    var typeSelect = document.getElementById('userType');
    if (!typeSelect) return;

    var staffOnly = document.querySelectorAll('[data-staff-contact]');
    var hints = document.querySelectorAll('.form-hint-staff');
    var parentHints = document.querySelectorAll('.form-hint-parent');
    if (!staffOnly.length) return;

    function isParent() {
      return typeSelect.value === PARENT;
    }

    function sync() {
      var parent = isParent();
      var i;
      for (i = 0; i < staffOnly.length; i += 1) {
        staffOnly[i].hidden = parent;
      }
      for (i = 0; i < hints.length; i += 1) {
        hints[i].hidden = parent;
      }
      for (i = 0; i < parentHints.length; i += 1) {
        parentHints[i].hidden = !parent;
      }
    }

    typeSelect.addEventListener('change', sync);
    sync();
  });
})();
