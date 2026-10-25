// Customer Web Remote Script
(function () {
  let currentState = null;
  let localTickerTimer = null;
  let pollInterval = null;

  // DOM Elements
  const roomTitle = document.getElementById("roomTitle");
  const customerInfo = document.getElementById("customerInfo");
  const sessionStatusChip = document.getElementById("sessionStatusChip");
  const timeRemaining = document.getElementById("timeRemaining");
  const timerProgress = document.getElementById("timerProgress");
  const requestAlertBanner = document.getElementById("requestAlertBanner");
  const reqBannerTitle = document.getElementById("reqBannerTitle");
  const reqBannerDesc = document.getElementById("reqBannerDesc");
  const btnExt1H = document.getElementById("btnExt1H");
  const btnExt2H = document.getElementById("btnExt2H");
  const waSection = document.getElementById("waSection");
  const btnWaOperator = document.getElementById("btnWaOperator");
  const btnVolDown = document.getElementById("btnVolDown");
  const btnVolMute = document.getElementById("btnVolMute");
  const btnVolUp = document.getElementById("btnVolUp");
  const btnOpenYouTube = document.getElementById("btnOpenYouTube");
  const dpadContainer = document.getElementById("dpadContainer");
  const navShortcuts = document.querySelector(".nav-shortcuts");
  const btnNavUp = document.getElementById("btnNavUp");
  const btnNavDown = document.getElementById("btnNavDown");
  const btnNavLeft = document.getElementById("btnNavLeft");
  const btnNavRight = document.getElementById("btnNavRight");
  const btnNavOk = document.getElementById("btnNavOk");
  const btnNavBack = document.getElementById("btnNavBack");
  const btnNavHome = document.getElementById("btnNavHome");
  const toastEl = document.getElementById("toast");
  const pulseDot = document.getElementById("pulseDot");
  const connLabel = document.getElementById("connLabel");

  function vibrate() {
    if (navigator.vibrate) {
      navigator.vibrate(35);
    }
  }

  function showToast(message, duration = 3000) {
    if (!toastEl) return;
    toastEl.textContent = message;
    toastEl.classList.remove("hidden");
    clearTimeout(toastEl._timer);
    toastEl._timer = setTimeout(() => {
      toastEl.classList.add("hidden");
    }, duration);
  }

  async function fetchStatus() {
    try {
      const res = await fetch("/api/customer/status");
      if (!res.ok) throw new Error("Gagal terhubung");
      const data = await res.json();
      applyCustomerState(data);
      if (pulseDot) pulseDot.style.backgroundColor = "var(--accent-emerald)";
      if (connLabel) connLabel.textContent = "LIVE";
    } catch (e) {
      if (pulseDot) pulseDot.style.backgroundColor = "var(--signal-crimson)";
      if (connLabel) connLabel.textContent = "OFFLINE";
    }
  }

  function applyCustomerState(data) {
    currentState = {
      ...data,
      localReceiveTime: Date.now()
    };

    if (roomTitle) roomTitle.textContent = data.roomName || "Ruang Karaoke";
    if (customerInfo) {
      customerInfo.textContent = data.customerName 
        ? `Tamu: ${data.customerName}` 
        : "Sesi Tamu Ruangan";
    }

    // Status chip
    if (sessionStatusChip) {
      if (data.status === "RUNNING") {
        sessionStatusChip.textContent = "SESI AKTIF";
        sessionStatusChip.style.color = "var(--accent-emerald)";
        sessionStatusChip.style.background = "rgba(16, 185, 129, 0.15)";
        sessionStatusChip.style.borderColor = "rgba(16, 185, 129, 0.3)";
      } else if (data.status === "PAUSED") {
        sessionStatusChip.textContent = "SESI DIJEDA";
        sessionStatusChip.style.color = "var(--accent-amber)";
        sessionStatusChip.style.background = "rgba(245, 158, 11, 0.15)";
        sessionStatusChip.style.borderColor = "rgba(245, 158, 11, 0.3)";
      } else {
        sessionStatusChip.textContent = "SESI SELESAI / SIAP";
        sessionStatusChip.style.color = "var(--text-muted)";
        sessionStatusChip.style.background = "rgba(255, 255, 255, 0.05)";
        sessionStatusChip.style.borderColor = "rgba(255, 255, 255, 0.1)";
      }
    }

    // WhatsApp Operator link
    if (waSection && btnWaOperator) {
      if (data.operatorPhone && data.operatorPhone.trim().length > 5) {
        let cleanPhone = data.operatorPhone.trim().replace(/[^0-9]/g, "");
        if (cleanPhone.startsWith("0")) {
          cleanPhone = "62" + cleanPhone.substring(1);
        }
        const roomEncoded = encodeURIComponent(data.roomName || "Ruang Karaoke");
        const msg = encodeURIComponent(`Halo Operator, saya dari ${data.roomName || "Ruangan"}. Saya membutuhkan bantuan di ruangan...`);
        btnWaOperator.href = `https://wa.me/${cleanPhone}?text=${msg}`;
        waSection.classList.remove("hidden");
      } else {
        waSection.classList.add("hidden");
      }
    }

    // Pending Request Banner
    if (requestAlertBanner) {
      if (data.pendingRequest && data.pendingRequest.status === "PENDING") {
        requestAlertBanner.classList.remove("hidden");
        if (data.pendingRequest.type === "EXTENSION") {
          reqBannerTitle.textContent = `Permintaan Tambah +${data.pendingRequest.requestedMinutes / 60} Jam`;
          reqBannerDesc.textContent = "Menunggu persetujuan kasir/operator...";
        } else {
          reqBannerTitle.textContent = "Panggilan Bantuan Operator";
          reqBannerDesc.textContent = "Kru sedang diberitahu...";
        }
      } else {
        requestAlertBanner.classList.add("hidden");
      }
    }

    // YouTube & D-Pad Navigation availability (Only allow when timer is actively RUNNING)
    const isRunning = (data.status === "RUNNING");
    if (btnOpenYouTube) {
      if (isRunning) {
        btnOpenYouTube.classList.remove("disabled");
      } else {
        btnOpenYouTube.classList.add("disabled");
      }
    }
    if (dpadContainer) {
      if (isRunning) {
        dpadContainer.classList.remove("disabled");
      } else {
        dpadContainer.classList.add("disabled");
      }
    }
    if (navShortcuts) {
      if (isRunning) {
        navShortcuts.classList.remove("disabled");
      } else {
        navShortcuts.classList.add("disabled");
      }
    }

    renderTimer();

    // Start local interpolator
    if (localTickerTimer) clearInterval(localTickerTimer);
    if (data.status === "RUNNING") {
      localTickerTimer = setInterval(renderTimer, 1000);
    }
  }

  function renderTimer() {
    if (!currentState) return;

    let remMs = currentState.remainingMs;
    if (currentState.status === "RUNNING") {
      const elapsed = Date.now() - currentState.localReceiveTime;
      remMs = Math.max(0, currentState.remainingMs - elapsed);
    }

    const totalSec = Math.ceil(remMs / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;

    let displayStr = "";
    if (h > 0) {
      displayStr = `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
    } else {
      displayStr = `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
    }

    if (timeRemaining) {
      timeRemaining.textContent = displayStr;
    }

    // Update progress bar
    if (timerProgress && currentState.totalMs > 0) {
      const pct = Math.min(100, Math.max(0, (remMs / currentState.totalMs) * 100));
      timerProgress.style.width = `${pct}%`;
    }
  }

  // Action handlers
  async function sendCustomerRequest(type, minutes, message) {
    vibrate();
    try {
      showToast("Mengirim permintaan ke kasir...");
      const res = await fetch("/api/customer/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: type,
          requestedMinutes: minutes,
          message: message
        })
      });

      if (!res.ok) throw new Error("Gagal mengirim permintaan");
      const created = await res.json();
      showToast("Permintaan berhasil dikirim ke kasir!");
      fetchStatus();
    } catch (e) {
      showToast(`Error: ${e.message}`);
    }
  }

  async function sendVolume(action) {
    vibrate();
    try {
      await fetch("/api/customer/volume", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: action })
      });
    } catch (e) {
      console.warn("Volume error:", e);
    }
  }

  async function sendNav(action) {
    vibrate();
    if (!currentState || currentState.status !== "RUNNING") {
      showToast("Waktu belum berjalan. Mulai sesi karaoke terlebih dahulu!");
      return;
    }
    try {
      await fetch("/api/customer/nav", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: action })
      });
    } catch (e) {
      console.warn("Nav error:", e);
    }
  }

  async function openTvYouTube() {
    vibrate();
    if (!currentState || currentState.status !== "RUNNING") {
      showToast("Waktu belum berjalan. Hubungi kasir untuk memulai sesi!");
      return;
    }
    try {
      showToast("Membuka YouTube di TV...");
      const res = await fetch("/api/customer/open-youtube", {
        method: "POST",
        headers: { "Content-Type": "application/json" }
      });
      if (res.ok) {
        showToast("YouTube terbuka di TV");
      } else {
        const err = await res.json().catch(() => ({}));
        showToast(err.message || "Gagal membuka YouTube di TV");
      }
    } catch (e) {
      showToast("Gagal membuka YouTube di TV");
    }
  }

  // Setup Event Listeners
  btnExt1H?.addEventListener("click", () => {
    sendCustomerRequest("EXTENSION", 60, "Permintaan tambah waktu 1 jam dari pelanggan");
  });

  btnExt2H?.addEventListener("click", () => {
    sendCustomerRequest("EXTENSION", 120, "Permintaan tambah waktu 2 jam dari pelanggan");
  });

  btnVolDown?.addEventListener("click", () => sendVolume("DOWN"));
  btnVolUp?.addEventListener("click", () => sendVolume("UP"));
  btnVolMute?.addEventListener("click", () => sendVolume("MUTE"));
  btnOpenYouTube?.addEventListener("click", openTvYouTube);

  // D-Pad Navigation Listeners
  btnNavUp?.addEventListener("click", () => sendNav("UP"));
  btnNavDown?.addEventListener("click", () => sendNav("DOWN"));
  btnNavLeft?.addEventListener("click", () => sendNav("LEFT"));
  btnNavRight?.addEventListener("click", () => sendNav("RIGHT"));
  btnNavOk?.addEventListener("click", () => sendNav("OK"));
  btnNavBack?.addEventListener("click", () => sendNav("BACK"));
  btnNavHome?.addEventListener("click", () => sendNav("HOME"));

  // Initial fetch and polling every 2.5 seconds
  fetchStatus();
  pollInterval = setInterval(fetchStatus, 2500);
})();
