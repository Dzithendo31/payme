function getInvoiceIdFromPath() {
  // /pay/{invoiceId}
  const parts = window.location.pathname.split("/").filter(Boolean);
  return parts.length >= 2 ? parts[1] : null;
}

function formatMoney(amount, currency) {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(Number(amount));
  } catch {
    return `${amount} ${currency}`;
  }
}

function setStatusPill(el, status) {
  el.textContent = status;
  el.classList.remove("pill-green", "pill-gray", "pill-red", "pill-amber");
  if (status === "SUCCEEDED") el.classList.add("pill-green");
  else if (status === "FAILED" || status === "EXPIRED") el.classList.add("pill-red");
  else if (status === "PENDING") el.classList.add("pill-amber");
  else el.classList.add("pill-gray");
}

async function loadInvoice(invoiceId) {
  const res = await fetch(`/api/invoices/${encodeURIComponent(invoiceId)}`, {
    headers: { "Accept": "application/json" }
  });
  if (!res.ok) throw new Error(`Invoice fetch failed: ${res.status}`);
  return await res.json();
}

async function startCheckout(invoiceId) {
  const res = await fetch(`/pay/${encodeURIComponent(invoiceId)}/checkout`, {
    method: "POST",
    headers: { "Accept": "application/json" }
  });
  if (!res.ok) throw new Error(`Checkout start failed: ${res.status}`);
  return await res.json();
}

(async function init() {
  const invoiceId = getInvoiceIdFromPath();
  const payBtn = document.getElementById("payBtn");
  const refreshBtn = document.getElementById("refreshBtn");

  if (!invoiceId) {
    document.getElementById("description").textContent = "Invalid invoice link.";
    payBtn.disabled = true;
    return;
  }

  async function render() {
    const inv = await loadInvoice(invoiceId);

    document.getElementById("amount").textContent =
      formatMoney(inv.amount ?? inv.money?.amount ?? 0, inv.currency ?? inv.money?.currency ?? "USD");

    document.getElementById("description").textContent = inv.description || "—";
    document.getElementById("createdAt").textContent = inv.createdAt ? new Date(inv.createdAt).toLocaleString() : "—";
    document.getElementById("expiresAt").textContent = inv.expiresAt ? `Expires: ${new Date(inv.expiresAt).toLocaleString()}` : "";

    setStatusPill(document.getElementById("statusPill"), inv.status || "UNKNOWN");

    // Disable pay if not payable
    const payable = inv.isPayable ?? (inv.status === "CREATED");
    payBtn.disabled = !payable;
    payBtn.textContent = payable ? "Pay" : "Not payable";
  }

  refreshBtn.addEventListener("click", async (e) => {
    e.preventDefault();
    refreshBtn.classList.add("loading");
    try { await render(); } finally { refreshBtn.classList.remove("loading"); }
  });

  payBtn.addEventListener("click", async () => {
    payBtn.disabled = true;
    payBtn.textContent = "Starting checkout…";
    try {
      const { checkoutUrl } = await startCheckout(invoiceId);
      window.location.assign(checkoutUrl);
    } catch (e) {
      payBtn.textContent = "Pay";
      payBtn.disabled = false;
      alert("Could not start checkout. Please try again.");
    }
  });

  await render();
})();
