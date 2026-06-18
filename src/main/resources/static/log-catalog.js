const state = {
  apps: [],
  selectedApp: "",
  selectedWorker: "",
  sampleSize: 5,
  events: [],
  eventTypeSummaries: [],
  failureSummaries: [],
  search: "",
  selectedCatalogItemKey: ""
};

const els = {
  appSelect: document.querySelector("#appSelect"),
  workerSelect: document.querySelector("#workerSelect"),
  sampleSizeSelect: document.querySelector("#sampleSizeSelect"),
  eventSearchInput: document.querySelector("#eventSearchInput"),
  refreshButton: document.querySelector("#refreshButton"),
  message: document.querySelector("#message"),
  stats: document.querySelector("#stats"),
  events: document.querySelector("#events"),
  template: document.querySelector("#eventTemplate")
};

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message || response.statusText);
  }

  if (response.status === 204) return null;
  return response.json();
}

function setMessage(text) {
  els.message.textContent = text || "";
}

async function loadApps() {
  setMessage("Loading apps...");
  const data = await request("/api/log-catalog/apps");
  state.apps = data.apps || [];
  state.selectedApp = state.selectedApp || state.apps[0]?.appName || "";
  renderAppSelect();

  if (!state.selectedApp) {
    state.events = [];
    renderEvents();
    setMessage("No registered app.");
    return;
  }

  await loadEvents();
}

async function loadEvents() {
  const params = new URLSearchParams();
  if (state.selectedWorker) params.set("workerId", state.selectedWorker);
  params.set("sampleSize", String(state.sampleSize));

  setMessage("Loading catalog...");
  const data = await request(`/api/log-catalog/apps/${encodeURIComponent(state.selectedApp)}/events?${params}`);
  state.selectedWorker = data.selectedWorkerId || "";
  state.events = data.events || [];
  state.eventTypeSummaries = data.eventTypeSummaries || [];
  state.failureSummaries = data.failureSummaries || [];
  renderWorkerSelect(data.workerIds || []);
  renderStats();
  renderEvents();
  setMessage(`${state.events.length} eventName loaded.`);
}

function renderAppSelect() {
  els.appSelect.innerHTML = state.apps.map((app) =>
    `<option value="${escapeHtml(app.appName)}">${escapeHtml(app.appName)}</option>`
  ).join("");
  els.appSelect.value = state.selectedApp;
}

function renderWorkerSelect(workerIds) {
  const options = [`<option value="">All workers</option>`].concat(
    workerIds.map((workerId) => `<option value="${escapeHtml(workerId)}">${escapeHtml(workerId)}</option>`)
  );
  els.workerSelect.innerHTML = options.join("");
  els.workerSelect.value = state.selectedWorker;
}

function renderEvents() {
  const query = state.search.trim().toLowerCase();
  const items = buildCatalogItems(state.events)
    .filter((item) => matchesCatalogSearch(item, query));
  els.events.innerHTML = "";

  if (items.length === 0) {
    els.events.innerHTML = `<p class="message">No API event.</p>`;
    return;
  }

  if (!items.some((item) => item.key === state.selectedCatalogItemKey)) {
    state.selectedCatalogItemKey = items[0].key;
  }

  const layout = document.createElement("div");
  layout.className = "catalog-layout";

  const list = document.createElement("section");
  list.className = "api-list";
  list.setAttribute("aria-label", "API events");
  list.innerHTML = items.map((item) => renderCatalogItemButton(item)).join("");

  const detail = document.createElement("section");
  detail.className = "api-detail";
  detail.setAttribute("aria-live", "polite");

  layout.appendChild(list);
  layout.appendChild(detail);
  els.events.appendChild(layout);

  list.querySelectorAll("button[data-item-key]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedCatalogItemKey = button.dataset.itemKey;
      renderEvents();
    });
  });

  const selected = items.find((item) => item.key === state.selectedCatalogItemKey) || items[0];
  renderCatalogDetail(detail, selected);
}

