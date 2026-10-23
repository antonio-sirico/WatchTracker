const TYPE_LABEL = { FILM: 'Film', SERIE_TV: 'Serie TV', ANIME: 'Anime' };
const SOURCE_LABEL = { TMDB_MOVIE: 'TMDB', TMDB_TV: 'TMDB', MAL: 'MyAnimeList' };
const STATUS_LABEL = {
  DA_VEDERE: 'Da vedere',
  IN_CORSO: 'In corso',
  COMPLETATO: 'Completato',
  ABBANDONATO: 'Abbandonato',
};

const state = {
  user: null,
  results: [],
  library: [],
  view: 'search',
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    credentials: 'same-origin',
    ...options,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
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
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.classList.toggle('err', isError);
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 3000);
}

function posterHtml(url, title) {
  return url
    ? `<div class="poster"><img src="${escapeHtml(url)}" alt="" loading="lazy"></div>`
    : `<div class="poster">Nessuna locandina</div>`;
}

function badge(type) {
  return `<span class="badge ${type}">${TYPE_LABEL[type] || type}</span>`;
}

function renderSession() {
  const el = $('#session');
  if (state.user) {
    el.innerHTML = `
      <span class="nick">${escapeHtml(state.user.nickname)}</span>
      <button id="logout">Esci</button>`;
    $('#logout').addEventListener('click', logout);
  } else {
    el.innerHTML = `<button class="primary" id="open-auth">Accedi</button>`;
    $('#open-auth').addEventListener('click', () => openAuth('login'));
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
  showView('search');
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

function bindAuthForm(form, path, successMessage) {
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorEl = $('[data-error]', form);
    errorEl.textContent = '';
    const body = Object.fromEntries(new FormData(form).entries());
    try {
      state.user = await api(path, { method: 'POST', body });
      form.reset();
      $('#auth').close();
      renderSession();
      toast(successMessage);
      if (state.view === 'library') loadLibrary();
    } catch (err) {
      const details = Object.values(err.fields || {});
      errorEl.textContent = details.length ? details.join(' ') : err.message;
    }
  });
}

async function search(query) {
  const status = $('#search-status');
  const warnings = $('#search-warnings');
  status.textContent = 'Ricerca in corso...';
  warnings.innerHTML = '';
  $('#results').innerHTML = '';
  try {
    const data = await api(`/api/catalog/search?q=${encodeURIComponent(query)}`);
    state.results = data.results;
    warnings.innerHTML = data.warnings.map((w) => `<div class="warning">${escapeHtml(w)}</div>`).join('');
    status.textContent = data.results.length
      ? `${data.results.length} risultati per "${data.query}"`
      : `Nessun risultato per "${data.query}"`;
    renderResults();
  } catch (err) {
    status.textContent = '';
    toast(err.message, true);
  }
}

function renderResults() {
  $('#results').innerHTML = state.results.map((c, i) => `
    <article class="card" data-index="${i}">
      ${posterHtml(c.posterUrl, c.title)}
      <div class="card-body">
        <div class="card-title">${escapeHtml(c.title)}</div>
        <div class="card-meta">
          ${badge(c.type)}
          ${c.year ? `<span>${c.year}</span>` : ''}
          <span>${escapeHtml(c.sourceName || SOURCE_LABEL[c.source])}</span>
        </div>
      </div>
    </article>`).join('');
}

async function openDetail(source, id) {
  const dialog = $('#detail');
  const body = $('#detail-body');
  body.innerHTML = '<p class="muted">Caricamento...</p>';
  dialog.showModal();
  try {
    const d = await api(`/api/catalog/${source}/${encodeURIComponent(id)}`);
    renderDetail(d);
  } catch (err) {
    body.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
  }
}

function renderDetail(d) {
  const inLibrary = state.library.find((e) => e.source === d.source && e.externalId === d.externalId);
  const meta = [];
  if (d.year) meta.push(d.year);
  if (d.runtimeMinutes) meta.push(d.type === 'FILM' ? `${d.runtimeMinutes} min` : `${d.runtimeMinutes} min/episodio`);
  if (d.seasonCount) meta.push(`${d.seasonCount} stagion${d.seasonCount === 1 ? 'e' : 'i'}`);
  if (d.episodeCount) meta.push(`${d.episodeCount} episodi`);
  if (d.status) meta.push(d.status);

  $('#detail-body').innerHTML = `
    ${posterHtml(d.posterUrl, d.title)}
    <div>
      <h2>${escapeHtml(d.title)}</h2>
      ${d.originalTitle && d.originalTitle !== d.title ? `<p class="orig">${escapeHtml(d.originalTitle)}</p>` : ''}
      <div class="meta">${badge(d.type)} ${meta.map((m) => `<span>${escapeHtml(m)}</span>`).join('<span>&middot;</span>')}</div>
      <div class="genres">${d.genres.map((g) => `<span class="genre">${escapeHtml(g)}</span>`).join('')}</div>
      <p class="overview">${escapeHtml(d.overview || 'Trama non disponibile.')}</p>
      ${d.seasons.length > 1 ? `<ul class="seasons">${d.seasons.map((s) => `<li>${escapeHtml(s.name || 'Stagione ' + s.number)}: ${s.episodeCount} ep.</li>`).join('')}</ul>` : ''}
      <div class="actions">
        ${state.user
          ? inLibrary
            ? `<span class="muted">In libreria: ${STATUS_LABEL[inLibrary.status]}</span>`
            : `<button class="primary" id="add-to-library">Aggiungi alla libreria</button>`
          : `<button class="primary" id="login-to-add">Accedi per aggiungere alla libreria</button>`}
        <span class="muted">Fonte: ${escapeHtml(d.sourceName || SOURCE_LABEL[d.source])}</span>
      </div>
    </div>`;

  $('#add-to-library')?.addEventListener('click', () => addToLibrary(d));
  $('#login-to-add')?.addEventListener('click', () => { $('#detail').close(); openAuth('login'); });
}

