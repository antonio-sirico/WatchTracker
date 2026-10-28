/* WatchTracker – frontend. I riferimenti UC-xx e RF x.y rimandano al Requirements Analysis Document. */

const TYPE_LABEL = { FILM: 'Film', SERIE_TV: 'Serie TV', ANIME: 'Anime' };
const SOURCE_LABEL = { TMDB_MOVIE: 'TMDB', TMDB_TV: 'TMDB', MAL: 'MyAnimeList' };
const STATUS_LABEL = {
  DA_VEDERE: 'Da vedere',
  IN_CORSO: 'In corso',
  COMPLETATO: 'Completato',
  ABBANDONATO: 'Abbandonato',
};
const MSG = {
  searchMin: 'Inserisci almeno 2 caratteri per cercare',
  noResults: (q) => `Nessun risultato per “${q}”: controlla il titolo e riprova`,
  libraryEmpty: 'La tua libreria è vuota: cerca un titolo e aggiungilo.',
  noMatch: 'Nessun contenuto corrisponde ai filtri selezionati',
  dateRange: 'La data iniziale deve precedere la data finale',
  onlyCompleted: 'Puoi valutare solo i contenuti completati',
  reviewLength: 'La recensione deve avere da 10 a 2000 caratteri',
  ratingRange: 'Seleziona un valore da 1 a 5 stelle',
  profileUnavailable: 'Questo profilo non è disponibile',
  notAvailable: 'non disponibile',
  fileTooLarge: 'Il file supera la dimensione massima di 20 MB',
  fileInvalid: 'Il file selezionato non è un backup valido di WatchTracker',
};
const MAX_BACKUP_BYTES = 20 * 1024 * 1024;

const state = {
  user: null,
  results: [],
  library: [],
  view: 'search',
  detail: null,        // scheda aperta: { content, entry, declinedAt }
  pendingBackup: null, // testo del file di backup in attesa di conferma (UC-21)
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

/* ---------------------------------------------------------------- utilità */

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  let body = options.body;
  if (body !== undefined && typeof body !== 'string') {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  } else if (typeof body === 'string') {
    headers['Content-Type'] = headers['Content-Type'] || 'application/json';
  }
  const res = await fetch(path, { credentials: 'same-origin', ...options, headers, body });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.message || `Errore ${res.status}`);
    err.status = res.status;
    err.fields = data.fields || {};
    throw err;
  }
  return data;
}

function escapeHtml(s) {
  return String(s ?? '')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

let toastTimer;
/** RNF-U4: esito delle operazioni. */
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.toggle('err', isError);
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), isError ? 5000 : 3000);
}

function formatDate(iso, opts = { day: 'numeric', month: 'long', year: 'numeric' }) {
  if (!iso) return '';
  const d = iso.length === 10 ? new Date(iso + 'T00:00:00') : new Date(iso);
  return new Intl.DateTimeFormat('it-IT', opts).format(d);
}

function formatMonth(yyyyMm) {
  const [y, m] = yyyyMm.split('-').map(Number);
  return new Intl.DateTimeFormat('it-IT', { month: 'short', year: 'numeric' }).format(new Date(y, m - 1, 1));
}

/** RF 3.7: etichetta dello stato di prosecuzione. */
function continuationLabel(c) {
  if (!c) return 'Prosecuzione non nota';
  switch (c.status) {
    case 'TERMINATA': return 'Terminata';
    case 'PROSSIMAMENTE': return 'Prossimamente';
    case 'DATA': return `Prossimo episodio: ${formatDate(c.nextDate)}`;
    default: return 'Prosecuzione non nota';
  }
}

function posterHtml(url) {
  return url
    ? `<div class="poster"><img src="${escapeHtml(url)}" alt="" loading="lazy"></div>`
    : `<div class="poster">Nessuna locandina</div>`;
}

function badge(type) {
  return `<span class="badge ${type}">${TYPE_LABEL[type] || type}</span>`;
}

function statusHtml(status) {
  return `<span class="status ${status}">${STATUS_LABEL[status]}</span>`;
}

function starsHtml(rating) {
  if (!rating) return '';
  return `<span class="stars" title="${rating} su 5">${'★'.repeat(rating)}<span class="off">${'★'.repeat(5 - rating)}</span></span>`;
}

function progressHtml(entry) {
  if (entry.type === 'FILM' || !entry.episodeCount) return '';
  const pct = Math.min(100, Math.round((entry.watchedEpisodes / entry.episodeCount) * 100));
  return `<div class="muted" style="font-size:.8rem">${entry.watchedEpisodes}/${entry.episodeCount} episodi</div>
    <div class="progress ${pct === 100 ? 'done' : ''}"><span style="width:${pct}%"></span></div>`;
}

/** EC3: mostra il messaggio accanto al campo senza cancellare gli altri valori. */
function showFieldErrors(form, err) {
  $$('[data-error]', form).forEach((p) => { p.textContent = ''; });
  $$('.invalid', form).forEach((i) => i.classList.remove('invalid'));
  const fields = err.fields || {};
  let shown = false;
  for (const [name, message] of Object.entries(fields)) {
    const target = $(`[data-error="${name}"]`, form);
    if (target) {
      target.textContent = message;
      $(`[name="${name}"]`, form)?.classList.add('invalid');
      shown = true;
    }
  }
  if (!shown) {
    const general = $('[data-error="form"]', form) || $('[data-error]', form);
    if (general) general.textContent = Object.values(fields)[0] || err.message;
    else toast(err.message, true);
  }
}

/* ------------------------------------------------- finestra di conferma */

/**
 * Finestra modale con scelte personalizzate. Restituisce il valore del pulsante scelto,
 * oppure null se la finestra viene chiusa senza scegliere (UC-14 E2).
 */
function ask({ title, body = '', actions }) {
  const dialog = $('#confirm');
  $('#confirm-title').textContent = title;
  $('#confirm-body').innerHTML = body;
  const box = $('#confirm-actions');
  box.innerHTML = '';
  return new Promise((resolve) => {
    let result = null;
    actions.forEach((a) => {
      const b = document.createElement('button');
      b.textContent = a.label;
      if (a.primary) b.classList.add('primary');
      if (a.danger) b.classList.add('danger');
      b.addEventListener('click', () => { result = a.value; dialog.close(); });
      box.appendChild(b);
    });
    dialog.addEventListener('close', () => resolve(result), { once: true });
    dialog.showModal();
  });
}

function confirmDialog(title, body, okLabel = 'Conferma', danger = false) {
  return ask({
    title,
    body,
    actions: [{ label: 'Annulla', value: false }, { label: okLabel, value: true, primary: !danger, danger }],
  }).then((v) => v === true);
}

