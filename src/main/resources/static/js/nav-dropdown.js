/*
 * Dropdown behaviour for the two nav menus (.nav-about and .nav-superadmin).
 *
 * The markup for each dropdown must be a wrapper div with one of those two
 * classes, containing exactly two direct children in this order:
 *   1. <a ... aria-haspopup="true" aria-expanded="false">   (the trigger)
 *   2. <ul class="nav-submenu">...</ul>                      (the menu)
 *
 * The script toggles the submenu by adding/removing state classes on the
 * wrapper: nav-about--open / nav-about--closed and
 * nav-superadmin--open / nav-superadmin--closed. The suffix is derived from
 * the wrapper's own base class so one code path serves both dropdowns.
 *
 * There is a THIRD state class, nav-about--current / nav-superadmin--current,
 * which nav.html renders SERVER-SIDE whenever the current page lives inside
 * that menu (/about, /team, /superadmin and /superadmin/**). school-template.css
 * force-opens the submenu from it, so on those pages the menu is visibly open
 * before any JS has run and with no --open class present. isMenuOpen() below
 * therefore treats --current as open, or the script would disagree with the
 * stylesheet: the trigger would take the "open" branch on a --current page and
 * change nothing visible, and the outside-click handler would have nothing to
 * dismiss. The CSS :hover reveal counts as open too, and --closed ties with
 * :hover on specificity and comes later in the file, so it wins here too.
 *
 * The parent anchor never navigates; its href stays for accessibility and
 * server-rendered tests. Clicking it always calls preventDefault() and toggles
 * the menu. Clicking a LINK INSIDE the submenu closes the menu but does NOT
 * call preventDefault(), so the link still navigates -- including to the page
 * it already points at, where the click would otherwise leave the menu sitting
 * * open. Escape closes the menu and returns focus to the trigger. A click
 * outside the wrapper, or focus moving completely outside it, also closes
 * whatever menu is open, --current ones included.
 *
 * HOVER: the reveal itself is CSS (:hover / :focus-within); the two hover
 * listeners only clear a stale --closed on the way in and close the menu on the
 * way out. Both are no-ops unless hasHoverPointer() confirms a real hover
 * pointer, because touch browsers emulate mouseenter/mouseleave around a tap
 * and the emulated mouseleave would otherwise hide the always-visible mobile
 * submenu. mouseleave never restores focus.
 *
 * CROSS-PAGE DISMISSAL:
 * A click on a submenu item that NAVIGATES must keep the menu closed on the
 * destination page, because nav.html re-renders --current there and the CSS
 * would re-open it. This is achieved by recording a dismissal flag in
 * sessionStorage at click time (markDismissed) and consuming it on the next
 * page load (consumeDismissed). The flag carries a timestamp; consumeDismissed
 * only honours it if the timestamp is FRESH (less than DISMISS_WINDOW_MS = 3000
 * ms old). The window prevents a stale flag from wrongly suppressing --current
 * on a page the user visits minutes later after a no-navigation click (e.g.
 * clicking "About Us" while already on /about). All sessionStorage access is
 * wrapped in try/catch: in privacy modes or when storage is disabled the
 * exception is caught and degraded silently -- the same-page close still works,
 * only the cross-page suppression is lost.
 */
