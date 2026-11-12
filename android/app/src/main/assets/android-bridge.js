/* Android-Hülle: ersetzt die Web Speech API (fehlt in der WebView) durch die
   native Sprachausgabe und behandelt die Zurück-Taste. Im Browser inaktiv. */
(function () {
  var T = window.AndroidTTS;
  if (T) {
    var pending = {}, seq = 0, voices = [];
    var loadVoices = function () {
      try {
        voices = JSON.parse(T.voices() || "[]").map(function (v) {
          return { name: v.name, voiceURI: v.name, lang: v.lang, localService: !v.net, "default": false };
        });
      } catch (e) { voices = []; }
    };
    var synth = {
      onvoiceschanged: null, pending: false, paused: false,
      getVoices: function () { return voices.slice(); },
      speak: function (u) {
        var id = String(++seq);
        pending[id] = u;
        T.speak(id, String(u.text || ""), String(u.lang || ""), u.voice ? String(u.voice.name) : "",
          +u.rate || 1, +u.pitch || 1);
      },
      cancel: function () {
        pending = {};
        T.stop();
      },
      pause: function () {}, resume: function () {},
      addEventListener: function (t, f) { if (t === "voiceschanged") this.onvoiceschanged = f; },
      removeEventListener: function () {}
    };
    Object.defineProperty(synth, "speaking", {
      get: function () {
        if (Object.keys(pending).length) return true;
        try { return !!T.isSpeaking(); } catch (e) { return false; }
      }
    });
    var Utt = function (text) {
      this.text = text || ""; this.lang = ""; this.voice = null; this.rate = 1; this.pitch = 1; this.volume = 1;
      this.onstart = this.onend = this.onerror = null;
    };
    Object.defineProperty(window, "speechSynthesis", { value: synth, configurable: true });
    Object.defineProperty(window, "SpeechSynthesisUtterance", { value: Utt, configurable: true, writable: true });

    window.__ttsEvent = function (id, type) {
      var u = pending[id];
      if (!u) return;
      if (type === "start") { if (u.onstart) try { u.onstart({}); } catch (e) {} return; }
      delete pending[id];
      var f = type === "end" ? u.onend : u.onerror;
      if (f) try { f({}); } catch (e) {}
    };
    window.__ttsVoicesChanged = function () {
      loadVoices();
      if (typeof synth.onvoiceschanged === "function") try { synth.onvoiceschanged(); } catch (e) {}
    };
    loadVoices();
  }

  window.__appBack = function () {
    try {
      var m = document.getElementById("modal");
      if (m && m.innerHTML.trim()) { m.innerHTML = ""; return true; }
      var b = document.querySelector("#app .back");
      if (b) { b.click(); return true; }
      if (typeof S !== "undefined" && S.view !== "home") {
        S.view = "home"; render(); window.scrollTo(0, 0); return true;
      }
    } catch (e) {}
    return false;
  };
})();