/* ------------------------------------------------------ sessione (UC-02) */

function renderSession() {
  const el = $('#session');
  if (state.user) {
    el.innerHTML = `
      <span class="nick">${escapeHtml(state.user.nickname)}</span>
      <button id="logout">Esci</button>`;
    $('#logout').addEventListener('click', logout);
  } else {
    el.innerHTML = `<button id="open-register">Registrati</button><button class="primary" id="open-auth">Accedi</button>`;
    $('#open-auth').addEventListener('click', () => openAuth('login'));
    $('#open-register').addEventListener('click', () => openAuth('register'));
  }
  $$('[data-needs-auth]').forEach((b) => b.classList.toggle('hidden', !state.user));
}

async function loadSession() {
  try {
    state.user = await api('/api/auth/me');
  } catch {
    state.user = null;
  }
  renderSession();
}

async function logout() {
  await api('/api/auth/logout', { method: 'POST' });
  state.user = null;
  state.library = [];
  renderSession();
  location.hash = '#/search';
  toast('Sessione terminata');
}

function openAuth(tab) {
  switchTab(tab);
  $('#auth').showModal();
}

function switchTab(tab) {
  $$('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === tab));
  $('#login-form').classList.toggle('hidden', tab !== 'login');
  $('#register-form').classList.toggle('hidden', tab !== 'register');
}

/** UC-01 / UC-02: i vincoli della tabella dei dati sono verificati anche lato client. */
function validateRegister(body) {
  const fields = {};
  if (!/^[A-Za-z0-9._]{3,20}$/.test(body.nickname || '')) {
    fields.nickname = 'Il nickname deve avere da 3 a 20 caratteri e può contenere solo lettere, numeri, punto e trattino basso';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email || '') || body.email.length > 254) {
    fields.email = 'Inserisci un indirizzo e-mail valido';
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(body.password || '')) {
    fields.password = 'La password deve avere almeno 8 caratteri e contenere almeno una lettera e un numero';
  }
  if (body.password !== body.confirmPassword) {
    fields.confirmPassword = 'Le due password non coincidono';
  }
  return fields;
}

function bindAuthForm(form, path, successMessage, validate) {
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(form).entries());
    const fields = validate ? validate(body) : {};
    if (Object.keys(fields).length) {
      showFieldErrors(form, { fields });
      return;
    }
    try {
      state.user = await api(path, { method: 'POST', body });
      form.reset();
      $$('[data-error]', form).forEach((p) => { p.textContent = ''; });
      $('#auth').close();
      renderSession();
      toast(successMessage);
      await loadLibrary(true);
      route();
      if (state.detail) renderDetail();
    } catch (err) {
      showFieldErrors(form, err);
    }
  });
}

/* ---------------------------------------------------------- ricerca (UC-06) */

async function search(query) {
  const status = $('#search-status');
  const warnings = $('#search-warnings');
  const error = $('#search-error');
  error.textContent = '';
  if (query.length < 2 || query.length > 100) {
    error.textContent = MSG.searchMin;
    return;
  }
  status.textContent = 'Ricerca in corso...';
  warnings.innerHTML = '';
  $('#results').innerHTML = '';
  try {
    const data = await api(`/api/catalog/search?q=${encodeURIComponent(query)}`);
    state.results = data.results;
    warnings.innerHTML = data.warnings.map((w) => `<div class="warning">${escapeHtml(w)}</div>`).join('');
    status.textContent = data.results.length
      ? `${data.results.length} risultati per “${data.query}”`
      : MSG.noResults(data.query);
    renderResults();
  } catch (err) {
    status.textContent = '';
    if (err.fields?.q) error.textContent = err.fields.q;
    else toast(err.message, true);
  }
}

function renderResults() {
  $('#results').innerHTML = state.results.map((c, i) => `
    <article class="card" data-index="${i}">
      ${posterHtml(c.posterUrl)}
      <div class="card-body">
        <div class="card-title">${escapeHtml(c.title)}</div>
        <div class="card-meta">
          ${badge(c.type)}
          ${c.year ? `<span>${c.year}</span>` : ''}
          <span>${escapeHtml(c.sourceName || SOURCE_LABEL[c.source])}</span>
        </div>
        ${c.genres?.length ? `<div class="card-meta">${escapeHtml(c.genres.slice(0, 3).join(', '))}</div>` : ''}
      </div>
    </article>`).join('');
}

/* ------------------------------------------------- scheda contenuto (UC-07) */

async function openDetail(source, id) {
  const dialog = $('#detail');
  const body = $('#detail-body');
  body.innerHTML = '<p class="muted">Caricamento...</p>';
  if (!dialog.open) dialog.showModal();
  state.detail = null;
  let content = null;
  let entry = null;
  let catalogError = null;
  try {
    content = await api(`/api/catalog/${source}/${encodeURIComponent(id)}`);
  } catch (err) {
    catalogError = err.message;
  }
  if (state.user) {
    try {
      entry = await api(`/api/library/entry?source=${source}&externalId=${encodeURIComponent(id)}`);
    } catch { entry = null; }
  }
  if (!content && !entry) {
    body.innerHTML = `<p class="error">${escapeHtml(catalogError || 'Contenuto non disponibile')}</p>`;
    return;
  }
  // UC-07 E2: catalogo non raggiungibile ma contenuto in libreria: si mostrano stato e avanzamento salvati
  if (!content) {
    content = {
      source: entry.source, externalId: entry.externalId, type: entry.type, title: entry.title, year: entry.year,
      posterUrl: entry.posterUrl, genres: entry.genres || [], runtimeMinutes: entry.runtimeMinutes,
      seasonCount: entry.seasonCount, episodeCount: entry.episodeCount, seasons: [], continuation: entry.continuation,
      sourceName: SOURCE_LABEL[entry.source], unavailable: catalogError,
    };
  }
  state.detail = { content, entry, declinedAt: -1 };
  renderDetail();
}

function episodeKey(season, episode) {
  return `${season}:${episode}`;
}

function watchedSet(entry) {
  return new Set((entry?.watched || []).map((w) => episodeKey(w.season, w.episode)));
}