function buildCatalogItems(events) {
  return events.flatMap((event) => {
    const hints = Array.isArray(event.discoveredHints) ? event.discoveredHints : [];
    if (hints.length === 0) {
      return [createCatalogItem(event, null, 0)];
    }
    return hints.map((hint, index) => createCatalogItem(event, hint, index));
  });
}

function createCatalogItem(event, hint, index) {
  const specHint = hint?.specHint || {};
  const apiMethod = specHint.apiMethod || event.apiContext?.method || "";
  const apiPath = specHint.apiPath || event.apiContext?.path || "";
  const api = [apiMethod, apiPath].filter(Boolean).join(" ");
  const source = hint ? `${hint.sourceClass}.${hint.sourceMethod}` : "";
  const title = api || event.eventName;
  const subtitle = source || event.description || "No API hint";
  const key = [
    event.eventName,
    apiMethod,
    apiPath,
    hint?.sourceClass,
    hint?.sourceMethod,
    hint?.appVersion,
    index
  ].filter(Boolean).join("::");

  return { key, event, hint, title, subtitle };
}

function matchesCatalogSearch(item, query) {
  if (!query) return true;
  return [
    item.event.eventName,
    item.title,
    item.subtitle,
    item.event.specStatus
  ].some((value) => String(value || "").toLowerCase().includes(query));
}

function renderCatalogItemButton(item) {
  const selected = item.key === state.selectedCatalogItemKey ? "selected" : "";
  const appVersion = item.hint?.appVersion ? `<span>${escapeHtml(item.hint.appVersion)}</span>` : "";
  return `
    <button class="api-item ${selected}" type="button" data-item-key="${escapeHtml(item.key)}">
      <span class="api-item-title">${escapeHtml(item.title)}</span>
      <span class="api-item-event">${escapeHtml(item.event.eventName)}</span>
      <span class="api-item-source">${escapeHtml(item.subtitle)}</span>
      <span class="api-item-meta">
        <span>${escapeHtml(item.event.specStatus)}</span>
        ${appVersion}
      </span>
    </button>
  `;
}

function renderCatalogDetail(container, item) {
  const event = item.event;
  const node = els.template.content.cloneNode(true);
  node.querySelector("h2").textContent = event.eventName;
  renderApiContext(node.querySelector(".api-context"), apiContextForItem(item));
  node.querySelector(".description").textContent = descriptionForItem(item);
  renderBadges(node.querySelector(".badges"), event);
  renderEventActions(node.querySelector(".event-actions"), event);
  renderFields(node.querySelector(".fields"), event.fields || []);
  renderHints(node.querySelector(".hints"), item.hint ? [item.hint] : event.discoveredHints || []);
  renderSamples(node.querySelector(".samples"), event.samples || []);
  renderRequests(node.querySelector(".requests"), event);
  bindRequestForm(node.querySelector(".request-form"), node.querySelector(".toggle-request-form"), event);
  container.replaceChildren(node);
}

function renderEventActions(container, event) {
  const params = new URLSearchParams();
  if (state.selectedApp) params.set("appName", state.selectedApp);
  if (state.selectedWorker) params.set("workerId", state.selectedWorker);
  params.set("eventType", "LOG_EVENT");
  params.set("eventName", event.eventName);
  container.innerHTML = `<a class="button-link" href="/raw-events?${params.toString()}">View Logs / CSV</a>`;
}

function apiContextForItem(item) {
  const specHint = item.hint?.specHint || {};
  if (specHint.apiMethod || specHint.apiPath || specHint.apiDescription) {
    return {
      method: specHint.apiMethod,
      path: specHint.apiPath,
      description: specHint.apiDescription
    };
  }
  return item.event.apiContext;
}

function descriptionForItem(item) {
  return item.hint?.specHint?.description || item.event.description || "";
}

