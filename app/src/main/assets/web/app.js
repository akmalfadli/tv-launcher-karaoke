(function() {
  let token = sessionStorage.getItem("admin_token");
  let ws = null;
  let wsReconnectTimer = null;
  let wsReconnectDelay = 1000;
  let currentState = null;
  let localTickerTimer = null;

  // DOM elements
  const loginSection = document.getElementById("loginSection");
  const dashboardSection = document.getElementById("dashboardSection");
  const pinInput = document.getElementById("pinInput");
  const loginError = document.getElementById("loginError");
  const btnLogin = document.getElementById("btnLogin");
  const btnLogout = document.getElementById("btnLogout");
  const statusDot = document.getElementById("statusDot");
  const connectionText = document.getElementById("connectionText");
  const roomNameHeader = document.getElementById("roomNameHeader");
  const warningsBanner = document.getElementById("warningsBanner");
  const statusBadge = document.getElementById("statusBadge");
  const timerDisplay = document.getElementById("timerDisplay");
  const progressBar = document.getElementById("progressBar");
  const finishNotice = document.getElementById("finishNotice");
  const btnPauseResume = document.getElementById("btnPauseResume");
  const btnStop = document.getElementById("btnStop");
  const extendControls = document.getElementById("extendControls");

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
    btnLogout.classList.add("hidden");
    statusDot.className = "status-dot offline";
    connectionText.textContent = "Offline";
  }

  function showDashboard() {
    loginSection.classList.add("hidden");
    dashboardSection.classList.remove("hidden");
    btnLogout.classList.remove("hidden");
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
      if (w === "ADB_ENABLED") return "⚠️ Debugging ADB aktif! Segera matikan di Opsi Pengembang TV demi keamanan.";
      if (w === "OVERLAY_DENIED") return "⚠️ Izin overlay belum aktif di TV!";
      if (w === "NOT_DEFAULT_LAUNCHER") return "⚠️ Aplikasi belum diatur sebagai launcher utama.";
      if (w === "BATTERY_OPTIMIZATION_ACTIVE") return "⚠️ Optimasi baterai masih aktif untuk aplikasi ini.";
      return `⚠️ Peringatan sistem: ${w}`;
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
      callTimerApi("start", { minutes });
    });
  });

  btnPauseResume.addEventListener("click", () => {
    if (!currentState) return;
    if (currentState.status === "RUNNING") {
      callTimerApi("pause");
    } else if (currentState.status === "PAUSED") {
      callTimerApi("resume");
    } else if (currentState.status === "FINISHED" || currentState.status === "IDLE") {
      callTimerApi("start", { minutes: 60 });
    }
  });

  btnStop.addEventListener("click", () => {
    if (confirm("Yakin ingin menghentikan timer ruangan ini?")) {
      callTimerApi("stop");
    }
  });

  document.querySelectorAll(".btn-adjust").forEach(btn => {
    btn.addEventListener("click", () => {
      const minutes = parseInt(btn.getAttribute("data-adjust"), 10);
      callTimerApi("adjust", { minutes });
    });
  });

  // Initialization check
  if (token) {
    showDashboard();
    connectWebSocket();
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