function renderDetail() {
  const { content: d, entry } = state.detail;
  const seen = watchedSet(entry);
  const isSeries = d.type !== 'FILM';
  const meta = [];
  if (d.year) meta.push(String(d.year));
  if (isSeries) {
    meta.push(d.seasonCount ? `${d.seasonCount} stagion${d.seasonCount === 1 ? 'e' : 'i'}` : 'stagioni: non disponibile');
    meta.push(d.episodeCount ? `${d.episodeCount} episodi` : 'episodi: non disponibile');
    meta.push(d.runtimeMinutes ? `${d.runtimeMinutes} min/episodio` : 'durata: non disponibile');
  } else {
    meta.push(d.runtimeMinutes ? `${d.runtimeMinutes} min` : 'durata: non disponibile');
  }
  meta.push(`Fonte: ${d.sourceName || SOURCE_LABEL[d.source]}`);

  $('#detail-body').innerHTML = `
    ${posterHtml(d.posterUrl)}
    <div>
      <h2>${escapeHtml(d.title)}</h2>
      ${d.originalTitle && d.originalTitle !== d.title ? `<p class="orig">${escapeHtml(d.originalTitle)}</p>` : ''}
      <div class="meta">${badge(d.type)} ${meta.map((m) => `<span>${escapeHtml(m)}</span>`).join('<span>&middot;</span>')}</div>
      ${isSeries ? `<div class="meta"><span class="continuation">📅 ${escapeHtml(continuationLabel(d.continuation))}</span></div>` : ''}
      <div class="genres">${(d.genres || []).map((g) => `<span class="genre">${escapeHtml(g)}</span>`).join('') || '<span class="muted">Generi: non disponibile</span>'}</div>
      ${d.unavailable ? `<div class="warning">${escapeHtml(d.unavailable)}</div>` : ''}
      <p class="overview">${escapeHtml(d.overview || 'Trama non disponibile.')}</p>
      <div id="library-box"></div>
      <div id="seasons-box"></div>
    </div>`;

  renderLibraryBox();
  renderSeasons(seen);
}

/** UC-10, UC-12, UC-17: funzioni della libreria nella scheda. */
function renderLibraryBox() {
  const { content: d, entry } = state.detail;
  const box = $('#library-box');
  if (!state.user) {
    box.innerHTML = `<div class="actions"><button class="primary" id="login-to-add">Accedi per aggiungere alla libreria</button></div>`;
    $('#login-to-add').addEventListener('click', () => { $('#detail').close(); openAuth('login'); });
    return;
  }
  if (!entry) {
    box.innerHTML = `<div class="actions"><button class="primary" id="add-to-library">Aggiungi alla libreria</button>
      ${d.type !== 'FILM' ? '<span class="muted">oppure segna un episodio come visto</span>' : ''}</div>`;
    $('#add-to-library').addEventListener('click', addToLibrary);
    return;
  }
  const isSeries = entry.type !== 'FILM';
  const completed = entry.status === 'COMPLETATO';
  box.innerHTML = `
    <div class="library-box">
      <div class="row">
        <strong>In libreria</strong>
        <select class="status-select ${entry.status}" id="detail-status">
          ${Object.entries(STATUS_LABEL).map(([v, l]) => `<option value="${v}" ${v === entry.status ? 'selected' : ''}>${l}</option>`).join('')}
        </select>
        ${statusHtml(entry.status)}
        <span class="muted">aggiunto il ${formatDate(entry.addedAt, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
        <button class="danger small" id="remove-entry">Rimuovi dalla libreria</button>
      </div>
      ${isSeries ? `<div>${progressHtml(entry)}</div>` : ''}
      ${completed ? ratingBoxHtml(entry) : `<p class="muted" style="margin:0">${MSG.onlyCompleted}.</p>`}
    </div>`;

  $('#detail-status').addEventListener('change', (e) => changeStatus(entry, e.target.value, true));
  $('#remove-entry').addEventListener('click', () => removeEntry(entry, true));
  if (completed) bindRatingBox(entry);
}

function ratingBoxHtml(entry) {
  const r = entry.rating || 0;
  return `
    <div class="rating-box">
      <div class="row">
        <strong>Il tuo rating</strong>
        <span class="star-input" id="star-input" role="radiogroup" aria-label="Rating da 1 a 5 stelle">
          ${[1, 2, 3, 4, 5].map((v) => `<span data-value="${v}" class="${v <= r ? 'on' : ''}" role="radio" aria-checked="${v === r}">★</span>`).join('')}
        </span>
        ${r ? `<button class="small" id="remove-rating">Rimuovi rating</button>` : '<span class="muted">Scegli le stelle per valutare</span>'}
      </div>
      <label class="muted">Recensione (facoltativa, 10–2000 caratteri)
        <textarea id="review-input" maxlength="2000" placeholder="Scrivi una recensione...">${escapeHtml(entry.review || '')}</textarea>
      </label>
      <p class="error" id="rating-error"></p>
      <div class="row">
        <button class="primary small" id="save-rating">Salva</button>
        ${entry.review ? `<button class="small danger" id="delete-review">Elimina recensione</button>
          <span class="muted">aggiornata il ${formatDate(entry.reviewUpdatedAt, { day: 'numeric', month: 'short', year: 'numeric' })}</span>` : ''}
      </div>
    </div>`;
}

function bindRatingBox(entry) {
  let chosen = entry.rating || 0;
  const stars = $$('#star-input span');
  const paint = (v) => stars.forEach((s) => s.classList.toggle('on', Number(s.dataset.value) <= v));
  stars.forEach((s) => {
    s.addEventListener('mouseenter', () => paint(Number(s.dataset.value)));
    s.addEventListener('mouseleave', () => paint(chosen));
    s.addEventListener('click', () => { chosen = Number(s.dataset.value); paint(chosen); });
  });
  $('#save-rating').addEventListener('click', async () => {
    const error = $('#rating-error');
    error.textContent = '';
    const review = $('#review-input').value.trim();
    if (chosen < 1 || chosen > 5) { error.textContent = MSG.ratingRange; return; }
    if (review && (review.length < 10 || review.length > 2000)) { error.textContent = MSG.reviewLength; return; }
    try {
      const updated = await api(`/api/library/${entry.id}/rating`, { method: 'PUT', body: { rating: chosen, review: review || null } });
      replaceEntry(updated);
      toast('Rating salvato');
      renderDetail();
    } catch (err) {
      error.textContent = err.fields?.review || err.fields?.rating || err.message;
    }
  });
  $('#remove-rating')?.addEventListener('click', async () => {
    // UC-17 E4: se c'è una recensione si chiede se eliminare anche quella
    let alsoReview = false;
    if (entry.review) {
      const choice = await ask({
        title: 'Rimuovere il rating?',
        body: 'Vuoi eliminare anche la recensione associata?',
        actions: [{ label: 'Annulla', value: null }, { label: 'Solo il rating', value: 'rating' }, { label: 'Rating e recensione', value: 'both', danger: true }],
      });
      if (!choice) return;
      alsoReview = choice === 'both';
    } else if (!(await confirmDialog('Rimuovere il rating?', '', 'Rimuovi', true))) {
      return;
    }
    try {
      replaceEntry(await api(`/api/library/${entry.id}/rating?review=${alsoReview}`, { method: 'DELETE' }));
      toast('Rating rimosso');
      renderDetail();
    } catch (err) { toast(err.message, true); }
  });
  $('#delete-review')?.addEventListener('click', async () => {
    // UC-18: eliminazione della recensione
    if (!(await confirmDialog('Eliminare la recensione?', 'La recensione sarà rimossa dal tuo profilo.', 'Elimina', true))) return;
    try {
      replaceEntry(await api(`/api/library/${entry.id}/review`, { method: 'DELETE' }));
      toast('Recensione eliminata');
      renderDetail();
    } catch (err) { toast(err.message, true); }
  });
}

