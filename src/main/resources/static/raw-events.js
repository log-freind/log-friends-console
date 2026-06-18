const els = {
  appNameInput: document.querySelector("#appNameInput"),
  workerIdInput: document.querySelector("#workerIdInput"),
  eventNameInput: document.querySelector("#eventNameInput"),
  fromInput: document.querySelector("#fromInput"),
  toInput: document.querySelector("#toInput"),
  limitSelect: document.querySelector("#limitSelect"),
  loadButton: document.querySelector("#loadButton"),
  downloadButton: document.querySelector("#downloadButton"),
  message: document.querySelector("#message"),
  rows: document.querySelector("#rows")
};

function setMessage(text) {
  els.message.textContent = text || "";
}

function initializeFilters() {
  const params = new URLSearchParams(window.location.search);
  els.appNameInput.value = params.get("appName") || "";
  els.workerIdInput.value = params.get("workerId") || "";
  els.eventNameInput.value = params.get("eventName") || "";
  els.limitSelect.value = params.get("limit") || "100";

  const now = new Date();
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  const end = new Date(now);
  end.setHours(23, 59, 59, 999);

  els.fromInput.value = toLocalDateTimeInput(params.get("from") ? new Date(params.get("from")) : start);
  els.toInput.value = toLocalDateTimeInput(params.get("to") ? new Date(params.get("to")) : end);
}

function toLocalDateTimeInput(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join("-") + "T" + [
    pad(date.getHours()),
    pad(date.getMinutes())
  ].join(":");
}

function buildParams({ includeLimit }) {
  const params = new URLSearchParams();
  const appName = els.appNameInput.value.trim();
  const workerId = els.workerIdInput.value.trim();
  const eventName = els.eventNameInput.value.trim();
  if (appName) params.set("appName", appName);
  if (workerId) params.set("workerId", workerId);
  if (eventName) params.set("eventName", eventName);
  params.set("from", new Date(els.fromInput.value).toISOString());
  params.set("to", new Date(els.toInput.value).toISOString());
  if (includeLimit) params.set("limit", els.limitSelect.value);
  return params;
}

async function loadRows() {
  try {
    setMessage("Loading raw LOG_EVENT data...");
    const params = buildParams({ includeLimit: true });
    const response = await fetch(`/api/events/custom?${params}`);
    if (!response.ok) {
      throw new Error(await response.text() || response.statusText);
    }
    const rows = await response.json();
    renderRows(rows);
    setMessage(`${rows.length} rows loaded. CSV downloads the full selected date range.`);
  } catch (error) {
    setMessage(error.message);
  }
}

function renderRows(rows) {
  if (rows.length === 0) {
    els.rows.innerHTML = `<tr><td colspan="5" class="empty-cell">No LOG_EVENT data.</td></tr>`;
    return;
  }

  els.rows.innerHTML = rows.map((row) => `
    <tr>
      <td>${escapeHtml(row.timestamp)}</td>
      <td>${escapeHtml(row.appName)}</td>
      <td>${escapeHtml(row.workerId)}</td>
      <td>${escapeHtml(row.eventName)}</td>
      <td><pre>${escapeHtml(JSON.stringify(row.payload, null, 2))}</pre></td>
    </tr>
  `).join("");
}

function downloadCsv() {
  const params = buildParams({ includeLimit: false });
  window.location.href = `/api/events/custom.csv?${params}`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

initializeFilters();
els.loadButton.addEventListener("click", loadRows);
els.downloadButton.addEventListener("click", downloadCsv);
loadRows();