(function () {
  'use strict';

  var WRAPPER_SELECTORS = ['.nav-about', '.nav-superadmin'];

  // Storage key for the cross-page dismissal flag.
  var DISMISSAL_KEY = 'sstsNavSubmenuDismissed';
  // Freshness window in milliseconds: only a flag younger than this is honoured.
  // 3000 ms covers the navigation round-trip but expires well before a user
  // could reach an unrelated page after a no-navigation click.
  var DISMISS_WINDOW_MS = 3000;

  function getBaseClass(wrapper) {
    if (wrapper.classList.contains('nav-about')) {
      return 'nav-about';
    }
    if (wrapper.classList.contains('nav-superadmin')) {
      return 'nav-superadmin';
    }
    return null;
  }

  /*
   * Mirrors the CSS precedence in school-template.css, and is the single
   * answer to "is this menu open right now?": the stylesheet force-opens a
   * --current menu, so the absence of --open does not mean closed.
   *   --closed, --open, :hover, and :focus-within all sit at (0,4,2);
   *   only --current is lower at (0,3,2).
   *   --current AND :hover both count as OPEN here, because both of them make
   *   the submenu visible in the stylesheet; a menu the user can see but this
   *   function reports as closed would make the trigger click do nothing
   *   visible and leave the outside-click/Escape handlers with nothing to
   *   dismiss. :focus-within stays uncounted -- it is transient and needs no
   *   click handler, unlike hover which the CSS opens on its own.
   *   --closed beats :hover, :focus-within and --open on SOURCE ORDER alone --
   *   it TIES with all of them at (0,4,2) and is the last block in the file --
   *   and beats --current outright at (0,4,2) vs (0,3,2). This is why the
   *   --closed rule must remain the LAST block in school-template.css, after
   *   every @media (max-width: 760px) block: moving it earlier lets
   *   :hover/:focus-within win again and the menu cannot be closed. That same
   *   ordering is why --closed short-circuits to false below, before :hover is
   *   even consulted.
   * matches() is guarded so an element without it (very old DOM shims) cannot
   * throw out of a click handler; treating hover as unknown degrades to the
   * pre-hover behaviour rather than breaking the menu.
   */
  function isMenuOpen(wrapper, base) {
    if (wrapper.classList.contains(base + '--closed')) {
      return false;
    }
    if (wrapper.classList.contains(base + '--open')) {
      return true;
    }
    if (wrapper.classList.contains(base + '--current')) {
      return true;
    }
    return isHovered(wrapper);
  }

  function isHovered(wrapper) {
    if (typeof wrapper.matches !== 'function') {
      return false;
    }
    try {
      return wrapper.matches(':hover');
    } catch (e) {
      return false;
    }
  }

  /*
   * True when the device has a real hover pointer, and therefore true (the
   * mouseenter/mouseleave behaviour is safe to run) unless the platform
   * positively reports otherwise. The two hover listeners are no-ops when this
   * is false, because on a touch device the browser emulates mouseenter and
   * mouseleave around a tap: the emulated mouseleave would add --closed, and
   * school-template.css's --closed override is (0,4,2) so it beats the mobile
   * always-visible rule (0,2,2) and would HIDE the submenu links entirely at
   * max-width: 760px, where the submenu is deliberately left inline and open.
   * matchMedia may be missing in an old/shimmed DOM, hence the try/catch: the
   * fallback is TRUE so an unavailable matchMedia preserves the old desktop
   * behaviour instead of silently disabling hover everywhere.
   * Queried inside the handlers, not cached at load, so a device that switches
   * input mode is picked up without needing a reload.
   */
  function hasHoverPointer() {
    try {
      if (!window.matchMedia) {
        return true;
      }
      return window.matchMedia('(hover: hover)').matches;
    } catch (e) {
      return true;
    }
  }

  /*
   * Only one menu may be visible at a time. Called at the START of openMenu so
   * the other dropdown is closed before the one being opened is touched, and
   * the pair can never both be open. It deliberately NEVER calls openMenu, so
   * there is no recursion, and it SKIPS the wrapper it was called for --
   * openMenu is about to open that one itself.
   * restoreFocus is false: the wrapper being opened immediately moves focus to
   * its own first submenu link, so re-focusing the OTHER menu's trigger would
   * steal keyboard focus straight back out of the menu the user just opened.
   * A wrapper with no trigger is skipped, because closeMenu needs one for the
   * aria-expanded write.
   */
  function closeOtherMenus(wrapper) {
    WRAPPER_SELECTORS.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (other) {
        if (other === wrapper) {
          return;
        }
        var otherBase = getBaseClass(other);
        if (!otherBase) {
          return;
        }
        var otherTrigger = other.querySelector(':scope > a');
        if (otherTrigger && isMenuOpen(other, otherBase)) {
          closeMenu(other, otherBase, otherTrigger, false);
        }
      });
    });
  }

  function openMenu(wrapper, base, trigger, submenu) {
    closeOtherMenus(wrapper);
    wrapper.classList.remove(base + '--closed');
    wrapper.classList.add(base + '--open');
    trigger.setAttribute('aria-expanded', 'true');
    var firstLink = submenu.querySelector('a');
    if (firstLink) {
      firstLink.focus();
    }
  }

  /*
   * restoreFocus is opt-in because only the Escape case wants it. The user has
   * just dismissed the menu from the keyboard there, so focus has to go
   * somewhere sensible and the trigger is it. For an outside click, a focusin
   * from elsewhere, or a click on a submenu link, focus is already somewhere
   * the user put it; moving it back to the trigger yanks it away from what
   * they just activated and can scroll the page.
   */
  function closeMenu(wrapper, base, trigger, restoreFocus) {
    wrapper.classList.remove(base + '--open');
    wrapper.classList.add(base + '--closed');
    trigger.setAttribute('aria-expanded', 'false');
    if (restoreFocus) {
      trigger.focus();
    }
  }

  function toggleMenu(wrapper, base, trigger, submenu) {
    if (isMenuOpen(wrapper, base)) {
      // The trigger is the element that was just activated, so re-focusing it
      // is a no-op rather than a focus steal.
      closeMenu(wrapper, base, trigger, true);
    } else {
      openMenu(wrapper, base, trigger, submenu);
    }
  }

  function closeAllMenus() {
    WRAPPER_SELECTORS.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (wrapper) {
        var base = getBaseClass(wrapper);
        if (!base) {
          return;
        }
        var trigger = wrapper.querySelector(':scope > a');
        if (trigger && isMenuOpen(wrapper, base)) {
          closeMenu(wrapper, base, trigger, false);
        }
      });
    });
  }

  /*
   * Walks up from the click target to find the anchor, stopping at the submenu
   * so a click on the <li> or the <ul> itself (or, defensively, an event that
   * did not originate inside this menu at all) yields null and is ignored. The
   * target may be the anchor itself or one of its descendants.
   */
  function findAnchorInSubmenu(target, submenu) {
    var node = target;
    while (node && node !== submenu) {
      if (node.tagName === 'A') {
        return node;
      }
      node = node.parentNode;
    }
    return null;
  }

  /*
   * Records a cross-page dismissal flag in sessionStorage with the current
   * timestamp. Called immediately before closeMenu() in the submenu click
   * handler so the flag is set even if the navigation is same-page (no load).
   * Any storage error is caught and ignored -- cross-page suppression is best
   * effort; same-page behaviour must not break.
   */
  function markDismissed() {
    try {
      sessionStorage.setItem(DISMISSAL_KEY, String(Date.now()));
    } catch (e) {
      // Storage unavailable (private mode, disabled, quota). Degrade silently.
    }
  }

  /*
   * Reads and REMOVES the dismissal flag. Returns true only if a value was
   * present, parseable as an integer, and FRESH (timestamp within
   * DISMISS_WINDOW_MS of now). The unconditional removal guarantees a stale
   * flag can never leak into a later page view. Any storage error is caught,
   * the key is treated as absent, and false is returned.
   */
  function consumeDismissed() {
    var raw;
    try {
      raw = sessionStorage.getItem(DISMISSAL_KEY);
      sessionStorage.removeItem(DISMISSAL_KEY);
    } catch (e) {
      // Storage unavailable. Treat as no flag.
      return false;
    }
    if (!raw) {
      return false;
    }
    var ts = parseInt(raw, 10);
    if (isNaN(ts)) {
      return false;
    }
    return (Date.now() - ts) < DISMISS_WINDOW_MS;
  }

  WRAPPER_SELECTORS.forEach(function (sel) {
    document.querySelectorAll(sel).forEach(function (wrapper) {
      var base = getBaseClass(wrapper);
      if (!base) {
        return;
      }
      var trigger = wrapper.querySelector(':scope > a');
      var submenu = wrapper.querySelector(':scope > ul.nav-submenu');
      if (!trigger || !submenu) {
        return;
      }

      trigger.addEventListener('click', function (e) {
        e.preventDefault();
        toggleMenu(wrapper, base, trigger, submenu);
      });

      submenu.addEventListener('click', function (e) {
        if (!findAnchorInSubmenu(e.target, submenu)) {
          return;
        }
        // No preventDefault(): the link must go where it points. Closing first,
        // synchronously, is what makes the "About Us while already on /about"
        // case work -- if the click causes no navigation there is nothing else
        // that would ever close the menu.
        // Record the dismissal BEFORE closing so a navigation carries it across
        // the page load. The flag is consumed on the next load by the init step
        // below and, if fresh, both menus are closed before any CSS --current
        // rule can re-open them.
        markDismissed();
        closeMenu(wrapper, base, trigger, false);
      });

      /*
       * Hover is CSS-driven: school-template.css reveals the submenu on :hover
       * and :focus-within, so there is deliberately nothing to OPEN here. The
       * only JS work on the way in is dropping a stale --closed left over from
       * an earlier click, otherwise the (0,4,2) --closed override -- which ties
       * with :hover and wins on source order -- would keep the menu shut under
       * the pointer. --open is NOT added and openMenu is NOT called: that would
       * focus the first submenu link on every mouse pass across the nav, which
       * is wrong for a pointer user.
       */
      wrapper.addEventListener('mouseenter', function () {
        if (!hasHoverPointer()) {
          return;
        }
        wrapper.classList.remove(base + '--closed');
      });

      /*
       * On the way out the menu is closed. restoreFocus is false: the pointer
       * leaving must never move keyboard focus. Guarded by isMenuOpen so a
       * mouseleave from a menu that was never opened cannot add a pointless
       * --closed -- and so the CSS :hover reveal, which is not a class and is
       * already gone by the time the pointer has left, is not fought with.
       * No delay or relatedTarget check is needed: the submenu is
       * position:absolute at top:100% with no gap and is a DOM descendant of
       * the wrapper, so travelling from the trigger down into it does not fire
       * mouseleave.
       */
      wrapper.addEventListener('mouseleave', function () {
        if (!hasHoverPointer()) {
          return;
        }
        if (isMenuOpen(wrapper, base)) {
          closeMenu(wrapper, base, trigger, false);
        }
      });

      wrapper.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' || e.key === 'Esc') {
          if (isMenuOpen(wrapper, base)) {
            e.preventDefault();
            closeMenu(wrapper, base, trigger, true);
          }
        }
      });
    });
  });

  /*
   * Cross-page dismissal init: consume the flag once on page load. If it is
   * present and fresh, close BOTH dropdowns by adding --closed and setting
   * aria-expanded to false. This runs AFTER the per-wrapper wiring above so
   * the click handlers are already registered even if storage throws (the
   * try/catch in consumeDismissed prevents that, but the order also ensures
   * the wiring is not dependent on the init step succeeding). No focus is
   * restored -- this is a fresh document load.
   */
  if (consumeDismissed()) {
    closeAllMenus();
  }

  document.addEventListener('click', function (e) {
    var target = e.target;
    var clickedInside = false;
    WRAPPER_SELECTORS.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (wrapper) {
        if (wrapper.contains(target)) {
          clickedInside = true;
        }
      });
    });
    if (!clickedInside) {
      closeAllMenus();
    }
  });

  document.addEventListener('focusin', function (e) {
    var target = e.target;
    var focusInside = false;
    WRAPPER_SELECTORS.forEach(function (sel) {
      document.querySelectorAll(sel).forEach(function (wrapper) {
        if (wrapper.contains(target)) {
          focusInside = true;
        }
      });
    });
    if (!focusInside) {
      closeAllMenus();
    }
  });
})();