/** RF 3.2: tutte le stagioni e gli episodi con l'indicazione visto/non visto. */
function renderSeasons(seen) {
  const { content: d, entry } = state.detail;
  const box = $('#seasons-box');
  if (d.type === 'FILM' || !d.seasons?.length) {
    box.innerHTML = d.type === 'FILM' ? '' : '<p class="muted">Elenco degli episodi non disponibile.</p>';
    return;
  }
  const canMark = !!state.user;
  box.innerHTML = `<div class="seasons">${d.seasons.map((s, i) => {
    const total = s.episodes.length;
    const watched = s.episodes.filter((e) => seen.has(episodeKey(s.number, e.number))).length;
    return `
      <details class="season" ${i === 0 || (entry && watched > 0 && watched < total) ? 'open' : ''}>
        <summary>
          ${canMark ? `<input type="checkbox" class="season-check" data-season="${s.number}" ${watched === total ? 'checked' : ''} data-all="${watched === total}" data-partial="${watched > 0 && watched < total}" aria-label="Stagione vista: ${escapeHtml(s.name || `Stagione ${s.number}`)}" title="${watched === total ? 'Vista: clicca per segnare tutta la stagione come non vista' : 'Segna tutta la stagione come vista'}">` : ''}
          <span class="grow">${escapeHtml(s.name || `Stagione ${s.number}`)}</span>
          <span class="muted">${watched}/${total} episodi</span>
        </summary>
        <ul class="episodes">${s.episodes.map((e) => {
          const isSeen = seen.has(episodeKey(s.number, e.number));
          const label = e.name || `Episodio ${e.number}`;
          return `<li class="episode ${isSeen ? 'seen' : ''}">
            ${canMark ? `<input type="checkbox" class="ep-check" data-season="${s.number}" data-episode="${e.number}" ${isSeen ? 'checked' : ''} aria-label="Visto: S${s.number} E${e.number} ${escapeHtml(label)}" title="${isSeen ? 'Visto: clicca per segnare come non visto' : 'Segna come visto'}">` : ''}
            <span class="num">E${e.number}</span>
            <span class="name">${escapeHtml(label)}</span>
            ${e.airDate ? `<span class="date">${formatDate(e.airDate, { day: 'numeric', month: 'short', year: 'numeric' })}</span>` : ''}
          </li>`;
        }).join('')}</ul>
      </details>`;
  }).join('')}</div>`;

  if (!canMark) return;
  // stagione vista solo in parte: checkbox in stato indeterminato
  $$('.season-check[data-partial="true"]', box).forEach((c) => { c.indeterminate = true; });
  // Stagione intera (UC-15; la rimozione dell'intera stagione è un'estensione non prevista dal RAD).
  // preventDefault evita sia il cambio della checkbox (che torna allo stato salvato) sia l'apertura/chiusura della stagione.
  box.addEventListener('click', (e) => {
    const check = e.target.closest('input.season-check');
    if (!check) return;
    e.preventDefault();
    e.stopPropagation();
    // durante il click il browser ha già invertito "checked": lo stato salvato è in data-all
    if (check.dataset.all === 'true') unmarkSeason(Number(check.dataset.season));
    else markSeason(Number(check.dataset.season));
  });
  // Singolo episodio (UC-13): la checkbox torna allo stato salvato finché il server non conferma
  box.addEventListener('change', (e) => {
    const check = e.target.closest('input.ep-check');
    if (!check) return;
    check.checked = !check.checked;
    markEpisode(Number(check.dataset.season), Number(check.dataset.episode));
  });
}

/* --------------------------------------- libreria: operazioni dalla scheda */

function replaceEntry(updated) {
  if (state.detail && state.detail.entry?.id === updated.id) state.detail.entry = updated;
  else if (state.detail && state.detail.content.source === updated.source && state.detail.content.externalId === updated.externalId) state.detail.entry = updated;
  const idx = state.library.findIndex((e) => e.id === updated.id);
  if (idx >= 0) state.library[idx] = updated;
  else state.library.unshift(updated);
  if (state.view === 'library') renderLibrary();
}

async function addToLibrary() {
  const { content: d } = state.detail;
  try {
    const entry = await api('/api/library', { method: 'POST', body: { source: d.source, externalId: d.externalId } });
    replaceEntry(entry);
    toast(`"${d.title}" aggiunto alla libreria con stato Da vedere`);
    renderDetail();
  } catch (err) {
    toast(err.message, true);
  }
}

/** UC-12: cambio di stato; E1 se Completato con episodi non visti. */
async function changeStatus(entry, status, fromDetail) {
  if (status === entry.status) return;
  if (status === 'COMPLETATO' && entry.type !== 'FILM' && entry.episodeCount && entry.watchedEpisodes < entry.episodeCount) {
    const ok = await confirmDialog('Completare la serie?',
      'Non tutti gli episodi risultano visti: vuoi comunque segnare la serie come completata?', 'Sì, completa');
    if (!ok) { if (fromDetail) renderLibraryBox(); else renderLibrary(); return; }
  }
  try {
    const updated = await api(`/api/library/${entry.id}/status`, { method: 'PATCH', body: { status } });
    replaceEntry(updated);
    toast(`Stato aggiornato: ${STATUS_LABEL[status]}`);
    if (fromDetail) renderDetail();
  } catch (err) {
    toast(err.message, true);
    if (fromDetail) renderLibraryBox(); else renderLibrary();
  }
}

/** UC-12 passo 2: rimozione previa conferma. */
async function removeEntry(entry, fromDetail) {
  const ok = await confirmDialog(`Rimuovere "${entry.title}"?`,
    'Saranno eliminati anche avanzamento, rating, recensione e commenti associati.', 'Rimuovi', true);
  if (!ok) return;
  try {
    await api(`/api/library/${entry.id}`, { method: 'DELETE' });
    state.library = state.library.filter((e) => e.id !== entry.id);
    if (state.detail?.entry?.id === entry.id) state.detail.entry = null;
    toast('Rimosso dalla libreria');
    if (fromDetail) renderDetail();
    else renderLibrary();
  } catch (err) {
    toast(err.message, true);
  }
}

