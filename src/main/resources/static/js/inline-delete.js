/*
 * Confirmation prompt for the destructive delete buttons on the super-admin
 * list screens (calendar, team, gallery, announcements).
 *
 * ONE copy of this logic for all four modules. It used to be inlined in each
 * list template, and the copies had already diverged: the calendar template
 * passed a per-row message via data-confirm while the other three hardcoded a
 * different static string each.
 *
 * Usage -- on any delete form, supply the message as data-confirm:
 *   <form th:action="..." method="post" class="inline-delete"
 *         th:data-confirm="|Delete \"${event.title}\"?|">
 *     <button type="submit" class="delete-button">Delete</button>
 *   </form>
 *
 * The CSRF token is injected by Thymeleaf because the form uses th:action;
 * do not switch it to a plain action attribute.
 */
(function () {
  'use strict';

  var FALLBACK = 'Delete this item? This cannot be undone.';

  document.querySelectorAll('form.inline-delete').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      if (!window.confirm(form.dataset.confirm || FALLBACK)) {
        e.preventDefault();
      }
    });
  });
})();