function renderHints(container, hints) {
  if (hints.length === 0) {
    container.innerHTML = `<span class="field">No annotation hint</span>`;
    return;
  }

  container.innerHTML = hints.map((hint) => {
    const specHint = hint.specHint || {};
    const source = `${hint.sourceClass}.${hint.sourceMethod}`;
    const api = [specHint.apiMethod, specHint.apiPath].filter(Boolean).join(" ");
    const fields = Array.isArray(specHint.fields) ? specHint.fields : [];
    return `
      <div class="hint-card">
        <p class="api-endpoint">${escapeHtml(source)}</p>
        ${hint.appVersion ? `<p class="description">appVersion ${escapeHtml(hint.appVersion)}</p>` : ""}
        ${api ? `<p class="api-endpoint">${escapeHtml(api)}</p>` : ""}
        ${specHint.apiDescription ? `<p class="api-description">${escapeHtml(specHint.apiDescription)}</p>` : ""}
        ${specHint.description ? `<p class="description">${escapeHtml(specHint.description)}</p>` : ""}
        <div class="hint-fields">${renderHintFields(fields)}</div>
      </div>
    `;
  }).join("");
}

function renderHintFields(fields) {
  if (fields.length === 0) return `<span class="field">No field hint</span>`;
  return fields.map((field) => {
    const required = field.required === false ? "optional" : "required";
    const nested = Array.isArray(field.nestedFields) && field.nestedFields.length > 0
      ? `<div class="hint-nested">${renderHintFields(field.nestedFields)}</div>`
      : "";
    return `
      <div class="field-row">
        <span class="field">${escapeHtml(field.name)} · ${escapeHtml(field.type || "STRING")} · ${required}</span>
        ${field.description ? `<p class="field-description">${escapeHtml(field.description)}</p>` : ""}
        ${nested}
      </div>
    `;
  }).join("");
}

function renderStats() {
  const eventStats = state.eventTypeSummaries.map((item) => `
    <div class="stat-card">
      <p>${escapeHtml(item.eventType)}</p>
      <strong>${item.count}</strong>
      <p class="description">errors ${item.errorCount}</p>
    </div>
  `);
  const failureStats = state.failureSummaries.map((item) => `
    <div class="stat-card">
      <p>${escapeHtml(item.reasonCode)}</p>
      <strong>${item.count}</strong>
      <p class="description">ingest failures</p>
    </div>
  `);

  els.stats.innerHTML = eventStats.concat(failureStats).join("");
}

function renderBadges(container, event) {
  const badges = [];
  const statusClass = event.specStatus === "REGISTERED" ? "ok" : "warn";
  badges.push(`<span class="badge ${statusClass}">${escapeHtml(event.specStatus)}</span>`);
  for (const mismatch of event.mismatches || []) {
    badges.push(`<span class="badge warn">${escapeHtml(mismatch.code)}: ${escapeHtml(mismatch.fieldName)}</span>`);
  }
  container.innerHTML = badges.join("");
}

function renderApiContext(container, apiContext) {
  if (!apiContext) {
    container.innerHTML = "";
    container.classList.add("hidden");
    return;
  }

  const endpoint = [apiContext.method, apiContext.path].filter(Boolean).join(" ");
  const endpointMarkup = endpoint ? `<p class="api-endpoint">${escapeHtml(endpoint)}</p>` : "";
  const descriptionMarkup = apiContext.description
    ? `<p class="api-description">${escapeHtml(apiContext.description)}</p>`
    : "";

  container.classList.remove("hidden");
  container.innerHTML = endpointMarkup + descriptionMarkup;
}

function renderFields(container, fields) {
  if (fields.length === 0) {
    container.innerHTML = `<span class="field">No spec fields</span>`;
    return;
  }
  container.innerHTML = fields.map((field) => {
    const required = field.required ? "required" : "optional";
    const description = field.description
      ? `<p class="field-description">${escapeHtml(field.description)}</p>`
      : "";
    return `
      <div class="field-row">
        <span class="field">${escapeHtml(field.name)} · ${escapeHtml(field.type)} · ${required}</span>
        ${description}
      </div>
    `;
  }).join("");
}