/** Episodi che precedono (stagione, episodio) nell'ordine del catalogo. */
function previousEpisodes(seasons, season, episode) {
  const out = [];
  for (const s of seasons) {
    for (const e of s.episodes) {
      if (s.number < season || (s.number === season && e.number < episode)) out.push({ season: s.number, episode: e.number });
    }
  }
  return out;
}

/** UC-13 (con UC-14 e UC-16): registrazione di un episodio. */
async function markEpisode(season, episode) {
  const { content: d, entry } = state.detail;
  const seen = watchedSet(entry);
  const key = episodeKey(season, episode);

  // UC-13 E1: episodio già visto -> si propone di annullare il contrassegno
  if (seen.has(key)) {
    const ok = await confirmDialog(`S${season} E${episode} è già segnato come visto`, 'Vuoi segnarlo come non visto?', 'Segna come non visto');
    if (ok) await sendEpisodes([{ season, episode }], false);
    return;
  }
  // UC-13 E2: contenuto abbandonato
  if (entry?.status === 'ABBANDONATO') {
    const ok = await confirmDialog('Riprendere la visione?', 'Il contenuto è segnato come Abbandonato. Vuoi riprendere la visione e portarlo In corso?', 'Sì, riprendi');
    if (!ok) return;
  }
  // UC-14: episodi precedenti non visti
  let episodes = [{ season, episode }];
  const previous = previousEpisodes(d.seasons, season, episode).filter((p) => !seen.has(episodeKey(p.season, p.episode)));
  if (previous.length) {
    const first = previous[0];
    const last = previous[previous.length - 1];
    const range = previous.length === 1 ? `S${first.season} E${first.episode}` : `S${first.season} E${first.episode} – S${last.season} E${last.episode}`;
    const choice = await ask({
      title: 'Hai visto anche gli episodi precedenti?',
      body: `<p>Risultano non visti ${previous.length} episod${previous.length === 1 ? 'io' : 'i'} precedent${previous.length === 1 ? 'e' : 'i'}: <strong>${range}</strong>.</p>`,
      actions: [{ label: 'No, solo questo', value: 'one' }, { label: 'Sì, segnali tutti', value: 'all', primary: true }],
    });
    if (choice === 'all') episodes = [...previous, { season, episode }];
  }
  await sendEpisodes(episodes, true);
}

/** UC-15: intera stagione in un'unica operazione. */
async function markSeason(seasonNumber) {
  const { content: d, entry } = state.detail;
  const seen = watchedSet(entry);
  const s = d.seasons.find((x) => x.number === seasonNumber);
  const missing = s.episodes.filter((e) => !seen.has(episodeKey(seasonNumber, e.number))).map((e) => ({ season: seasonNumber, episode: e.number }));
  if (!missing.length) { toast('Hai già visto tutti gli episodi di questa stagione'); return; }
  if (entry?.status === 'ABBANDONATO') {
    const ok = await confirmDialog('Riprendere la visione?', 'Il contenuto è segnato come Abbandonato. Vuoi riprendere la visione e portarlo In corso?', 'Sì, riprendi');
    if (!ok) return;
  }
  const name = s.name || 'Stagione ' + seasonNumber;
  // Come UC-14, esteso alla stagione: se le stagioni precedenti hanno episodi non visti si propone di segnarli
  const previous = previousEpisodes(d.seasons, seasonNumber, 1).filter((p) => !seen.has(episodeKey(p.season, p.episode)));
  if (previous.length) {
    const choice = await ask({
      title: `Segnare "${name}" come vista?`,
      body: `<p>Risultano non visti anche ${previous.length} episod${previous.length === 1 ? 'io' : 'i'} delle stagioni precedenti. Vuoi segnare come visti anche quelli?</p>`,
      actions: [
        { label: 'Annulla', value: null },
        { label: `No, solo questa stagione (${missing.length})`, value: 'one' },
        { label: `Sì, anche le precedenti (${previous.length + missing.length})`, value: 'all', primary: true },
      ],
    });
    if (!choice) return;
    await sendEpisodes(choice === 'all' ? [...previous, ...missing] : missing, true);
    return;
  }
  const ok = await confirmDialog(`Segnare "${name}" come vista?`,
    `Saranno contrassegnati come visti ${missing.length} episod${missing.length === 1 ? 'io' : 'i'}.`, 'Segna come vista');
  if (!ok) return;
  await sendEpisodes(missing, true);
}

/** Estensione (non nel RAD): toglie il contrassegno a tutti gli episodi visti di una stagione. */
async function unmarkSeason(seasonNumber) {
  const { content: d, entry } = state.detail;
  const seen = watchedSet(entry);
  const s = d.seasons.find((x) => x.number === seasonNumber);
  const watched = s.episodes.filter((e) => seen.has(episodeKey(seasonNumber, e.number))).map((e) => ({ season: seasonNumber, episode: e.number }));
  if (!watched.length) return;
  const ok = await confirmDialog(`Segnare "${s.name || 'Stagione ' + seasonNumber}" come non vista?`,
    `${watched.length} episod${watched.length === 1 ? 'io sarà segnato' : 'i saranno segnati'} come non vist${watched.length === 1 ? 'o' : 'i'}.`, 'Segna come non vista', true);
  if (!ok) return;
  await sendEpisodes(watched, false);
}

async function sendEpisodes(episodes, watched) {
  const { content: d, entry: before } = state.detail;
  try {
    const result = await api('/api/library/episodes', {
      method: 'POST',
      body: { source: d.source, externalId: d.externalId, episodes, watched },
    });
    replaceEntry(result.entry);
    if (result.added) toast(`"${d.title}" aggiunto alla libreria e portato In corso`);
    else if (watched) toast(result.changed === 1 ? 'Episodio segnato come visto' : `${result.changed} episodi segnati come visti`);
    else toast(result.changed === 1 ? 'Episodio segnato come non visto' : `${result.changed} episodi segnati come non visti`);
    if (watched && before?.status !== result.entry.status && !result.added) toast(`Stato aggiornato: ${STATUS_LABEL[result.entry.status]}`);
    renderDetail();
    // UC-16: proposta di completamento (non riproposta finché l'avanzamento non cambia, E1)
    if (result.allWatched && state.detail.declinedAt !== result.entry.watchedEpisodes) {
      await proposeCompletion(result.entry);
    }
  } catch (err) {
    toast(err.message, true);
  }
}

