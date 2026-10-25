import {
  Crosshair,
  Gamepad2,
  Gauge,
  LayoutGrid,
  Smartphone,
  Zap,
} from "lucide-react";

interface Feature {
  id: string;
  icon: typeof Zap;
  title: string;
  body: string;
  api: string;
  chips: string[];
  accent: "lime" | "cyan";
}

const FEATURES: Feature[] = [
  {
    id: "A",
    icon: Zap,
    title: "REAL APP BOOSTING",
    body: "Pick any installed launcher app — via an actual PackageManager query — and PulseBoost runs privileged commands inside the Shizuku server process: force-stop, full data+cache clear, memory trim at level 80, background kill. Before/after RAM comes from ActivityManager.MemoryInfo, shown as a delta.",
    api: 'Shizuku.newProcess(["am","force-stop",pkg])',
    chips: ["SHIZUKU REQUIRED", "USER-SELECTED TARGETS"],
    accent: "lime",
  },
  {
    id: "B",
    icon: Smartphone,
    title: "REAL PHONE BOOSTING",
    body: "One tap kills every background process and reclaims cached storage system-wide. Live card refreshes every second with RAM (MemoryInfo), per-core CPU from /proc/stat jiffy deltas, and battery temperature/level from the BatteryManager sticky intent.",
    api: 'exec "pm trim-caches 999G" + "am kill-all"',
    chips: ["SHIZUKU REQUIRED", "1 HZ TELEMETRY"],
    accent: "lime",
  },
  {
    id: "C",
    icon: Gamepad2,
    title: "GAME MODE INTEGRATION",
    body: "On Android 12+ the SETTINGS tab drives android.app.GameManager: public getGameMode() reads, a setGameState(MODE_GAMEPLAY_UNINTERRUPTIBLE) gameplay signal, plus a reflective attempt at the hidden setGameMode API with every rejection reason surfaced verbatim. Below API 31 the section is hidden entirely.",
    api: "gameManager.getGameMode() · setGameState(…)",
    chips: ["API 31+ ONLY", "HIDDEN BELOW"],
    accent: "cyan",
  },
  {
    id: "D",
    icon: Crosshair,
    title: "PER-APP BOOST PROFILES",
    body: "Flip 'auto-boost on launch' for any game. A lightweight foreground service polls UsageStatsManager; the moment a profiled package takes focus, it kills your marked heavyweight apps and trims caches, then records the reclaimed-megabyte summary into Settings.",
    api: "usageStats.queryEvents(ACTIVITY_RESUMED)",
    chips: ["USAGE ACCESS", "SHIZUKU REQUIRED"],
    accent: "cyan",
  },
  {
    id: "E",
    icon: Gauge,
    title: "FPS OVERLAY",
    body: "A draggable TYPE_APPLICATION_OVERLAY served by a foreground service. Choreographer.FrameCallback measures genuinely delivered frames — refreshed every half second — with the live foreground package printed underneath. One tap dismisses it.",
    api: "Choreographer.postFrameCallback(…)",
    chips: ["OVERLAY PERMISSION", "FEATURE E OF SIX"],
    accent: "lime",
  },
  {
    id: "F",
    icon: LayoutGrid,
    title: "HOME-SCREEN WIDGET",
    body: "An AppWidgetProvider paints live free-RAM straight from MemoryInfo, re-armed on a 30-second AlarmManager chain (Doze may stretch it, honestly documented), with a one-tap BOOST button that runs kill-all + trim-caches through Shizuku.",
    api: "RemoteViews + goAsync boost chain",
    chips: ["30 S REFRESH", "ONE-TAP BOOST"],
    accent: "lime",
  },
];

export function FeatureMatrix() {
  return (
    <section id="features" className="relative mx-auto max-w-7xl px-6 py-24 lg:py-32">
      <div data-reveal className="mb-14 flex flex-wrap items-end justify-between gap-6">
        <div>
          <p className="font-mono text-[10px] tracking-[0.3em] text-lime">CAPABILITY MATRIX</p>
          <h2 className="mt-3 font-display text-4xl font-bold leading-[1.02] tracking-tight sm:text-5xl">
            SIX SYSTEMS.
            <br />
            <span className="text-dim">ALL WIRED TO REAL APIs.</span>
          </h2>
        </div>
        <p className="max-w-sm font-mono text-[11px] leading-relaxed tracking-wide text-faint">
          every feature below exists in android-app/ as compilable Kotlin —
          browse it in the source explorer further down
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {FEATURES.map((f, idx) => (
          <article
            key={f.id}
            data-reveal
            style={{ transitionDelay: `${(idx % 3) * 90}ms` }}
            className="group relative flex flex-col border border-line bg-panel p-6 transition-colors duration-300 hover:border-lime/50"
          >
            <div className="mb-5 flex items-start justify-between">
              <span className="flex h-11 w-11 items-center justify-center border border-line2 bg-panel2 text-lime transition-colors group-hover:border-lime/50">
                <f.icon size={19} strokeWidth={1.8} />
              </span>
              <span className="font-mono text-[32px] font-semibold leading-none text-line2 transition-colors group-hover:text-lime/30">
                {f.id}
              </span>
            </div>
            <h3 className="font-display text-[17px] font-semibold tracking-wide">{f.title}</h3>
            <p className="mt-3 flex-1 text-[12.5px] leading-relaxed text-dim">{f.body}</p>
            <p
              className={`mt-4 border-l-2 py-0.5 pl-3 font-mono text-[10.5px] tracking-wide ${
                f.accent === "lime" ? "border-lime/70 text-lime/90" : "border-cyan/70 text-cyan/90"
              }`}
            >
              {f.api}
            </p>
            <div className="mt-4 flex flex-wrap gap-1.5">
              {f.chips.map((c) => (
                <span
                  key={c}
                  className="border border-line px-2 py-0.5 font-mono text-[9px] tracking-[0.18em] text-dim"
                >
                  {c}
                </span>
              ))}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
