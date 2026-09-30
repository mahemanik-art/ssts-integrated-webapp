/**
 * Public donor carousel: a three-card window that cycles through every donor.
 *
 * <p>The cards are rendered server-side from ssts_donors; this script only swaps
 * their CONTENTS as the window advances. There are no donor objects hardcoded
 * here -- an earlier version kept nine of them in this file alongside dead
 * example.com links, which meant the public carousel could not be edited without
 * a code change and a deploy.
 *
 * <p>Why the content is rebuilt rather than swapped:
 *
 * <p><b>A donor may have no logo.</b> The previous implementation assumed every
 * card contained an <img> and did card.querySelector('.donor-photo').src = ...,
 * which THROWS for a donor with no photo. Here each card is rebuilt with
 * createElement + textContent, so a missing logo becomes an initials tile and a
 * missing tagline or website simply omits that row.
 *
 * <p><b>textContent, never innerHTML.</b> Donor names and taglines are
 * school-supplied text. Building the nodes programmatically means a value
 * containing markup or a quote is displayed as text and can never execute.
 *
 * <p>Behaviour notes:
 * <ul>
 *   <li>Autoplay advances one donor every ROTATE_MS, matching the hero slider's
 *       8s rhythm that this section originally shared.</li>
 *   <li>Autoplay PAUSES on hover and on keyboard focus. A rotating panel that
 *       moves while someone is reading or about to click its link is hostile,
 *       and it also made the "Visit website" link effectively unclickable.</li>
 *   <li>prefers-reduced-motion disables autoplay entirely. The manual arrows
 *       still work.</li>
 *   <li>Everything is wrapped in try/catch. This page is public; a failure here
 *       must never take the rest of the page's scripts down with it.</li>
 * </ul>
 */
(function () {
  'use strict';

  var ROTATE_MS = 8000;
  var FADE_MS = 350;
  var VISIBLE = 3;

  function ready(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  ready(function () {
    try {
      init();
    } catch (err) {
      // A public page must keep working even if the carousel cannot start; the
      // three server-rendered cards are already in the DOM.
      if (window.console && window.console.warn) {
        window.console.warn('Donor carousel disabled:', err);
      }
    }
  });

  function init() {
    var wrap = document.getElementById('donorCards');
    if (!wrap) return;

    var donors = window.__SSTS_DONORS;
    if (!donors || !donors.length) return;

    var cards = wrap.querySelectorAll('.donor-card');
    if (!cards.length) return;

    // Nothing to cycle through: three or fewer donors already all fit.
    if (donors.length <= VISIBLE) return;

    var dotsWrap = wrap.querySelector('[data-donor-dots]');
    var prevBtn = wrap.querySelector('[data-donor-prev]');
    var nextBtn = wrap.querySelector('[data-donor-next]');
    var steps = donors.length - VISIBLE + 1;
    var index = 0;
    var timer = null;
    var paused = false;

    var reduceMotion =
      window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    buildDots();

    render(0, false);

    if (!reduceMotion) start();

    if (prevBtn) {
      prevBtn.addEventListener('click', function () {
        render(index - 1, true);
        restart();
      });
    }
    if (nextBtn) {
      nextBtn.addEventListener('click', function () {
        render(index + 1, true);
        restart();
      });
    }

    // Pause while the pointer is over the panel or a control inside it has
    // focus, so a donor's link is actually clickable.
    wrap.addEventListener('mouseenter', function () {
      paused = true;
      stop();
    });
    wrap.addEventListener('mouseleave', function () {
      paused = false;
      if (!reduceMotion) start();
    });
    wrap.addEventListener('focusin', function () {
      paused = true;
      stop();
    });
    wrap.addEventListener('focusout', function (event) {
      // Only resume once focus has left the panel entirely, not when it moves
      // between the two arrow buttons inside it.
      if (wrap.contains(event.relatedTarget)) return;
      paused = false;
      if (!reduceMotion) start();
    });

    // Keyboard support for the panel itself.
    wrap.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowRight') {
        render(index + 1, true);
        restart();
      } else if (event.key === 'ArrowLeft') {
        render(index - 1, true);
        restart();
      }
    });

    // The hero slider calls this so that interacting with one carousel does not
    // leave the other frozen mid-rotation. Restored with the rotation.
    window.__syncDonorTimer = restart;

    function buildDots() {
      if (!dotsWrap) return;
      dotsWrap.textContent = '';
      for (var n = 0; n < steps; n++) {
        (function (n) {
          var dot = document.createElement('button');
          dot.type = 'button';
          dot.className = 'donor-dot';
          dot.setAttribute('aria-label', 'Show donors starting at ' + (n + 1));
          dot.addEventListener('click', function () {
            render(n, true);
            restart();
          });
          dotsWrap.appendChild(dot);
        })(n);
      }
    }

    function render(next, animate) {
      index = ((next % steps) + steps) % steps;

      if (animate) {
        // Fade the contents out, swap, fade back in. This is the same
        // .turning hook the original markup used, so the CSS transition is
        // unchanged.
        for (var c = 0; c < cards.length; c++) cards[c].classList.add('turning');
        window.setTimeout(function () {
          paint();
          for (var d = 0; d < cards.length; d++) cards[d].classList.remove('turning');
        }, FADE_MS);
      } else {
        paint();
      }
    }

    function paint() {
      for (var n = 0; n < cards.length; n++) {
        fill(cards[n], donors[(index + n) % donors.length]);
      }
      if (dotsWrap) {
        var dots = dotsWrap.querySelectorAll('.donor-dot');
        for (var d = 0; d < dots.length; d++) {
          dots[d].classList.toggle('active', d === index);
        }
      }
    }

    /** Rebuilds one card's contents for one donor. */
    function fill(card, donor) {
      if (!donor) return;
      card.textContent = '';

      var hasPhoto = donor.photoPath && donor.photoPath.length > 0;
      if (hasPhoto) {
        var img = document.createElement('img');
        img.className = 'donor-photo';
        img.src = donor.photoPath;
        img.alt = 'Logo for ' + donor.name;
        img.loading = 'lazy';
        card.appendChild(img);
      } else {
        // Same initials tile the server renders, so a rotated card is
        // indistinguishable from a server-rendered one.
        var tile = document.createElement('div');
        tile.className = 'donor-photo donor-photo--fallback';
        tile.setAttribute('aria-label', donor.name + ' has no logo');
        tile.textContent = (donor.name || '?').charAt(0);
        card.appendChild(tile);
      }

      var info = document.createElement('div');
      info.className = 'donor-info';

      var name = document.createElement('strong');
      name.className = 'donor-name';
      name.textContent = donor.name;
      info.appendChild(name);

      if (donor.tagline && donor.tagline.length > 0) {
        var biz = document.createElement('span');
        biz.className = 'donor-biz';
        biz.textContent = donor.tagline;
        info.appendChild(biz);
      }

      if (donor.websiteUrl && donor.websiteUrl.length > 0) {
        var link = document.createElement('a');
        link.className = 'donor-link';
        link.href = donor.websiteUrl;
        link.target = '_blank';
        link.rel = 'noopener';
        link.textContent = 'Visit website ↗';
        info.appendChild(link);
      }

      card.appendChild(info);
    }

    function start() {
      if (reduceMotion) return;
      stop();
      timer = window.setInterval(function () {
        if (!paused) render(index + 1, true);
      }, ROTATE_MS);
    }

    function stop() {
      if (timer) {
        window.clearInterval(timer);
        timer = null;
      }
    }

    function restart() {
      stop();
      if (!reduceMotion && !paused) start();
    }
  }
})();
