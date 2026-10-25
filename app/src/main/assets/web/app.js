(function() {
  let token = sessionStorage.getItem("admin_token");
  let ws = null;
  let wsReconnectTimer = null;
  let wsReconnectDelay = 1000;
  let currentState = null;
  let localTickerTimer = null;
  let currentTab = "timer"; // "timer", "apps", "history", or "settings"
  let currentHourlyRate = 50000;
  let currentOverlayCorner = "TOP_RIGHT";
  let currentOverlaySize = "MEDIUM";
  let currentStandbyBgMode = "NONE";
  let activeHistoryFilter = "today"; // "today", "week", "month", "all"
  let allRentalsData = [];

  // DOM elements
  const navTabs = document.getElementById("navTabs");
  const tabTimer = document.getElementById("tabTimer");
  const tabApps = document.getElementById("tabApps");
  const tabHistory = document.getElementById("tabHistory");
  const tabSettings = document.getElementById("tabSettings");
  const loginSection = document.getElementById("loginSection");
  const dashboardSection = document.getElementById("dashboardSection");
  const appsSection = document.getElementById("appsSection");
  const historySection = document.getElementById("historySection");
  const settingsSection = document.getElementById("settingsSection");
  const pinInput = document.getElementById("pinInput");
  const loginError = document.getElementById("loginError");
  const btnLogin = document.getElementById("btnLogin");
  const btnLogout = document.getElementById("btnLogout");
  const statusDot = document.getElementById("statusDot");
  const connectionText = document.getElementById("connectionText");
  const roomNameHeader = document.getElementById("roomNameHeader");
  const warningsBanner = document.getElementById("warningsBanner");
  const statusBadge = document.getElementById("statusBadge");
  const activeCustomerBadge = document.getElementById("activeCustomerBadge");
  const activeCustomerName = document.getElementById("activeCustomerName");
  const customerInputSection = document.getElementById("customerInputSection");
  const customerNameInput = document.getElementById("customerNameInput");
  const timerDisplay = document.getElementById("timerDisplay");
  const progressBar = document.getElementById("progressBar");
  const finishNotice = document.getElementById("finishNotice");
  const btnPauseResume = document.getElementById("btnPauseResume");
  const btnStop = document.getElementById("btnStop");
  const extendControls = document.getElementById("extendControls");
  const appsListContainer = document.getElementById("appsListContainer");
  const btnRefreshApps = document.getElementById("btnRefreshApps");
  const rentalListContainer = document.getElementById("rentalListContainer");
  const btnRefreshHistory = document.getElementById("btnRefreshHistory");
  const btnClearHistory = document.getElementById("btnClearHistory");
  const historyFilterBar = document.getElementById("historyFilterBar");
  const statTotalSessions = document.getElementById("statTotalSessions");
  const statTotalHours = document.getElementById("statTotalHours");
  const statTotalRevenue = document.getElementById("statTotalRevenue");
  const rateDisplayTag = document.getElementById("rateDisplayTag");
  const statAvgDuration = document.getElementById("statAvgDuration");

  // Settings DOM elements
  const settingRoomName = document.getElementById("settingRoomName");
  const settingHourlyRate = document.getElementById("settingHourlyRate");
  const previewRateFormatted = document.getElementById("previewRateFormatted");
  const settingNewPin = document.getElementById("settingNewPin");
  const settingConfirmPin = document.getElementById("settingConfirmPin");
  const btnSaveSettings = document.getElementById("btnSaveSettings");
  const btnRefreshSettings = document.getElementById("btnRefreshSettings");
  const settingsAlert = document.getElementById("settingsAlert");
  const bgModeSelector = document.getElementById("bgModeSelector");
  const panelBgImage = document.getElementById("panelBgImage");
  const panelBgVideo = document.getElementById("panelBgVideo");
  const panelBgYoutube = document.getElementById("panelBgYoutube");
  const bgImgElement = document.getElementById("bgImgElement");
  const bgEmptyPlaceholder = document.getElementById("bgEmptyPlaceholder");
  const bgFileInput = document.getElementById("bgFileInput");
  const btnPickBgFile = document.getElementById("btnPickBgFile");
  const btnDeleteBgImage = document.getElementById("btnDeleteBgImage");
  const bgVideoElement = document.getElementById("bgVideoElement");
  const bgVideoEmptyPlaceholder = document.getElementById("bgVideoEmptyPlaceholder");
  const bgVideoFileInput = document.getElementById("bgVideoFileInput");
  const btnPickBgVideo = document.getElementById("btnPickBgVideo");
  const btnDeleteBgVideo = document.getElementById("btnDeleteBgVideo");
  const videoUploadProgress = document.getElementById("videoUploadProgress");
  const videoProgressBar = document.getElementById("videoProgressBar");
  const videoProgressText = document.getElementById("videoProgressText");
  const settingVideoMute = document.getElementById("settingVideoMute");
  const settingYoutubeUrl = document.getElementById("settingYoutubeUrl");
  const settingYoutubeMute = document.getElementById("settingYoutubeMute");
  const settingOperatorPhone = document.getElementById("settingOperatorPhone");

  function formatWaLink(phone, room) {
    if (!phone) return "#";
    let clean = phone.replace(/[^0-9]/g, "");
    if (clean.startsWith("08")) {
      clean = "62" + clean.substring(1);
    } else if (clean.startsWith("8")) {
      clean = "62" + clean;
    }
    const text = encodeURIComponent(`Halo Operator, saya di ${room || "Ruang Karaoke"} butuh bantuan/sewa.`);
    return `https://wa.me/${clean}?text=${text}`;
  }

  // Tab Switching
  tabTimer.addEventListener("click", () => switchTab("timer"));
  tabApps?.addEventListener("click", () => {
    switchTab("apps");
    loadApps();
  });
  tabHistory.addEventListener("click", () => {
    switchTab("history");
    loadRentalHistory();
  });
  tabSettings?.addEventListener("click", () => {
    switchTab("settings");
    loadSettings();
  });

  btnRefreshApps?.addEventListener("click", loadApps);
  btnRefreshHistory.addEventListener("click", loadRentalHistory);
  btnRefreshSettings?.addEventListener("click", loadSettings);
  btnSaveSettings?.addEventListener("click", saveSettings);

  // Custom Confirmation Modal Helper
  const confirmModal = document.getElementById("confirmModal");
  const modalTitle = document.getElementById("modalTitle");
  const modalMessage = document.getElementById("modalMessage");
  const btnModalCancel = document.getElementById("btnModalCancel");
  const btnModalConfirm = document.getElementById("btnModalConfirm");
  let pendingModalAction = null;

  function showConfirmModal(opts) {
    if (!confirmModal) return;
    if (modalTitle) modalTitle.textContent = opts.title || "Konfirmasi Aksi";
    if (modalMessage) modalMessage.textContent = opts.message || "Apakah Anda yakin ingin melanjutkan tindakan ini?";
    if (btnModalConfirm) {
      btnModalConfirm.textContent = opts.confirmText || "Lanjutkan";
      btnModalConfirm.className = "btn " + (opts.isDanger ? "btn-danger" : "btn-primary");
    }
    pendingModalAction = opts.onConfirm || null;
    confirmModal.classList.remove("hidden");
  }

  function hideConfirmModal() {
    if (!confirmModal) return;
    confirmModal.classList.add("hidden");
    pendingModalAction = null;
  }

  btnModalCancel?.addEventListener("click", hideConfirmModal);
  btnModalConfirm?.addEventListener("click", () => {
    const action = pendingModalAction;
    hideConfirmModal();
    if (typeof action === "function") {
      action();
    }
  });
  confirmModal?.addEventListener("click", (e) => {
    if (e.target === confirmModal) {
      hideConfirmModal();
    }
  });

  btnClearHistory?.addEventListener("click", () => {
    showConfirmModal({
      title: "Hapus Riwayat Sesi?",
      message: "Seluruh arsip riwayat rental dan rekap omset ruangan ini akan dihapus secara permanen dari memori TV.",
      confirmText: "Hapus Semua",
      isDanger: true,
      onConfirm: async () => {
        try {
          await fetch("/api/rentals/clear", {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
          });
          loadRentalHistory();
        } catch (e) {
          alert("Gagal menghapus riwayat: " + e.message);
        }
      }
    });
  });

  // Edit active customer name
  activeCustomerBadge.addEventListener("click", async () => {
    if (!currentState || currentState.status === "IDLE") return;
    const currentName = activeCustomerName.textContent || "";
    const newName = prompt("Ubah nama pelanggan untuk sesi ini:", currentName);
    if (newName !== null && newName.trim() !== "") {
      try {
        const res = await fetch("/api/rental/customer-name", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`
          },
          body: JSON.stringify({ customerName: newName.trim() })
        });
        if (res.ok) {
          const updated = await res.json();
          applyState(updated);
        }
      } catch (e) {
        alert("Gagal mengubah nama pelanggan");
      }
    }
  });

  function switchTab(tab) {
    currentTab = tab;
    tabTimer.classList.toggle("active", tab === "timer");
    tabApps?.classList.toggle("active", tab === "apps");
    tabHistory.classList.toggle("active", tab === "history");
    tabSettings?.classList.toggle("active", tab === "settings");

    dashboardSection.classList.toggle("hidden", tab !== "timer");
    appsSection?.classList.toggle("hidden", tab !== "apps");
    historySection.classList.toggle("hidden", tab !== "history");
    settingsSection?.classList.toggle("hidden", tab !== "settings");
  }

  // Keypad
  document.querySelectorAll(".key-btn[data-val]").forEach(btn => {
    btn.addEventListener("click", () => {
      if (pinInput.value.length < 6) {
        pinInput.value += btn.getAttribute("data-val");
      }
    });
  });

  document.getElementById("btnKeyClear")?.addEventListener("click", () => {
    pinInput.value = "";
    hideError();
  });

  document.getElementById("btnKeyDelete")?.addEventListener("click", () => {
    pinInput.value = pinInput.value.slice(0, -1);
    hideError();
  });

  // Login
  btnLogin.addEventListener("click", performLogin);
  pinInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") performLogin();
  });

  async function performLogin() {
    const pin = pinInput.value.trim();
    if (!pin) {
      showError("Masukkan PIN terlebih dahulu");
      return;
    }

    btnLogin.disabled = true;
    hideError();

    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pin })
      });

      const data = await res.json();
      if (!res.ok) {
        showError(data.error || "Gagal masuk");
        btnLogin.disabled = false;
        return;
      }

      token = data.token;
      sessionStorage.setItem("admin_token", token);
      pinInput.value = "";
      btnLogin.disabled = false;
      showDashboard();
      connectWebSocket();
      loadSettings();
    } catch (e) {
      showError("Koneksi gagal ke server TV");
      btnLogin.disabled = false;
    }
  }

  // Logout
  btnLogout.addEventListener("click", async () => {
    if (token) {
      try {
        await fetch("/api/auth/logout", {
          method: "POST",
          headers: { "Authorization": `Bearer ${token}` }
        });
      } catch (e) {}
    }
    token = null;
    sessionStorage.removeItem("admin_token");
    if (ws) {
      ws.close();
      ws = null;
    }
    showLogin();
  });

  function showError(msg) {
    loginError.textContent = msg;
    loginError.classList.remove("hidden");
  }

  function hideError() {
    loginError.classList.add("hidden");
  }

  function showLogin() {
    loginSection.classList.remove("hidden");
    dashboardSection.classList.add("hidden");
    appsSection?.classList.add("hidden");
    historySection.classList.add("hidden");
    navTabs.classList.add("hidden");
    btnLogout.classList.add("hidden");
    statusDot.className = "status-dot offline";
    connectionText.textContent = "Offline";
  }

  function showDashboard() {
    loginSection.classList.add("hidden");
    navTabs.classList.remove("hidden");
    btnLogout.classList.remove("hidden");
    switchTab(currentTab);
  }

  // WebSocket Connection
  function connectWebSocket() {
    if (!token) return;
    if (ws) {
      ws.close();
      ws = null;
    }

    clearTimeout(wsReconnectTimer);

    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${location.host}/ws?token=${encodeURIComponent(token)}`;

    connectionText.textContent = "Menghubungkan...";
    statusDot.className = "status-dot";

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      wsReconnectDelay = 1000;
      statusDot.className = "status-dot online";
      connectionText.textContent = "Terhubung";
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.status) {
          applyState(msg.status);
          if (currentTab === "history") {
            loadRentalHistory();
          }
        }
      } catch (e) {}
    };

    ws.onclose = () => {
      statusDot.className = "status-dot offline";
      connectionText.textContent = "Terputus";
      // Reconnect with backoff
      wsReconnectTimer = setTimeout(() => {
        wsReconnectDelay = Math.min(wsReconnectDelay * 1.5, 10000);
        if (token) connectWebSocket();
      }, wsReconnectDelay);
    };

    ws.onerror = () => {
      ws.close();
    };
  }

  // State application and local ticking
  function applyState(state) {
    currentState = {
      ...state,
      localReceiveTime: Date.now()
    };

    roomNameHeader.textContent = state.roomName || "Ruang Karaoke";
    renderWarnings(state.warnings || []);
    renderTimer();

    // Active customer badge
    if (state.customerName && state.status !== "IDLE") {
      activeCustomerName.textContent = state.customerName;
      activeCustomerBadge.classList.remove("hidden");
      customerInputSection.classList.add("hidden");
    } else {
      activeCustomerBadge.classList.add("hidden");
      customerInputSection.classList.remove("hidden");
    }

    // Start local interpolator
    if (localTickerTimer) clearInterval(localTickerTimer);
    if (state.status === "RUNNING") {
      localTickerTimer = setInterval(renderTimer, 1000);
    }
  }

  function renderTimer() {
    if (!currentState) return;

    let remMs = currentState.remainingMs;
    if (currentState.status === "RUNNING") {
      const elapsedSinceReceive = Date.now() - currentState.localReceiveTime;
      remMs = Math.max(0, currentState.remainingMs - elapsedSinceReceive);
    }

    // Format MM:SS or HH:MM:SS
    const totalSec = Math.ceil(remMs / 1000);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    
    if (h > 0) {
      timerDisplay.textContent = `${pad(h)}:${pad(m)}:${pad(s)}`;
    } else {
      timerDisplay.textContent = `${pad(m)}:${pad(s)}`;
    }

    // Badge & Buttons
    statusBadge.textContent = currentState.status;
    statusBadge.className = `status-badge ${currentState.status}`;

    if (currentState.status === "RUNNING") {
      btnPauseResume.textContent = "Jeda";
      btnPauseResume.className = "btn btn-warning";
      finishNotice.classList.add("hidden");
      extendControls.classList.add("hidden");
    } else if (currentState.status === "PAUSED") {
      btnPauseResume.textContent = "Lanjutkan";
      btnPauseResume.className = "btn btn-primary";
      finishNotice.classList.add("hidden");
      extendControls.classList.add("hidden");
    } else if (currentState.status === "FINISHED") {
      btnPauseResume.textContent = "Mulai Ulang";
      btnPauseResume.className = "btn btn-primary";
      finishNotice.classList.remove("hidden");
      extendControls.classList.remove("hidden");
    } else {
      btnPauseResume.textContent = "Mulai";
      btnPauseResume.className = "btn btn-primary";
      finishNotice.classList.add("hidden");
      extendControls.classList.add("hidden");
    }

    // Progress bar
    if (currentState.totalMs > 0) {
      const pct = Math.min(100, Math.max(0, (remMs / currentState.totalMs) * 100));
      progressBar.style.width = `${pct}%`;
    } else {
      progressBar.style.width = "0%";
    }
  }

  function renderWarnings(warnings) {
    if (!warnings || warnings.length === 0) {
      warningsBanner.classList.add("hidden");
      warningsBanner.innerHTML = "";
      return;
    }
    const msgs = warnings.map(w => {
      if (w === "ADB_ENABLED") return "Debugging ADB aktif! Segera matikan di Opsi Pengembang TV demi keamanan.";
      if (w === "OVERLAY_DENIED") return "Izin overlay belum aktif di TV!";
      if (w === "NOT_DEFAULT_LAUNCHER") return "Aplikasi belum diatur sebagai launcher utama.";
      if (w === "BATTERY_OPTIMIZATION_ACTIVE") return "Optimasi baterai masih aktif untuk aplikasi ini.";
      return `Peringatan sistem: ${w}`;
    });
    warningsBanner.innerHTML = msgs.join("<br>");
    warningsBanner.classList.remove("hidden");
  }

  function pad(n) {
    return n < 10 ? "0" + n : n;
  }

  // REST API actions
  async function callTimerApi(endpoint, body = null) {
    if (!token) return;
    try {
      const res = await fetch(`/api/timer/${endpoint}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: body ? JSON.stringify(body) : null
      });
      if (res.status === 401) {
        // Session expired
        btnLogout.click();
        return;
      }
      const data = await res.json();
      applyState(data);
    } catch (e) {
      console.error(e);
    }
  }

  // Action listeners
  document.querySelectorAll(".btn-preset").forEach(btn => {
    btn.addEventListener("click", () => {
      const minutes = parseInt(btn.getAttribute("data-minutes"), 10);
      const customerName = customerNameInput.value.trim();
      callTimerApi("start", { minutes, customerName });
    });
  });

  btnPauseResume.addEventListener("click", () => {
    if (!currentState) return;
    if (currentState.status === "RUNNING") {
      callTimerApi("pause");
    } else if (currentState.status === "PAUSED") {
      callTimerApi("resume");
    } else if (currentState.status === "FINISHED" || currentState.status === "IDLE") {
      const customerName = customerNameInput.value.trim();
      callTimerApi("start", { minutes: 60, customerName });
    }
  });

  btnStop.addEventListener("click", () => {
    showConfirmModal({
      title: "Hentikan Sesi Timer?",
      message: "Sesi timer yang sedang berjalan akan diakhiri dan tampilan TV akan dikembalikan ke status SIAP. Apakah Anda yakin?",
      confirmText: "Hentikan Sesi",
      isDanger: true,
      onConfirm: () => {
        callTimerApi("stop");
      }
    });
  });

  document.querySelectorAll(".btn-adjust").forEach(btn => {
    btn.addEventListener("click", () => {
      const minutes = parseInt(btn.getAttribute("data-adjust"), 10);
      callTimerApi("adjust", { minutes });
    });
  });

  // Rental History Loader
  function formatRupiah(num) {
    if (isNaN(num)) num = 0;
    return "Rp " + Math.round(num).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  }

  function isWithinFilter(timestamp, filter) {
    if (!timestamp) return false;
    const recordDate = new Date(timestamp);
    const now = new Date();
    if (filter === "today") {
      return recordDate.getFullYear() === now.getFullYear() &&
             recordDate.getMonth() === now.getMonth() &&
             recordDate.getDate() === now.getDate();
    }
    if (filter === "week") {
      const sevenDaysAgo = now.getTime() - (7 * 24 * 60 * 60 * 1000);
      return timestamp >= sevenDaysAgo;
    }
    if (filter === "month") {
      return recordDate.getFullYear() === now.getFullYear() &&
             recordDate.getMonth() === now.getMonth();
    }
    return true; // "all"
  }

  // Filter Bar Event Listeners
  document.querySelectorAll("#historyFilterBar .filter-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      activeHistoryFilter = btn.getAttribute("data-filter") || "today";
      document.querySelectorAll("#historyFilterBar .filter-btn").forEach(b => {
        b.classList.toggle("active", b === btn);
      });
      renderRentalList(allRentalsData);
    });
  });

  // Rental History Loader
  async function loadRentalHistory() {
    if (!token) return;
    try {
      const res = await fetch("/api/rentals", {
        headers: { "Authorization": `Bearer ${token}` }
      });
      if (res.status === 401) {
        btnLogout.click();
        return;
      }
      const rentals = await res.json();
      allRentalsData = rentals || [];
      renderRentalList(allRentalsData);
    } catch (e) {
      rentalListContainer.innerHTML = `<div class="empty-state">Gagal memuat riwayat: ${e.message}</div>`;
    }
  }

  function renderRentalList(rentals) {
    allRentalsData = rentals || [];
    const filtered = allRentalsData.filter(item => isWithinFilter(item.startTime, activeHistoryFilter));

    if (rateDisplayTag) {
      rateDisplayTag.textContent = `Tarif: ${formatRupiah(currentHourlyRate)} / Jam`;
    }

    if (!filtered || filtered.length === 0) {
      if (statTotalSessions) statTotalSessions.textContent = "0 Sesi";
      if (statTotalHours) statTotalHours.textContent = "0 Jam";
      if (statTotalRevenue) statTotalRevenue.textContent = "Rp 0";
      if (statAvgDuration) statAvgDuration.textContent = "0 Mnt";
      rentalListContainer.innerHTML = `<div class="empty-state">Belum ada transaksi sewa untuk periode ini.</div>`;
      return;
    }

    // Calculate Summary Stats based on filtered data & current hourly rate
    const totalSessions = filtered.length;
    const totalMinutes = filtered.reduce((acc, r) => acc + (r.totalDurationMinutes || 0), 0);
    const hours = (totalMinutes / 60).toFixed(1);
    const avgDuration = Math.round(totalMinutes / totalSessions);
    const totalRevenue = filtered.reduce((acc, r) => {
      const mins = r.totalDurationMinutes || 0;
      return acc + Math.round((mins / 60) * currentHourlyRate);
    }, 0);

    if (statTotalSessions) statTotalSessions.textContent = `${totalSessions} Sesi`;
    if (statTotalHours) statTotalHours.textContent = `${hours} Jam`;
    if (statTotalRevenue) statTotalRevenue.textContent = formatRupiah(totalRevenue);
    if (statAvgDuration) statAvgDuration.textContent = `${avgDuration} Mnt`;

    const html = filtered.map(item => {
      const startFormatted = formatDateTime(item.startTime);
      const endFormatted = item.endTime ? formatTimeOnly(item.endTime) : (item.status === "ACTIVE" ? "Sedang berjalan" : "-");
      
      let statusLabel = "Selesai";
      let statusClass = "COMPLETED";
      if (item.status === "ACTIVE") {
        statusLabel = "Sedang Aktif";
        statusClass = "ACTIVE";
      } else if (item.status === "STOPPED" || item.status === "RESET") {
        statusLabel = "Dihentikan";
        statusClass = "STOPPED";
      }

      let durationText = `${item.totalDurationMinutes} Menit`;
      if (item.extendedMinutes > 0) {
        durationText = `${item.initialDurationMinutes}m (+${item.extendedMinutes}m perpanjangan) = ${item.totalDurationMinutes}m`;
      }

      const sessionCost = Math.round(((item.totalDurationMinutes || 0) / 60) * currentHourlyRate);

      return `
        <div class="rental-card">
          <div class="rental-card-top">
            <span class="rental-customer-name">${escapeHtml(item.customerName)}</span>
            <div class="rental-card-right">
              <span class="rental-fee-badge">${formatRupiah(sessionCost)}</span>
              <span class="rental-status-tag ${statusClass}">${statusLabel}</span>
            </div>
          </div>
          <div class="rental-meta">
            <div class="rental-time-line">
              <span class="micro-label-muted">WAKTU</span>
              <span>${startFormatted}</span>
              ${item.endTime ? `<span>— ${endFormatted}</span>` : ""}
            </div>
            <div class="rental-time-line">
              <span class="micro-label-muted">DURASI</span>
              <span class="rental-duration-badge">${durationText}</span>
            </div>
          </div>
        </div>
      `;
    }).join("");

    rentalListContainer.innerHTML = html;
  }

  function formatDateTime(timestamp) {
    const d = new Date(timestamp);
    const months = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"];
    const day = d.getDate();
    const month = months[d.getMonth()];
    const year = d.getFullYear();
    const h = pad(d.getHours());
    const m = pad(d.getMinutes());
    return `${day} ${month} ${year}, ${h}:${m}`;
  }

  function formatTimeOnly(timestamp) {
    const d = new Date(timestamp);
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function escapeHtml(str) {
    if (!str) return "Pelanggan";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // ==========================================================================
  // Settings Management Logic
  // ==========================================================================
  async function loadSettings() {
    if (!token) return;
    try {
      const res = await fetch("/api/settings", {
        headers: { "Authorization": `Bearer ${token}` }
      });
      if (res.status === 401) {
        btnLogout.click();
        return;
      }
      if (!res.ok) throw new Error("Gagal mengambil pengaturan");
      const data = await res.json();

      if (settingRoomName) settingRoomName.value = data.roomName || "";
      if (settingHourlyRate) settingHourlyRate.value = data.hourlyRate || 50000;
      currentHourlyRate = Number(data.hourlyRate) || 50000;
      if (previewRateFormatted) previewRateFormatted.textContent = `${formatRupiah(currentHourlyRate)} / Jam`;
      if (rateDisplayTag) rateDisplayTag.textContent = `Tarif: ${formatRupiah(currentHourlyRate)} / Jam`;
      currentOverlayCorner = data.overlayCorner || "TOP_RIGHT";
      currentOverlaySize = data.overlaySize || "MEDIUM";
      currentStandbyBgMode = data.standbyBgMode || "NONE";

      document.querySelectorAll("#cornerSelector .btn-option").forEach(btn => {
        btn.classList.toggle("active", btn.getAttribute("data-corner") === currentOverlayCorner);
      });
      document.querySelectorAll("#sizeSelector .btn-option").forEach(btn => {
        btn.classList.toggle("active", btn.getAttribute("data-size") === currentOverlaySize);
      });
      document.querySelectorAll("#bgModeSelector .btn-option").forEach(btn => {
        btn.classList.toggle("active", btn.getAttribute("data-bg-mode") === currentStandbyBgMode);
      });
      updateBgPanelsVisibility();

      if (settingYoutubeUrl) settingYoutubeUrl.value = data.standbyYoutubeUrl || "";
      if (settingYoutubeMute) settingYoutubeMute.checked = (data.standbyYoutubeMute !== false);
      if (settingVideoMute) settingVideoMute.checked = (data.standbyVideoMute !== false);
      if (settingOperatorPhone) settingOperatorPhone.value = data.operatorPhone || "";

      if (data.hasCustomBgImage) {
        if (bgImgElement) {
          bgImgElement.src = "/api/settings/background-image?t=" + Date.now();
          bgImgElement.classList.remove("hidden");
        }
        if (bgEmptyPlaceholder) bgEmptyPlaceholder.classList.add("hidden");
      } else {
        if (bgImgElement) {
          bgImgElement.src = "";
          bgImgElement.classList.add("hidden");
        }
        if (bgEmptyPlaceholder) bgEmptyPlaceholder.classList.remove("hidden");
      }

      if (data.hasCustomBgVideo) {
        if (bgVideoElement) {
          bgVideoElement.src = "/api/settings/background-video?t=" + Date.now();
          bgVideoElement.classList.remove("hidden");
        }
        if (bgVideoEmptyPlaceholder) bgVideoEmptyPlaceholder.classList.add("hidden");
      } else {
        if (bgVideoElement) {
          bgVideoElement.src = "";
          bgVideoElement.classList.add("hidden");
        }
        if (bgVideoEmptyPlaceholder) bgVideoEmptyPlaceholder.classList.remove("hidden");
      }

      hideSettingsAlert();

      // If viewing history tab, refresh calculations
      if (currentTab === "history" && allRentalsData.length > 0) {
        renderRentalList(allRentalsData);
      }
    } catch (e) {
      showSettingsAlert(e.message, true);
    }
  }

  function updateBgPanelsVisibility() {
    if (panelBgImage) panelBgImage.classList.toggle("hidden", currentStandbyBgMode !== "IMAGE");
    if (panelBgVideo) panelBgVideo.classList.toggle("hidden", currentStandbyBgMode !== "VIDEO");
    if (panelBgYoutube) panelBgYoutube.classList.toggle("hidden", currentStandbyBgMode !== "YOUTUBE");
  }

  async function saveSettings() {
    if (!token) return;
    const roomName = settingRoomName?.value.trim() || "";
    if (!roomName) {
      showSettingsAlert("Nama ruangan tidak boleh kosong", true);
      return;
    }
    const hourlyRate = parseInt(settingHourlyRate?.value, 10);
    if (isNaN(hourlyRate) || hourlyRate < 0) {
      showSettingsAlert("Tarif sewa per jam harus berupa angka valid", true);
      return;
    }

    const newPin = settingNewPin?.value.trim() || "";
    const confirmPin = settingConfirmPin?.value.trim() || "";
    if (newPin || confirmPin) {
      if (newPin !== confirmPin) {
        showSettingsAlert("Konfirmasi PIN tidak cocok dengan PIN baru", true);
        return;
      }
      if (newPin.length < 4 || newPin.length > 6 || !/^\d+$/.test(newPin)) {
        showSettingsAlert("PIN baru harus berupa 4-6 digit angka", true);
        return;
      }
    }

    if (btnSaveSettings) {
      btnSaveSettings.disabled = true;
      btnSaveSettings.textContent = "Menyimpan...";
    }

    try {
      const payload = {
        roomName,
        hourlyRate,
        overlayCorner: currentOverlayCorner,
        overlaySize: currentOverlaySize,
        standbyBgMode: currentStandbyBgMode,
        standbyYoutubeUrl: settingYoutubeUrl?.value.trim() || "",
        standbyYoutubeMute: settingYoutubeMute ? settingYoutubeMute.checked : true,
        standbyVideoMute: settingVideoMute ? settingVideoMute.checked : true,
        operatorPhone: settingOperatorPhone ? settingOperatorPhone.value.trim() : ""
      };
      if (newPin) payload.newPin = newPin;

      const res = await fetch("/api/settings", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`
        },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Gagal menyimpan");
      }
      const data = await res.json();
      currentHourlyRate = Number(data.hourlyRate) || hourlyRate;
      if (roomNameHeader) roomNameHeader.textContent = data.roomName;
      if (settingNewPin) settingNewPin.value = "";
      if (settingConfirmPin) settingConfirmPin.value = "";
      showSettingsAlert("Pengaturan berhasil disimpan dan disinkronkan ke TV", false);
      if (rateDisplayTag) rateDisplayTag.textContent = `Tarif: ${formatRupiah(currentHourlyRate)} / Jam`;
      if (previewRateFormatted) previewRateFormatted.textContent = `${formatRupiah(currentHourlyRate)} / Jam`;
    } catch (e) {
      showSettingsAlert("Gagal: " + e.message, true);
    } finally {
      if (btnSaveSettings) {
        btnSaveSettings.disabled = false;
        btnSaveSettings.textContent = "Simpan Pengaturan";
      }
    }
  }

  function showSettingsAlert(msg, isError) {
    if (!settingsAlert) return;
    settingsAlert.textContent = msg;
    settingsAlert.className = "settings-alert " + (isError ? "error" : "success");
    settingsAlert.classList.remove("hidden");
  }

  function hideSettingsAlert() {
    if (settingsAlert) settingsAlert.classList.add("hidden");
  }

  // Quick Chips & Live Preview listeners
  document.querySelectorAll("[data-set-room]").forEach(chip => {
    chip.addEventListener("click", () => {
      if (settingRoomName) settingRoomName.value = chip.getAttribute("data-set-room");
    });
  });

  document.querySelectorAll("[data-set-rate]").forEach(chip => {
    chip.addEventListener("click", () => {
      const val = parseInt(chip.getAttribute("data-set-rate"), 10);
      if (settingHourlyRate) {
        settingHourlyRate.value = val;
        if (previewRateFormatted) previewRateFormatted.textContent = `${formatRupiah(val)} / Jam`;
      }
    });
  });

  settingHourlyRate?.addEventListener("input", () => {
    const val = parseInt(settingHourlyRate.value, 10);
    if (!isNaN(val) && previewRateFormatted) {
      previewRateFormatted.textContent = `${formatRupiah(val)} / Jam`;
    }
  });

  document.querySelectorAll("#cornerSelector .btn-option").forEach(btn => {
    btn.addEventListener("click", () => {
      currentOverlayCorner = btn.getAttribute("data-corner") || "TOP_RIGHT";
      document.querySelectorAll("#cornerSelector .btn-option").forEach(b => {
        b.classList.toggle("active", b === btn);
      });
    });
  });

  document.querySelectorAll("#sizeSelector .btn-option").forEach(btn => {
    btn.addEventListener("click", () => {
      currentOverlaySize = btn.getAttribute("data-size") || "MEDIUM";
      document.querySelectorAll("#sizeSelector .btn-option").forEach(b => {
        b.classList.toggle("active", b === btn);
      });
    });
  });

  // Background Attract Mode Listeners
  document.querySelectorAll("#bgModeSelector .btn-option").forEach(btn => {
    btn.addEventListener("click", () => {
      currentStandbyBgMode = btn.getAttribute("data-bg-mode") || "NONE";
      document.querySelectorAll("#bgModeSelector .btn-option").forEach(b => {
        b.classList.toggle("active", b === btn);
      });
      updateBgPanelsVisibility();
    });
  });

  btnPickBgFile?.addEventListener("click", () => {
    bgFileInput?.click();
  });

  bgFileInput?.addEventListener("change", () => {
    const file = bgFileInput.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      showSettingsAlert("Harap pilih file gambar (JPG, PNG, WebP)", true);
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      showSettingsAlert("Ukuran file gambar maksimal 5MB", true);
      return;
    }

    const reader = new FileReader();
    reader.onload = async (e) => {
      const base64Data = e.target.result;
      try {
        if (btnPickBgFile) {
          btnPickBgFile.disabled = true;
          btnPickBgFile.textContent = "Mengunggah...";
        }
        const res = await fetch("/api/settings/background-image", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`
          },
          body: JSON.stringify({ imageBase64: base64Data })
        });
        if (!res.ok) {
          const err = await res.json();
          throw new Error(err.error || "Gagal mengunggah gambar");
        }
        showSettingsAlert("Foto latar berhasil diperbarui ke TV", false);
        if (bgImgElement) {
          bgImgElement.src = "/api/settings/background-image?t=" + Date.now();
          bgImgElement.classList.remove("hidden");
        }
        if (bgEmptyPlaceholder) bgEmptyPlaceholder.classList.add("hidden");
      } catch (err) {
        showSettingsAlert("Gagal upload gambar: " + err.message, true);
      } finally {
        if (btnPickBgFile) {
          btnPickBgFile.disabled = false;
          btnPickBgFile.textContent = "Pilih Foto";
        }
        bgFileInput.value = "";
      }
    };
    reader.readAsDataURL(file);
  });

  btnDeleteBgImage?.addEventListener("click", () => {
    showConfirmModal({
      title: "Hapus Foto Latar?",
      message: "Foto latar belakang promosi akan dihapus dari TV dan layar siap kembali ke latar standar.",
      confirmText: "Hapus Foto",
      isDanger: true,
      onConfirm: async () => {
        try {
          const res = await fetch("/api/settings/background-image/delete", {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
          });
          if (!res.ok) throw new Error("Gagal menghapus foto");
          if (bgImgElement) {
            bgImgElement.src = "";
            bgImgElement.classList.add("hidden");
          }
          if (bgEmptyPlaceholder) bgEmptyPlaceholder.classList.remove("hidden");
          showSettingsAlert("Foto latar berhasil dihapus", false);
        } catch (e) {
          showSettingsAlert("Gagal: " + e.message, true);
        }
      }
    });
  });

  // Video Background Event Listeners
  btnPickBgVideo?.addEventListener("click", () => {
    bgVideoFileInput?.click();
  });

  bgVideoFileInput?.addEventListener("change", () => {
    const file = bgVideoFileInput.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("video/") && !file.name.toLowerCase().endsWith(".mp4")) {
      showSettingsAlert("Harap pilih file video berformat MP4", true);
      return;
    }

    const maxBytes = 50 * 1024 * 1024; // 50MB
    if (file.size > maxBytes) {
      showSettingsAlert("Ukuran file video maksimal 50MB", true);
      return;
    }

    if (btnPickBgVideo) {
      btnPickBgVideo.disabled = true;
      btnPickBgVideo.textContent = "Mengunggah...";
    }
    if (videoUploadProgress) {
      videoUploadProgress.classList.remove("hidden");
    }
    if (videoProgressBar) videoProgressBar.style.width = "0%";
    if (videoProgressText) videoProgressText.textContent = "Mengunggah 0%...";

    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/settings/background-video");
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.setRequestHeader("Content-Type", file.type || "video/mp4");

    xhr.upload.onprogress = (evt) => {
      if (evt.lengthComputable) {
        const percent = Math.round((evt.loaded / evt.total) * 100);
        if (videoProgressBar) videoProgressBar.style.width = percent + "%";
        const loadedMb = (evt.loaded / (1024 * 1024)).toFixed(1);
        const totalMb = (evt.total / (1024 * 1024)).toFixed(1);
        if (videoProgressText) videoProgressText.textContent = `Mengunggah ${percent}% (${loadedMb}MB / ${totalMb}MB)`;
      }
    };

    xhr.onload = () => {
      if (videoUploadProgress) videoUploadProgress.classList.add("hidden");
      if (btnPickBgVideo) {
        btnPickBgVideo.disabled = false;
        btnPickBgVideo.textContent = "Pilih Video MP4";
      }
      bgVideoFileInput.value = "";

      if (xhr.status >= 200 && xhr.status < 300) {
        showSettingsAlert("Video latar MP4 berhasil diunggah ke TV", false);
        if (bgVideoElement) {
          bgVideoElement.src = "/api/settings/background-video?t=" + Date.now();
          bgVideoElement.classList.remove("hidden");
        }
        if (bgVideoEmptyPlaceholder) bgVideoEmptyPlaceholder.classList.add("hidden");
      } else {
        let msg = "Gagal mengunggah video";
        try {
          const res = JSON.parse(xhr.responseText);
          if (res.error) msg = res.error;
        } catch (_) {}
        showSettingsAlert(msg, true);
      }
    };

    xhr.onerror = () => {
      if (videoUploadProgress) videoUploadProgress.classList.add("hidden");
      if (btnPickBgVideo) {
        btnPickBgVideo.disabled = false;
        btnPickBgVideo.textContent = "Pilih Video MP4";
      }
      bgVideoFileInput.value = "";
      showSettingsAlert("Terjadi kendala jaringan saat mengunggah video ke TV", true);
    };

    xhr.send(file);
  });

  btnDeleteBgVideo?.addEventListener("click", () => {
    showConfirmModal({
      title: "Hapus Video Latar?",
      message: "Video promosi MP4 akan dihapus dari TV dan layar siap kembali ke latar standar.",
      confirmText: "Hapus Video",
      isDanger: true,
      onConfirm: async () => {
        try {
          const res = await fetch("/api/settings/background-video/delete", {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` }
          });
          if (!res.ok) throw new Error("Gagal menghapus video");
          if (bgVideoElement) {
            bgVideoElement.src = "";
            bgVideoElement.classList.add("hidden");
          }
          if (bgVideoEmptyPlaceholder) bgVideoEmptyPlaceholder.classList.remove("hidden");
          showSettingsAlert("Video latar berhasil dihapus", false);
        } catch (e) {
          showSettingsAlert("Gagal: " + e.message, true);
        }
      }
    });
  });

  async function loadApps() {
    if (!token || !appsListContainer) return;
    appsListContainer.innerHTML = '<div class="empty-state">Memuat daftar aplikasi...</div>';
    try {
      const res = await fetch("/api/apps", {
        headers: { "Authorization": `Bearer ${token}` }
      });
      if (!res.ok) throw new Error("Gagal mengambil daftar aplikasi");
      const apps = await res.json();
      if (!apps || apps.length === 0) {
        appsListContainer.innerHTML = '<div class="empty-state">Tidak ada aplikasi tambahan terdeteksi di TV</div>';
        return;
      }
      appsListContainer.innerHTML = "";
      apps.forEach(app => {
        const item = document.createElement("div");
        item.className = "app-card" + (app.isAllowed ? " allowed" : "");
        item.innerHTML = `
          <div class="app-card-left">
            <div class="app-card-title">${escapeHtml(app.appName)}</div>
            <div class="app-card-pkg">${escapeHtml(app.packageName)}</div>
          </div>
          <label class="hardware-switch">
            <input type="checkbox" ${app.isAllowed ? "checked" : ""}>
            <span class="switch-slider"></span>
          </label>
        `;
        const chk = item.querySelector("input");
        chk.addEventListener("change", async () => {
          try {
            chk.disabled = true;
            const toggleRes = await fetch("/api/apps/toggle", {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${token}`
              },
              body: JSON.stringify({ packageName: app.packageName, isAllowed: chk.checked })
            });
            if (!toggleRes.ok) throw new Error("Gagal mengubah izin");
            if (chk.checked) {
              item.classList.add("allowed");
            } else {
              item.classList.remove("allowed");
            }
          } catch (e) {
            alert(e.message);
            chk.checked = !chk.checked;
          } finally {
            chk.disabled = false;
          }
        });
        appsListContainer.appendChild(item);
      });
    } catch (e) {
      appsListContainer.innerHTML = `<div class="empty-state text-danger">${escapeHtml(e.message)}</div>`;
    }
  }

  // Initialization check
  if (token) {
    showDashboard();
    connectWebSocket();
    loadSettings();
    // Fetch initial status
    fetch("/api/status", {
      headers: { "Authorization": `Bearer ${token}` }
    }).then(res => {
      if (res.ok) return res.json();
      throw new Error("unauthorized");
    }).then(applyState).catch(() => {
      token = null;
      sessionStorage.removeItem("admin_token");
      showLogin();
    });
  } else {
    showLogin();
  }
})();
