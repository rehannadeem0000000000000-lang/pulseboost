import { AlertTriangle, XCircle } from "lucide-react";

const LIMITS = [
  {
    title: "No CPU governor control",
    body: "Scaling governors and clock floors live under /sys/devices/system/cpu — root-only. Not Shizuku, not any Play-legal API. PulseBoost works around it by freeing RAM and I/O pressure instead.",
  },
  {
    title: "No refresh-rate unlocking",
    body: "Display.Mode enumerates modes the panel and driver actually support. A 60 Hz panel stays 60 Hz; on a 120 Hz panel the app requests the max mode and reports what the OEM accepted.",
  },
  {
    title: "Shizuku is a hard prerequisite",
    body: "Every privileged action borrows the Shizuku server's identity. Without it the app degrades gracefully to a telemetry viewer and says so — it never claims a boost happened.",
  },
  {
    title: "Widget cadence bends to Doze",
    body: "The widget re-arms a 30 s alarm, but Android may stretch alarms while the device idles. Readings refresh on wake; the widget says 'updated just now' only after a real re-read.",
  },
];

const PERMISSIONS = [
  "SYSTEM_ALERT_WINDOW",
  "FOREGROUND_SERVICE",
  "FOREGROUND_SERVICE_SPECIAL_USE",
  "POST_NOTIFICATIONS",
  "QUERY_ALL_PACKAGES",
  "PACKAGE_USAGE_STATS",
  "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
  "WRITE_SETTINGS",
  "moe.shizuku.manager.permission.API_V23",
];

export function Limits() {
  return (
    <section id="limits" className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
      <div data-reveal className="mb-14">
        <p className="font-mono text-[10px] tracking-[0.3em] text-red">HONESTY LEDGER</p>
        <h2 className="mt-3 font-display text-4xl font-bold tracking-tight sm:text-5xl">
          WHAT IT CANNOT DO — <span className="text-dim">STATED UP FRONT.</span>
        </h2>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {LIMITS.map((l, i) => (
          <div
            key={l.title}
            data-reveal
            style={{ transitionDelay: `${(i % 2) * 90}ms` }}
            className="border border-line bg-panel p-6"
          >
            <div className="flex items-center gap-3">
              <XCircle size={16} className="shrink-0 text-red" />
              <h3 className="font-display text-[15px] font-semibold tracking-wide">{l.title}</h3>
            </div>
            <p className="mt-3 text-[12.5px] leading-relaxed text-dim">{l.body}</p>
          </div>
        ))}
      </div>

      <div data-reveal className="mt-10 border border-line bg-panel p-6">
        <div className="flex items-center gap-3">
          <AlertTriangle size={15} className="shrink-0 text-amber" />
          <h3 className="font-display text-[15px] font-semibold tracking-wide">
            Declared permissions — the full manifest list
          </h3>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {PERMISSIONS.map((p) => (
            <span
              key={p}
              className="border border-line bg-ink px-2.5 py-1 font-mono text-[9.5px] tracking-wider text-dim"
            >
              {p}
            </span>
          ))}
        </div>
        <p className="mt-4 font-mono text-[10.5px] leading-relaxed tracking-wide text-faint">
          note: "pm clear" on a selected app wipes that app's data and cache — the app asks for
          confirmation first, and the APPS tab marks the button red for exactly that reason.
        </p>
      </div>
    </section>
  );
}
