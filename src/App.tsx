import { useEffect } from "react";
import { Zap } from "lucide-react";
import { Hero } from "./components/Hero";
import { FeatureMatrix } from "./components/FeatureMatrix";
import { VerifyConsole } from "./components/VerifyConsole";
import { InstallGuide } from "./components/InstallGuide";
import { SourceExplorer } from "./components/SourceExplorer";
import { Limits } from "./components/Limits";
import { APK_META, fmtKb } from "./lib/apk";

const NAV_LINKS = [
  ["FEATURES", "#features"],
  ["VERIFY", "#verify"],
  ["INSTALL", "#install"],
  ["SOURCE", "#source"],
  ["LIMITS", "#limits"],
] as const;

export default function App() {
  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.1, rootMargin: "0px 0px -6% 0px" },
    );
    document.querySelectorAll("[data-reveal]").forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);

  return (
    <div className="min-h-screen bg-ink text-text">
      {/* nav */}
      <nav className="fixed inset-x-0 top-0 z-50 border-b border-line bg-ink/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3.5">
          <a href="#" className="flex items-center gap-3">
            <span className="flex h-7 w-7 items-center justify-center bg-lime text-ink">
              <Zap size={15} strokeWidth={2.4} />
            </span>
            <span className="font-mono text-[12px] font-semibold tracking-[0.22em]">
              PULSEBOOST <span className="text-lime">PRO</span>
            </span>
          </a>
          <div className="hidden items-center gap-6 md:flex">
            {NAV_LINKS.map(([label, href]) => (
              <a
                key={href}
                href={href}
                className="font-mono text-[10px] tracking-[0.24em] text-dim transition-colors hover:text-lime"
              >
                {label}
              </a>
            ))}
          </div>
          <span className="border border-line px-2.5 py-1 font-mono text-[9.5px] tracking-[0.18em] text-dim">
            v{APK_META.versionName} · {fmtKb(APK_META.byteLength)}
          </span>
        </div>
      </nav>

      <main>
        <Hero />
        <FeatureMatrix />
        <VerifyConsole />
        <InstallGuide />
        <SourceExplorer />
        <Limits />
      </main>

      {/* footer */}
      <footer className="border-t border-line bg-panel/60">
        <div className="mx-auto max-w-7xl px-6 py-14">
          <div className="grid gap-10 lg:grid-cols-[1.2fr_1fr]">
            <div>
              <p className="font-mono text-[10px] tracking-[0.3em] text-lime">PROVENANCE</p>
              <p className="mt-4 break-all font-mono text-[11px] leading-relaxed text-dim">
                sha256 {APK_META.sha256}
              </p>
              <p className="mt-3 font-mono text-[11px] leading-relaxed text-dim">
                {APK_META.packageName} · v{APK_META.versionName} ({APK_META.versionCode}) ·{" "}
                {APK_META.fileName}
              </p>
              <p className="mt-6 max-w-xl text-[12.5px] leading-relaxed text-dim">
                Built end-to-end by <span className="font-mono text-cyan">.github/workflows/build-apk.yml</span>:
                Gradle assembles and signs the release, gates it on structure and{" "}
                <span className="font-mono text-cyan">apksigner verify</span>, then{" "}
                <span className="font-mono text-cyan">scripts/embed-apk.mjs</span> re-embeds the
                verified bytes into this page. Sign with your own key via the{" "}
                <span className="font-mono">RELEASE_KEYSTORE_BASE64</span> repository secret.
              </p>
            </div>
            <div className="font-mono text-[10.5px] leading-loose tracking-wider text-faint">
              <p>ENGINEERING OVER PROMISES.</p>
              <p>IF A TOGGLE CANNOT WORK, THE APP SAYS SO.</p>
              <p>IF A NUMBER CHANGES, A SYSTEM API PRODUCED IT.</p>
              <p className="mt-4 text-dim">
                PULSEBOOST PRO · SHIZUKU-POWERED BOOSTER · SDK 24→34
              </p>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
