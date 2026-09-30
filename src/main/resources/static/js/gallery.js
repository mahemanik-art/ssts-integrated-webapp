// Event gallery: the card strip scrolls by hovering the edge strip arrows
// (continuous, fast) or press-and-hold on touch; clicking a card opens a
// large lightbox slider of that event's photos. No other hover scrolling.
(function () {
  'use strict';
  const track = document.getElementById('galleryTrack');
  if (!track) return;

  const cards = Array.from(track.querySelectorAll('.event-card'));

  // ---- Lightbox: click a card to open a large slider --------------------
  const lightbox = document.getElementById('galleryLightbox');
  let lbSlides = [];
  let lbIndex = 0;
  let lbTitle = '';

  function showLightbox(i) {
    if (lbSlides.length === 0) return;
    lbIndex = (i + lbSlides.length) % lbSlides.length;
    const img = document.getElementById('lightboxImage');
    img.src = lbSlides[lbIndex];
    img.alt = 'Photo from ' + lbTitle;
    document.querySelector('.lightbox-title').textContent = lbTitle;
    document.querySelector('.lightbox-counter').textContent =
      lbSlides.length > 1 ? lbIndex + 1 + ' / ' + lbSlides.length : '';
  }

  function openLightbox(card, startIndex) {
    lbSlides = Array.from(card.querySelectorAll('.slide')).map((s) => s.src);
    // Fallback card (no photos) has nothing to show.
    if (lbSlides.length === 0) return;
    lbTitle = card.querySelector('h2').textContent;
    lightbox.hidden = false;
    document.body.style.overflow = 'hidden'; // stop the page scrolling behind
    showLightbox(startIndex);
    lightbox.querySelector('.lightbox-close').focus();
  }

  function closeLightbox() {
    lightbox.hidden = true;
    lbSlides = [];
    document.body.style.overflow = '';
  }

  if (lightbox) {
    lightbox.querySelector('.lightbox-close').addEventListener('click', closeLightbox);
    lightbox.querySelector('.lightbox-arrow--prev').addEventListener('click', () => showLightbox(lbIndex - 1));
    lightbox.querySelector('.lightbox-arrow--next').addEventListener('click', () => showLightbox(lbIndex + 1));
    // Click the dark backdrop (not the figure or an arrow) to close.
    lightbox.addEventListener('click', (e) => {
      if (e.target === lightbox) closeLightbox();
    });
    document.addEventListener('keydown', (e) => {
      if (lightbox.hidden) return;
      if (e.key === 'Escape') closeLightbox();
      else if (e.key === 'ArrowLeft') showLightbox(lbIndex - 1);
      else if (e.key === 'ArrowRight') showLightbox(lbIndex + 1);
    });
  }
  // ---- Hover slider per card -------------------------------------------
  cards.forEach((card) => {
    const slides = Array.from(card.querySelectorAll('.slide'));
    if (slides.length === 0) return;

    const prev = card.querySelector('.slide-arrow--prev');
    const next = card.querySelector('.slide-arrow--next');
    const count = card.querySelector('.slide-count');
    let index = 0;

    function show(i) {
      index = (i + slides.length) % slides.length;
      slides.forEach((s, n) => s.classList.toggle('slide--active', n === index));
      if (count && slides.length > 1) {
        count.textContent = index + 1 + ' / ' + slides.length;
        count.classList.add('slide-count--visible');
      } else if (count) {
        count.classList.remove('slide-count--visible');
      }
    }

    if (slides.length > 1 && prev && next) {
      prev.addEventListener('click', (e) => {
        e.stopPropagation();
        show(index - 1);
      });
      next.addEventListener('click', (e) => {
        e.stopPropagation();
        show(index + 1);
      });
      // Keyboard: arrows work while the card is focused.
      card.setAttribute('tabindex', '0');
      card.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowLeft') {
          e.preventDefault();
          e.stopPropagation();
          show(index - 1);
        } else if (e.key === 'ArrowRight') {
          e.preventDefault();
          e.stopPropagation();
          show(index + 1);
        }
      });
      // Reveal the counter the first time the card is hovered.
      card.addEventListener('mouseenter', () => {
        if (slides.length > 1 && count) {
          count.textContent = index + 1 + ' / ' + slides.length;
          count.classList.add('slide-count--visible');
        }
      });
    }
    show(0);

    // Click anywhere on the card opens the large lightbox slider, starting at
    // the photo currently showing on the card.
    card.addEventListener('click', () => openLightbox(card, index));
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openLightbox(card, index);
      }
    });
  });

  // ---- Track centering --------------------------------------------------
  function centerCard(card, instant) {
    const target = card.offsetLeft - track.clientWidth / 2 + card.offsetWidth / 2;
    track.scrollTo({ left: target, behavior: instant ? 'auto' : 'smooth' });
  }

  function updateCentered() {
    const mid = track.scrollLeft + track.clientWidth / 2;
    let best = null;
    let bestDist = Infinity;
    for (const card of cards) {
      const center = card.offsetLeft + card.offsetWidth / 2;
      const dist = Math.abs(center - mid);
      if (dist < bestDist) {
        bestDist = dist;
        best = card;
      }
    }
    for (const card of cards) {
      card.classList.toggle('centered', card === best);
    }
  }

  track.addEventListener('scroll', updateCentered, { passive: true });
  window.addEventListener('resize', updateCentered);

  // ---- Strip arrows: hold to scroll the card strip fast -----------------
  const stripPrev = document.querySelector('.strip-arrow--prev');
  const stripNext = document.querySelector('.strip-arrow--next');
  const STRIP_SPEED = 280; // px per frame while hovered/held -- double speed
  let stripRafId = null;
  let stripDir = 0;

  function stripLoop() {
    if (stripDir === 0) {
      stripRafId = null;
      return;
    }
    const atStart = track.scrollLeft <= 0;
    const atEnd = track.scrollLeft >= track.scrollWidth - track.clientWidth;
    if ((stripDir < 0 && atStart) || (stripDir > 0 && atEnd)) {
      stripRafId = null;
      updateStripArrows();
      return;
    }
    track.scrollLeft += stripDir * STRIP_SPEED;
    stripRafId = requestAnimationFrame(stripLoop);
  }

  function startStrip(dir) {
    stripDir = dir;
    if (stripRafId === null) stripRafId = requestAnimationFrame(stripLoop);
  }

  function stopStrip() {
    stripDir = 0;
  }

  function updateStripArrows() {
    const atStart = track.scrollLeft <= 1;
    const atEnd = track.scrollLeft >= track.scrollWidth - track.clientWidth - 1;
    if (stripPrev) stripPrev.disabled = atStart;
    if (stripNext) stripNext.disabled = atEnd;
  }

  if (stripPrev && stripNext) {
    // Hovering the arrow scrolls; leaving (or lifting the finger) stops.
    stripPrev.addEventListener('mouseenter', () => startStrip(-1));
    stripPrev.addEventListener('mouseleave', stopStrip);
    stripNext.addEventListener('mouseenter', () => startStrip(1));
    stripNext.addEventListener('mouseleave', stopStrip);
    // Touch has no hover: press-and-hold does the same job.
    stripPrev.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      startStrip(-1);
    });
    stripNext.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      startStrip(1);
    });
    ['pointerup', 'pointercancel'].forEach((evt) => {
      stripPrev.addEventListener(evt, stopStrip);
      stripNext.addEventListener(evt, stopStrip);
    });
    track.addEventListener('scroll', updateStripArrows, { passive: true });
    window.addEventListener('resize', updateStripArrows);
  }

  // Center the first card on load.
  if (cards.length > 0) {
    centerCard(cards[0], true);
    updateCentered();
  }
  updateStripArrows();
})();