async function addToLibrary(d) {
  try {
    const entry = await api('/api/library', {
      method: 'POST',
      body: { source: d.source, externalId: d.externalId, type: d.type, title: d.title, year: d.year, posterUrl: d.posterUrl },
    });
    state.library.unshift(entry);
    toast(`"${d.title}" aggiunto alla libreria`);
    renderDetail(d);
  } catch (err) {
    toast(err.message, true);
  }
}

async function loadLibrary() {
  if (!state.user) return;
  const status = $('#library-status');
  status.textContent = 'Caricamento...';
  try {
    state.library = await api('/api/library');
    renderLibrary();
  } catch (err) {
    status.textContent = '';
    toast(err.message, true);
  }
}

function renderLibrary() {
  const filter = $('#library-filter').value;
  const entries = state.library.filter((e) => !filter || e.status === filter);
  $('#library-status').textContent = state.library.length
    ? `${entries.length} di ${state.library.length} contenuti`
    : 'La tua libreria è vuota: cerca un titolo e aggiungilo.';

  const options = (current) => Object.entries(STATUS_LABEL)
    .map(([v, l]) => `<option value="${v}" ${v === current ? 'selected' : ''}>${l}</option>`).join('');

  $('#library').innerHTML = entries.map((e) => `
    <article class="card" data-entry="${e.id}">
      ${posterHtml(e.posterUrl, e.title)}
      <div class="card-body">
        <div class="card-title">${escapeHtml(e.title)}</div>
        <div class="card-meta">${badge(e.type)} ${e.year ? `<span>${e.year}</span>` : ''}</div>
        <select data-status>${options(e.status)}</select>
        <div class="row"><button class="danger" data-remove>Rimuovi</button></div>
      </div>
    </article>`).join('');
}

async function updateStatus(entryId, status) {
  try {
    const updated = await api(`/api/library/${entryId}/status`, { method: 'PATCH', body: { status } });
    state.library = state.library.map((e) => (e.id === updated.id ? updated : e));
    renderLibrary();
    toast(`Stato aggiornato: ${STATUS_LABEL[status]}`);
  } catch (err) {
    toast(err.message, true);
  }
}

async function removeEntry(entryId) {
  try {
    await api(`/api/library/${entryId}`, { method: 'DELETE' });
    state.library = state.library.filter((e) => e.id !== entryId);
    renderLibrary();
    toast('Rimosso dalla libreria');
  } catch (err) {
    toast(err.message, true);
  }
}

function showView(view) {
  if (view === 'library' && !state.user) {
    openAuth('login');
    return;
  }
  state.view = view;
  $$('.view').forEach((v) => v.classList.toggle('hidden', v.id !== `view-${view}`));
  $$('.nav-link').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  if (view === 'library') loadLibrary();
}

document.addEventListener('DOMContentLoaded', async () => {
  $$('[data-view]').forEach((el) => el.addEventListener('click', (e) => {
    e.preventDefault();
    showView(el.dataset.view);
  }));
  $$('[data-close]').forEach((b) => b.addEventListener('click', () => b.closest('dialog').close()));
  $$('.tab').forEach((t) => t.addEventListener('click', () => switchTab(t.dataset.tab)));

  bindAuthForm($('#login-form'), '/api/auth/login', 'Bentornato!');
  bindAuthForm($('#register-form'), '/api/auth/register', 'Account creato, benvenuto!');

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
    updateStatus(Number(e.target.closest('.card').dataset.entry), e.target.value);
  });
  $('#library').addEventListener('click', (e) => {
    const card = e.target.closest('.card');
    if (!card) return;
    if (e.target.matches('[data-remove]')) {
      removeEntry(Number(card.dataset.entry));
    } else if (!e.target.matches('select, option')) {
      const entry = state.library.find((x) => x.id === Number(card.dataset.entry));
      openDetail(entry.source, entry.externalId);
    }
  });
  $('#library-filter').addEventListener('change', renderLibrary);

  await loadSession();
  if (state.user) {
    try { state.library = await api('/api/library'); } catch { /* ignorato */ }
  }
  showView('search');
});