/** UC-16: «Hai visto tutti gli episodi disponibili: vuoi segnare il contenuto come Completato?» */
async function proposeCompletion(entry) {
  const c = state.detail.content.continuation;
  const continues = c && (c.status === 'PROSSIMAMENTE' || c.status === 'DATA');
  const body = continues
    ? `<p>La serie prosegue: <strong>${escapeHtml(continuationLabel(c).replace('Prossimo episodio: ', ''))}</strong>. Puoi anche lasciarla In corso in attesa dei nuovi episodi.</p>`
    : '';
  const ok = await confirmDialog('Hai visto tutti gli episodi disponibili', `<p>Vuoi segnare il contenuto come Completato?</p>${body}`, 'Sì, Completato');
  if (!ok) { state.detail.declinedAt = entry.watchedEpisodes; return; }
  try {
    replaceEntry(await api(`/api/library/${entry.id}/status`, { method: 'PATCH', body: { status: 'COMPLETATO' } }));
    toast('Contenuto completato: ora puoi assegnare rating e recensione');
    renderDetail();
  } catch (err) { toast(err.message, true); }
}

/* -------------------------------------------------------- libreria (UC-11) */

async function loadLibrary(silent = false) {
  if (!state.user) return;
  const status = $('#library-status');
  if (!silent) status.textContent = 'Caricamento...';
  try {
    state.library = await api('/api/library');
    if (state.view === 'library') renderLibrary();
  } catch (err) {
    status.textContent = '';
    if (!silent) toast(err.message, true);
  }
}

function libraryFilters() {
  return Object.fromEntries(new FormData($('#library-filters')).entries());
}

function renderGenreOptions() {
  const select = $('#library-filters [name=genre]');
  const current = select.value;
  const genres = [...new Set(state.library.flatMap((e) => e.genres || []))].sort((a, b) => a.localeCompare(b, 'it'));
  select.innerHTML = `<option value="">Tutti</option>${genres.map((g) => `<option value="${escapeHtml(g)}" ${g === current ? 'selected' : ''}>${escapeHtml(g)}</option>`).join('')}`;
}

function renderLibrary() {
  renderGenreOptions();
  const f = libraryFilters();
  const error = $('#library-error');
  error.textContent = '';
  $('#library-filters [name=from]').classList.remove('invalid');
  // UC-11 E3
  if (f.from && f.to && f.from > f.to) {
    error.textContent = MSG.dateRange;
    $('#library-filters [name=from]').classList.add('invalid');
  }
  let entries = state.library.filter((e) => {
    if (f.type && e.type !== f.type) return false;
    if (f.status && e.status !== f.status) return false;
    if (f.genre && !(e.genres || []).includes(f.genre)) return false;
    if (f.rating === 'none' && e.rating) return false;
    if (f.rating && f.rating !== 'none' && e.rating !== Number(f.rating)) return false;
    const added = e.addedAt.slice(0, 10);
    if (f.from && added < f.from) return false;
    if (f.to && added > f.to) return false;
    return true;
  });
  const dir = f.dir === 'asc' ? 1 : -1;
  const cmp = {
    title: (a, b) => a.title.localeCompare(b.title, 'it'),
    genre: (a, b) => ((a.genres || [])[0] || '').localeCompare((b.genres || [])[0] || '', 'it'),
    rating: (a, b) => (a.rating || 0) - (b.rating || 0),
    addedAt: (a, b) => a.addedAt.localeCompare(b.addedAt),
  }[f.sort || 'addedAt'];
  entries = entries.sort((a, b) => cmp(a, b) * dir);

  const status = $('#library-status');
  if (!state.library.length) status.textContent = MSG.libraryEmpty;
  else if (!entries.length) status.textContent = MSG.noMatch;
  else status.textContent = `${entries.length} di ${state.library.length} contenuti`;

  const options = (current) => Object.entries(STATUS_LABEL)
    .map(([v, l]) => `<option value="${v}" ${v === current ? 'selected' : ''}>${l}</option>`).join('');

  $('#library').innerHTML = entries.map((e) => `
    <article class="card" data-entry="${e.id}">
      ${posterHtml(e.posterUrl)}
      <div class="card-body">
        <div class="card-title">${escapeHtml(e.title)}</div>
        <div class="card-meta">${badge(e.type)} ${e.year ? `<span>${e.year}</span>` : ''} ${starsHtml(e.rating)}</div>
        ${statusHtml(e.status)}
        ${progressHtml(e)}
        <select class="status-select ${e.status}" data-status aria-label="Stato di visione">${options(e.status)}</select>
        <div class="row"><button class="danger" data-remove>Rimuovi</button></div>
      </div>
    </article>`).join('');
}

/* ------------------------------------------------------- dashboard (UC-19) */

async function loadStats() {
  const status = $('#stats-status');
  status.textContent = 'Calcolo in corso...';
  try {
    const s = await api('/api/stats');
    status.textContent = `${s.libraryCount} contenuti in libreria, ${s.completedCount} completati`;
    renderStats($('#stats'), s);
  } catch (err) {
    status.textContent = '';
    toast(err.message, true);
  }
}

