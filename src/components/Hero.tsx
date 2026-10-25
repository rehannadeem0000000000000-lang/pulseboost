import { ArrowDown, Download, ScanSearch, Zap } from "lucide-react";
import { APK_META, downloadApk, fmtKb } from "../lib/apk";

const TERMINAL_LINES: Array<[string, string]> = [
  ["$", "./gradlew :app:assembleRelease"],
  ["›", `BUILD SUCCESSFUL · ${APK_META.fileName} · ${fmtKb(APK_META.byteLength)}`],
  ["$", "apksigner verify --verbose --print-certs ✓"],
  ["›", "Verifies · APK Signature Scheme v2 · 1 signer · CN=PulseBoost Pro"],
  ["$", "jarsigner -verify ✓ jar verified (self-signed dev cert)"],
  ["$", "sha256sum --check src/data/apkPayload.ts"],
  ["›", `${APK_META.sha256.slice(0, 24)}…${APK_META.sha256.slice(-6)}  MATCH`],
  ["$", "exec via Shizuku.newProcess — captured in-exec log"],
  ["›", 'am force-stop · pm clear · pm trim-caches 999G · am kill-all'],
  ["›", 'am send-trim-memory pkg 80 · settings put global low_power_mode'],
];

const TICKER = [
  "AM FORCE-STOP", "PM TRIM-CACHES", "AM KILL-ALL", "/PROC/STAT",
  "ACTIVITYMANAGER.MEMORYINFO", "CHOREOGRAPHER FRAMES", "GAMEMANAGER",
  "USAGESTATSMANAGER", "BATTERYMANAGER", "SHIZUKU.NEWPROCESS",
  "DISPLAY.MODE", "TYPE_APPLICATION_OVERLAY",
];

export function Hero() {
  return (
    <header className="relative overflow-hidden">
      <div className="bp-grid pointer-events-none absolute inset-0" />
      <div className="scanlines pointer-events-none absolute inset-0 opacity-60" />

      <div className="relative mx-auto grid max-w-7xl gap-12 px-6 pb-16 pt-32 lg:grid-cols-[1.15fr_1fr] lg:gap-8 lg:pt-40">
        {/* left : statement */}
        <div data-reveal>
          <div className="mb-7 flex flex-wrap items-center gap-2 font-mono text-[10px] tracking-[0.22em]">
            <span className="flex items-center gap-1.5 border border-lime/40 bg-lime/10 px-2.5 py-1 text-lime">
              <Zap size={11} /> SHIZUKU ENGINE
            </span>
            <span className="border border-line px-2.5 py-1 text-dim">
              SIGNED APK · SCHEME V2
            </span>
            <span className="border border-line px-2.5 py-1 text-dim">
              SDK 24 → 34
            </span>
          </div>

          <h1 className="font-display text-[17vw] font-bold leading-[0.86] tracking-tight sm:text-[13vw] lg:text-[7.2rem]">
            <span className="block text-text">BOOST.</span>
            <span className="glow-lime block text-lime">FOR REAL.</span>
          </h1>

          <p className="mt-7 max-w-xl text-[15px] leading-relaxed text-dim">
            PulseBoost Pro is an Android game &amp; app booster whose every
            button reaches a genuine system API — force-stops, cache clears,
            memory trims and settings writes all flow through{" "}
            <span className="text-text">Shizuku-processed shell commands</span>.
            No invented numbers: RAM comes from{" "}
            <span className="font-mono text-[13px] text-cyan">ActivityManager.MemoryInfo</span>,
            CPU from <span className="font-mono text-[13px] text-cyan">/proc/stat</span>,
            frames from <span className="font-mono text-[13px] text-cyan">Choreographer</span>.
          </p>

          <div className="mt-9 flex flex-wrap items-center gap-4">
            <button
              onClick={downloadApk}
              className="brackets group flex items-center gap-3 bg-lime px-7 py-4 font-mono text-[12px] font-semibold tracking-[0.18em] text-ink transition-transform duration-300 hover:-translate-y-0.5"
            >
              <Download size={15} />
              DOWNLOAD APK · {fmtKb(APK_META.byteLength)}
            </button>
            <a
              href="#verify"
              className="flex items-center gap-3 border border-line px-7 py-4 font-mono text-[12px] tracking-[0.18em] text-dim transition-colors hover:border-cyan/60 hover:text-cyan"
            >
              <ScanSearch size={15} />
              VERIFY PAYLOAD
            </a>
          </div>

          <dl className="mt-12 grid max-w-xl grid-cols-2 gap-px border border-line bg-line sm:grid-cols-4">
            {[
              ["PACKAGE", APK_META.packageName],
              ["VERSION", `${APK_META.versionName} · code ${APK_META.versionCode}`],
              ["SIZE", `${fmtKb(APK_META.byteLength)} signed`],
              ["SHA-256", `${APK_META.sha256.slice(0, 10)}…`],
            ].map(([k, v]) => (
              <div key={k} className="bg-panel px-4 py-3.5">
                <dt className="font-mono text-[9px] tracking-[0.24em] text-faint">{k}</dt>
                <dd className="mt-1 truncate font-mono text-[11.5px] text-text">{v}</dd>
              </div>
            ))}
          </dl>
        </div>

        {/* right : ci terminal */}
        <div data-reveal className="relative self-start lg:mt-10">
          <div className="console-sweep brackets relative border border-line2 bg-panel shadow-[0_40px_120px_-40px_rgba(200,255,46,0.15)]">
            <div className="flex items-center gap-2 border-b border-line px-4 py-3">
              <span className="h-2.5 w-2.5 rounded-full bg-red/70" />
              <span className="h-2.5 w-2.5 rounded-full bg-amber/70" />
              <span className="h-2.5 w-2.5 rounded-full bg-lime/70" />
              <span className="ml-3 font-mono text-[10px] tracking-[0.2em] text-faint">
                pulseboost-ci — build &amp; sign transcript
              </span>
            </div>
            <div className="console-scroll overflow-x-auto px-4 py-4 font-mono text-[11.5px] leading-[1.9]">
              {TERMINAL_LINES.map(([prefix, line], i) => (
                <div key={i} className="whitespace-nowrap">
                  <span className={prefix === "$" ? "text-lime" : "text-faint"}>
                    {prefix}{" "}
                  </span>
                  <span className={prefix === "$" ? "text-text" : "text-dim"}>{line}</span>
                </div>
              ))}
              <div className="mt-1 text-lime">
                $ <span className="caret">▌</span>
              </div>
            </div>
          </div>

          <a
            href="#features"
            className="group mt-8 hidden items-center gap-2 font-mono text-[10px] tracking-[0.3em] text-faint transition-colors hover:text-lime lg:flex"
          >
            <ArrowDown size={13} className="transition-transform duration-300 group-hover:translate-y-1" />
            SCROLL — FEATURES · VERIFICATION · SOURCE
          </a>
        </div>
      </div>

      {/* ticker */}
      <div className="relative border-y border-line bg-panel/60">
        <div className="flex overflow-hidden py-3 [mask-image:linear-gradient(90deg,transparent,black_8%,black_92%,transparent)]">
          <div className="ticker-track flex shrink-0 items-center gap-10 whitespace-nowrap font-mono text-[10px] tracking-[0.28em] text-dim">
            {[...TICKER, ...TICKER].map((t, i) => (
              <span key={i} className="flex items-center gap-3">
                <span className="h-1 w-1 rounded-full bg-lime/60" />
                {t}
              </span>
            ))}
          </div>
        </div>
      </div>
    </header>
  );
}
