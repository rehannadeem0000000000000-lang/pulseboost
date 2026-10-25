import { Download, ExternalLink } from "lucide-react";
import { APK_META, downloadApk } from "../lib/apk";
import { CommandBlock } from "./Copy";

export function InstallGuide() {
  return (
    <section id="install" className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
      <div data-reveal className="mb-14">
        <p className="font-mono text-[10px] tracking-[0.3em] text-lime">FIELD DEPLOYMENT</p>
        <h2 className="mt-3 font-display text-4xl font-bold tracking-tight sm:text-5xl">
          SHIZUKU FIRST. <span className="text-dim">THEN BOOST.</span>
        </h2>
        <p className="mt-4 max-w-2xl text-[13.5px] leading-relaxed text-dim">
          Boost actions borrow Shizuku's shell/root identity — that's the honest mechanism. No
          Shizuku, no boost. Five steps, all copy-pasteable.
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <div data-reveal className="border border-line bg-panel p-6">
          <p className="font-mono text-[11px] tracking-[0.25em] text-lime">01</p>
          <h3 className="mt-2 font-display text-lg font-semibold">Get the APK</h3>
          <p className="mt-2 text-[12.5px] leading-relaxed text-dim">
            Download the signed build straight from this page — it is byte-identical to the embedded
            payload you just verified (same SHA-256).
          </p>
          <button
            onClick={downloadApk}
            className="mt-4 flex items-center gap-2 border border-lime/60 px-5 py-3 font-mono text-[11px] tracking-[0.18em] text-lime transition-colors hover:bg-lime hover:text-ink"
          >
            <Download size={13} /> {APK_META.fileName}
          </button>
        </div>

        <div data-reveal className="border border-line bg-panel p-6">
          <p className="font-mono text-[11px] tracking-[0.25em] text-lime">02</p>
          <h3 className="mt-2 font-display text-lg font-semibold">Install Shizuku</h3>
          <p className="mt-2 text-[12.5px] leading-relaxed text-dim">
            From Google Play or GitHub — package{" "}
            <span className="font-mono text-cyan">moe.shizuku.privileged.api</span>. It supplies the
            privileged binder PulseBoost talks to.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <a
              href="https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 border border-line px-3 py-2 font-mono text-[10px] tracking-widest text-dim transition-colors hover:border-cyan/60 hover:text-cyan"
            >
              PLAY STORE <ExternalLink size={11} />
            </a>
            <a
              href="https://github.com/RikkaApps/Shizuku"
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1.5 border border-line px-3 py-2 font-mono text-[10px] tracking-widest text-dim transition-colors hover:border-cyan/60 hover:text-cyan"
            >
              GITHUB <ExternalLink size={11} />
            </a>
          </div>
        </div>

        <div data-reveal className="border border-line bg-panel p-6">
          <p className="font-mono text-[11px] tracking-[0.25em] text-lime">03</p>
          <h3 className="mt-2 font-display text-lg font-semibold">Start the Shizuku server</h3>
          <p className="mt-2 text-[12.5px] leading-relaxed text-dim">
            <span className="text-text">Rooted:</span> open Shizuku → tap Start.{" "}
            <span className="text-text">Or via ADB</span> (repeat after every reboot):
          </p>
          <div className="mt-4">
            <CommandBlock cmd="adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh" />
          </div>
        </div>

        <div data-reveal className="border border-line bg-panel p-6">
          <p className="font-mono text-[11px] tracking-[0.25em] text-lime">04</p>
          <h3 className="mt-2 font-display text-lg font-semibold">Install PulseBoost Pro</h3>
          <p className="mt-2 text-[12.5px] leading-relaxed text-dim">
            Either push-install via ADB, or drop the APK in Download/ and fire the platform intent:
          </p>
          <div className="mt-4 grid gap-3">
            <CommandBlock label="ADB" cmd={`adb install ${APK_META.fileName}`} />
            <CommandBlock
              label="PLATFORM INTENT"
              cmd={`am start -a android.intent.action.VIEW \\
  -d "file:///sdcard/Download/${APK_META.fileName}" \\
  -t "application/vnd.android.package-archive" \\
  -f 0x10000000`}
            />
          </div>
        </div>

        <div data-reveal className="border border-line bg-panel p-6 lg:col-span-2">
          <p className="font-mono text-[11px] tracking-[0.25em] text-lime">05</p>
          <h3 className="mt-2 font-display text-lg font-semibold">Grant, then boost</h3>
          <p className="mt-2 text-[12.5px] leading-relaxed text-dim">
            On first launch the app raises the real Shizuku permission dialog — accept it. Then, per
            feature: overlay permission for the FPS meter, usage access for profiles, notification
            policy for DND. Faster from a desktop:
          </p>
          <div className="mt-4 grid gap-3 lg:grid-cols-2">
            <CommandBlock
              label="USAGE ACCESS — OPTIONALLY VIA SHELL"
              cmd="adb shell cmd appops set dev.pulseboost.pro GET_USAGE_STATS allow"
            />
            <CommandBlock
              label="EXEMPT FROM DOZE — OPTIONAL"
              cmd="adb shell dumpsys deviceidle whitelist +dev.pulseboost.pro"
            />
          </div>
          <p className="mt-4 font-mono text-[10.5px] tracking-wide text-faint">
            → BOOST tab · BOOST NOW · watch the Δ free-RAM line update from ActivityManager.MemoryInfo
          </p>
        </div>
      </div>
    </section>
  );
}