function renderStats(root, s) {
  const na = `<span class="muted">${MSG.notAvailable}</span>`;
  const avg = (v) => (v == null ? na : `${v.toFixed(1)} ★`);
  const maxGenre = Math.max(1, ...s.genres.map((g) => g.count));
  const maxMonth = Math.max(1, ...s.completedByMonth.map((m) => m.count));
  root.innerHTML = `
    <div class="tiles">
      <div class="tile"><div class="label">Tempo totale di visione</div>
        <div class="value">${s.totalMinutes ? escapeHtml(s.watchTimeLabel) : na}</div>
        ${s.withoutRuntime ? `<div class="sub">${s.withoutRuntime} contenut${s.withoutRuntime === 1 ? 'o escluso' : 'i esclusi'}: durata non indicata dal catalogo</div>` : ''}
      </div>
      <div class="tile"><div class="label">Genere preferito</div>
        <div class="value">${s.favoriteGenres.length ? escapeHtml(s.favoriteGenres.join(', ')) : na}</div>
        ${s.favoriteGenres.length > 1 ? '<div class="sub">a pari merito</div>' : ''}
      </div>
      <div class="tile"><div class="label">Rating medio</div>
        <div class="value">${avg(s.averageRating)}</div>
        <div class="sub">Film ${avg(s.averageRatingByType.FILM)} &middot; Serie TV ${avg(s.averageRatingByType.SERIE_TV)} &middot; Anime ${avg(s.averageRatingByType.ANIME)}</div>
      </div>
      <div class="tile"><div class="label">Titoli completati</div>
        <div class="value">${s.completedCount}</div>
        <div class="sub">su ${s.libraryCount} in libreria</div>
      </div>
    </div>
    <div class="charts">
      <div class="chart"><h3>Distribuzione per genere</h3>
        ${s.genres.length ? `<div class="bars">${s.genres.map((g) => `<div class="bar"><span>${escapeHtml(g.genre)}</span><div class="track"><span style="width:${(g.count / maxGenre) * 100}%"></span></div><span class="n">${g.count}</span></div>`).join('')}</div>` : na}
      </div>
      <div class="chart"><h3>Titoli completati mese per mese</h3>
        ${s.completedByMonth.length ? `<div class="bars">${s.completedByMonth.map((m) => `<div class="bar"><span>${formatMonth(m.month)}</span><div class="track"><span style="width:${(m.count / maxMonth) * 100}%"></span></div><span class="n">${m.count}</span></div>`).join('')}</div>` : na}
      </div>
      <div class="chart"><h3>Serie in attesa di nuovi episodi</h3>
        ${s.waiting.length ? `<ul class="waiting">${s.waiting.map((w) => `<li data-entry="${w.entryId}">
            ${w.posterUrl ? `<img class="thumb" src="${escapeHtml(w.posterUrl)}" alt="">` : '<span class="thumb"></span>'}
            <span class="t">${escapeHtml(w.title)}<small>${w.status === 'DATA' ? formatDate(w.nextDate) : 'Prossimamente'} &middot; ${w.watchedEpisodes}${w.episodeCount ? '/' + w.episodeCount : ''} episodi visti</small></span>
            ${badge(w.type)}
          </li>`).join('')}</ul>` : '<span class="muted">Nessuna serie in attesa</span>'}
      </div>
    </div>`;
}

/* ------------------------------------------ profilo e backup (UC-04, 20, 21) */

function renderVisibilityForm() {
  const u = state.user;
  if (!u) return;
  const form = $('#visibility-form');
  $(`[name=publicProfile][value=${u.publicProfile}]`, form).checked = true;
  form.showLibrary.checked = u.showLibrary;
  form.showStats.checked = u.showStats;
  form.showReviews.checked = u.showReviews;
  $('#sections').disabled = !u.publicProfile;
  $('#profile-link').classList.toggle('hidden', !u.publicProfile);
  $('#profile-url').value = `${location.origin}${u.profilePath}`;
}

async function saveVisibility(e) {
  e.preventDefault();
  const form = e.target;
  $$('[data-error]', form).forEach((p) => { p.textContent = ''; });
  const body = {
    publicProfile: form.publicProfile.value === 'true',
    showLibrary: form.showLibrary.checked,
    showStats: form.showStats.checked,
    showReviews: form.showReviews.checked,
  };
  if (body.publicProfile && !body.showLibrary && !body.showStats && !body.showReviews) {
    $('[data-error=sections]', form).textContent = 'Seleziona almeno una sezione da mostrare nel profilo pubblico';
    return;
  }
  try {
    state.user = await api('/api/auth/me/visibility', { method: 'PATCH', body });
    renderVisibilityForm();
    toast(body.publicProfile ? 'Profilo pubblico: il link è attivo' : 'Profilo privato: il link non è più raggiungibile');
  } catch (err) {
    showFieldErrors(form, err);
  }
}

/** UC-20: il file viene scaricato dal browser. */
async function exportBackup() {
  try {
    const res = await fetch('/api/backup/export', { credentials: 'same-origin' });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      throw new Error(data.message || 'Non è stato possibile creare il backup: riprova più tardi');
    }
    const name = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') || '')?.[1] || 'watchtracker-backup.json';
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = Object.assign(document.createElement('a'), { href: url, download: name });
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    toast(state.library.length ? 'Backup scaricato' : 'Il backup contiene solo le impostazioni: la libreria è vuota');
  } catch (err) {
    toast(err.message, true);
  }
}

/** UC-21 passi 1–4: scelta del file, validazione e riepilogo. */
async function previewBackup(file) {
  const form = $('#import-form');
  const error = $('[data-error=file]', form);
  error.textContent = '';
  $('#import-summary').classList.add('hidden');
  state.pendingBackup = null;
  if (!file) return;
  if (file.size > MAX_BACKUP_BYTES) { error.textContent = MSG.fileTooLarge; return; }
  const text = await file.text();
  try {
    JSON.parse(text);
  } catch {
    error.textContent = MSG.fileInvalid;
    return;
  }
  try {
    const s = await api('/api/backup/preview', { method: 'POST', body: text });
    state.pendingBackup = text;
    $('#import-summary-list').innerHTML = `
      <li>Esportato da <strong>${escapeHtml(s.exportedBy || '?')}</strong> il ${formatDate(s.exportedAt, { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</li>
      <li>${s.entries} contenut${s.entries === 1 ? 'o' : 'i'}, di cui ${s.alreadyPresent} già present${s.alreadyPresent === 1 ? 'e' : 'i'} nella tua libreria (saranno fusi senza duplicati)</li>
      <li>${s.watchedEpisodes} episodi visti</li>
      <li>${s.ratings} rating e ${s.reviews} recensioni</li>
      ${s.settingsOnly ? '<li class="muted">Il backup contiene solo le impostazioni: la libreria era vuota</li>' : ''}`;
    $('#import-summary').classList.remove('hidden');
  } catch (err) {
    error.textContent = err.fields?.file || err.message;
  }
}

/** UC-21 passi 5–7: importazione dopo la conferma. */
async function importBackup(e) {
  e.preventDefault();
  if (!state.pendingBackup) return;
  const settings = $('#import-settings').checked;
  try {
    const r = await api(`/api/backup/import?settings=${settings}`, { method: 'POST', body: state.pendingBackup });
    toast(`Importazione completata: ${r.imported} contenuti importati, ${r.merged} fusi, ${r.watchedEpisodes} episodi aggiunti`);
    cancelImport();
    await loadLibrary(true);
    if (settings) { await loadSession(); renderVisibilityForm(); }
  } catch (err) {
    $('[data-error=file]', $('#import-form')).textContent = err.fields?.file || err.message;
  }
}

function cancelImport() {
  state.pendingBackup = null;
  $('#backup-file').value = '';
  $('#import-summary').classList.add('hidden');
  $('[data-error=file]', $('#import-form')).textContent = '';
}

/* -------------------------------------------------- profilo pubblico (UC-08) */