function renderSamples(container, samples) {
  if (samples.length === 0) {
    container.innerHTML = `<p class="message">아직 샘플 없음</p>`;
    return;
  }

  container.innerHTML = samples.map((sample) => `
    <div>
      <p class="description">${escapeHtml(sample.workerId)} · ${escapeHtml(sample.ts)}</p>
      <pre>${escapeHtml(JSON.stringify(sample.payload, null, 2))}</pre>
    </div>
  `).join("");
}

function renderRequests(container, event) {
  const requests = event.fieldRequests || [];
  if (requests.length === 0) {
    container.innerHTML = `<span class="field">No field request</span>`;
    return;
  }

  const openRequests = requests.filter((request) => request.status === "REQUESTED" || request.status === "ACCEPTED");
  const closedRequests = requests.filter((request) => request.status === "DONE" || request.status === "REJECTED");
  const renderRequest = (request) => {
    const isClosed = request.status === "DONE" || request.status === "REJECTED";
    const controls = isClosed ? "" : `
      <select data-request-id="${request.id}">
        <option value="">Change status</option>
        <option value="ACCEPTED">ACCEPTED</option>
        <option value="DONE">DONE</option>
        <option value="REJECTED">REJECTED</option>
      </select>
    `;
    return `
      <span class="request">
        ${escapeHtml(request.requestedFieldName)} · ${escapeHtml(request.requestedType)} · ${escapeHtml(request.status)}
        ${controls}
      </span>
    `;
  };

  const closedMarkup = closedRequests.length === 0 ? "" : `
    <details class="closed-requests">
      <summary>Closed requests (${closedRequests.length})</summary>
      <div class="requests">${closedRequests.map(renderRequest).join("")}</div>
    </details>
  `;

  container.innerHTML = openRequests.map(renderRequest).join("") + closedMarkup;

  container.querySelectorAll("select[data-request-id]").forEach((select) => {
    select.addEventListener("change", async () => {
      if (!select.value) return;
      try {
        await request(`/api/field-requests/${select.dataset.requestId}/status`, {
          method: "PATCH",
          body: JSON.stringify({ status: select.value })
        });
        await loadEvents();
      } catch (error) {
        setMessage(error.message);
      }
    });
  });
}

function bindRequestForm(form, button, event) {
  button.addEventListener("click", () => form.classList.toggle("hidden"));
  form.addEventListener("submit", async (submitEvent) => {
    submitEvent.preventDefault();
    const formData = new FormData(form);
    const payload = {
      appName: state.selectedApp,
      eventName: event.eventName,
      requestedFieldName: formData.get("requestedFieldName"),
      requestedType: formData.get("requestedType"),
      reason: formData.get("reason"),
      requestedBy: formData.get("requestedBy") || null
    };

    try {
      await request("/api/field-requests", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      form.reset();
      form.classList.add("hidden");
      await loadEvents();
    } catch (error) {
      setMessage(error.message);
    }
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

els.appSelect.addEventListener("change", async () => {
  state.selectedApp = els.appSelect.value;
  state.selectedWorker = "";
  await loadEvents().catch((error) => setMessage(error.message));
});

els.workerSelect.addEventListener("change", async () => {
  state.selectedWorker = els.workerSelect.value;
  await loadEvents().catch((error) => setMessage(error.message));
});

els.sampleSizeSelect.addEventListener("change", async () => {
  state.sampleSize = Number(els.sampleSizeSelect.value);
  await loadEvents().catch((error) => setMessage(error.message));
});

els.eventSearchInput.addEventListener("input", () => {
  state.search = els.eventSearchInput.value;
  renderEvents();
});

els.refreshButton.addEventListener("click", () => {
  loadApps().catch((error) => setMessage(error.message));
});

loadApps().catch((error) => setMessage(error.message));