async function loadPublicProfile(nickname) {
  const root = $('#public-profile');
  root.innerHTML = '<p class="muted">Caricamento...</p>';
  let p;
  try {
    p = await api(`/api/profiles/${encodeURIComponent(nickname)}`);
  } catch (err) {
    root.innerHTML = `<div class="notice">${escapeHtml(err.status === 404 ? MSG.profileUnavailable : err.message)}</div>`;
    return;
  }
  const own = state.user && state.user.nickname.toLowerCase() === p.nickname.toLowerCase();
  root.innerHTML = `
    <div class="public-head">
      <h1>${escapeHtml(p.nickname)}</h1>
      <span class="muted">su WatchTracker dal ${formatDate(p.memberSince)}</span>
      ${own ? '<span class="muted">(questo è il tuo profilo pubblico, visibile a tutti in sola lettura)</span>' : ''}
    </div>
    ${p.showLibrary ? `<section class="public-section"><h2>Libreria</h2>
      ${p.library.length ? `<div class="grid" id="public-library">${p.library.map((e, i) => `
        <article class="card" data-index="${i}">
          ${posterHtml(e.posterUrl)}
          <div class="card-body">
            <div class="card-title">${escapeHtml(e.title)}</div>
            <div class="card-meta">${badge(e.type)} ${e.year ? `<span>${e.year}</span>` : ''} ${starsHtml(e.rating)}</div>
            ${statusHtml(e.status)}
            ${progressHtml(e)}
          </div>
        </article>`).join('')}</div>` : '<p class="muted">La libreria è vuota.</p>'}
    </section>` : ''}
    ${p.showStats ? `<section class="public-section"><h2>Statistiche</h2><div id="public-stats"></div></section>` : ''}
    ${p.showReviews ? `<section class="public-section"><h2>Recensioni</h2>
      ${p.reviews.length ? `<div class="reviews">${p.reviews.map((r) => `
        <article class="review">
          ${r.posterUrl ? `<img class="thumb" src="${escapeHtml(r.posterUrl)}" alt="">` : '<span class="thumb"></span>'}
          <div class="body">
            <div class="head"><strong>${escapeHtml(r.title)}</strong> ${badge(r.type)} ${starsHtml(r.rating)}
              <span class="muted">${formatDate(r.reviewUpdatedAt)}</span></div>
            <p class="text">${escapeHtml(r.review)}</p>
          </div>
        </article>`).join('')}</div>` : '<p class="muted">Nessuna recensione pubblicata.</p>'}
    </section>` : ''}`;
  if (p.showStats) renderStats($('#public-stats'), p.stats);
  $('#public-library')?.addEventListener('click', (e) => {
    const card = e.target.closest('.card');
    if (!card) return;
    const entry = p.library[Number(card.dataset.index)];
    openDetail(entry.source, entry.externalId);
  });
}

/* ------------------------------------------------------------ navigazione */

function route() {
  const hash = location.hash || '#/search';
  const publicMatch = /^#\/u\/([^/]+)$/.exec(hash);
  if (publicMatch) {
    showView('public');
    loadPublicProfile(decodeURIComponent(publicMatch[1]));
    return;
  }
  // link diretto a una scheda contenuto (UC-07), ad esempio da un profilo pubblico condiviso
  const contentMatch = /^#\/content\/(TMDB_MOVIE|TMDB_TV|MAL)\/([^/]+)$/.exec(hash);
  if (contentMatch) {
    if (state.view !== 'public') showView('search');
    openDetail(contentMatch[1], decodeURIComponent(contentMatch[2]));
    return;
  }
  const view = /^#\/(search|library|stats|profile)$/.exec(hash)?.[1] || 'search';
  if (view !== 'search' && !state.user) {
    showView('search');
    openAuth('login');
    return;
  }
  showView(view);
  if (view === 'library') loadLibrary();
  if (view === 'stats') loadStats();
  if (view === 'profile') { renderVisibilityForm(); cancelImport(); }
}

function showView(view) {
  state.view = view;
  $$('.view').forEach((v) => v.classList.toggle('hidden', v.id !== `view-${view}`));
  $$('.nav-link').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
}

document.addEventListener('DOMContentLoaded', async () => {
  $$('[data-close]').forEach((b) => b.addEventListener('click', () => b.closest('dialog').close()));
  $$('.tab').forEach((t) => t.addEventListener('click', () => switchTab(t.dataset.tab)));
  $('#detail').addEventListener('close', () => { state.detail = null; });

  bindAuthForm($('#login-form'), '/api/auth/login', 'Bentornato!');
  bindAuthForm($('#register-form'), '/api/auth/register', 'Account creato, benvenuto!', validateRegister);

  $('#search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    search($('#search-input').value.trim());
  });
  $('#results').addEventListener('click', (e) => {
    const card = e.target.closest('.card');
    if (!card) return;
    const c = state.results[Number(card.dataset.index)];
    openDetail(c.source, c.externalId);
  });

  $('#library').addEventListener('change', (e) => {
    if (!e.target.matches('[data-status]')) return;
    const entry = state.library.find((x) => x.id === Number(e.target.closest('.card').dataset.entry));
    changeStatus(entry, e.target.value, false);
  });
  $('#library').addEventListener('click', (e) => {
    const card = e.target.closest('.card');
    if (!card) return;
    const entry = state.library.find((x) => x.id === Number(card.dataset.entry));
    if (e.target.matches('[data-remove]')) removeEntry(entry, false);
    else if (!e.target.matches('select, option')) openDetail(entry.source, entry.externalId);
  });
  $('#library-filters').addEventListener('change', renderLibrary);
  $('#library-reset').addEventListener('click', () => { $('#library-filters').reset(); renderLibrary(); });

  $('#stats').addEventListener('click', (e) => {
    const li = e.target.closest('li[data-entry]');
    if (!li) return;
    const entry = state.library.find((x) => x.id === Number(li.dataset.entry));
    if (entry) openDetail(entry.source, entry.externalId);
  });

  $('#visibility-form').addEventListener('submit', saveVisibility);
  $('#visibility-form').addEventListener('change', (e) => {
    if (e.target.name === 'publicProfile') $('#sections').disabled = e.target.value !== 'true';
  });
  $('#copy-link').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText($('#profile-url').value);
      toast('Link copiato');
    } catch {
      $('#profile-url').select();
      toast('Seleziona e copia il link');
    }
  });
  $('#export-backup').addEventListener('click', exportBackup);
  $('#backup-file').addEventListener('change', (e) => previewBackup(e.target.files[0]));
  $('#import-form').addEventListener('submit', importBackup);
  $('#import-cancel').addEventListener('click', cancelImport);

  window.addEventListener('hashchange', route);

  await loadSession();
  await loadLibrary(true);
  route();
});
