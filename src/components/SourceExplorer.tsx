import { useMemo, useState } from "react";
import { FileCode2, FolderClosed } from "lucide-react";

import manifestXml from "../../android-app/app/src/main/AndroidManifest.xml?raw";
import mainActivityKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/MainActivity.kt?raw";
import shizukuManagerKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/ShizukuManager.kt?raw";
import statsReaderKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/StatsReader.kt?raw";
import gameModeHelperKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/GameModeHelper.kt?raw";
import usageDetectKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/UsageDetect.kt?raw";
import overlayServiceKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/BoostOverlayService.kt?raw";
import monitorServiceKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/BoostMonitorService.kt?raw";
import widgetProviderKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/BoostWidgetProvider.kt?raw";
import profileStoreKt from "../../android-app/app/src/main/java/dev/pulseboost/pro/ProfileStore.kt?raw";
import appGradle from "../../android-app/app/build.gradle?raw";
import proguardRules from "../../android-app/app/proguard-rules.pro?raw";
import readmeMd from "../../android-app/README.md?raw";
import workflowYml from "../../.github/workflows/build-apk.yml?raw";
import embedScript from "../../scripts/embed-apk.mjs?raw";

interface SourceFile {
  path: string;
  src: string;
}

interface SourceGroup {
  group: string;
  files: SourceFile[];
}

const TREE: SourceGroup[] = [
  {
    group: "android-app · kotlin",
    files: [
      { path: "MainActivity.kt", src: mainActivityKt },
      { path: "ShizukuManager.kt", src: shizukuManagerKt },
      { path: "StatsReader.kt", src: statsReaderKt },
      { path: "GameModeHelper.kt", src: gameModeHelperKt },
      { path: "UsageDetect.kt", src: usageDetectKt },
      { path: "BoostOverlayService.kt", src: overlayServiceKt },
      { path: "BoostMonitorService.kt", src: monitorServiceKt },
      { path: "BoostWidgetProvider.kt", src: widgetProviderKt },
      { path: "ProfileStore.kt", src: profileStoreKt },
    ],
  },
  {
    group: "manifest & build",
    files: [
      { path: "AndroidManifest.xml", src: manifestXml },
      { path: "app/build.gradle", src: appGradle },
      { path: "proguard-rules.pro", src: proguardRules },
      { path: "android-app/README.md", src: readmeMd },
    ],
  },
  {
    group: "ci & tooling",
    files: [
      { path: ".github/workflows/build-apk.yml", src: workflowYml },
      { path: "scripts/embed-apk.mjs", src: embedScript },
    ],
  },
];

const ALL = TREE.flatMap((g) => g.files);

export function SourceExplorer() {
  const [active, setActive] = useState<SourceFile>(ALL[1]); // ShizukuManager first
  const lines = useMemo(() => active.src.replace(/\n$/, "").split("\n"), [active]);

  return (
    <section id="source" className="border-y border-line bg-panel/40">
      <div className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div data-reveal className="mb-12">
          <p className="font-mono text-[10px] tracking-[0.3em] text-lime">SOURCE EXPLORER</p>
          <h2 className="mt-3 font-display text-4xl font-bold tracking-tight sm:text-5xl">
            READ THE CODE <span className="text-dim">THAT COMPILED INTO THE APK.</span>
          </h2>
          <p className="mt-4 max-w-2xl text-[13.5px] leading-relaxed text-dim">
            These are the exact files the Gradle build ingested — served raw from the repository
            tree. Judge every exec call yourself.
          </p>
        </div>

        <div data-reveal className="grid overflow-hidden border border-line2 bg-panel lg:grid-cols-[290px_1fr]">
          {/* tree */}
          <aside className="console-scroll max-h-[560px] overflow-y-auto border-b border-line lg:border-b-0 lg:border-r">
            {TREE.map((g) => (
              <div key={g.group}>
                <p className="flex items-center gap-2 px-4 pb-1 pt-4 font-mono text-[9px] tracking-[0.24em] text-faint">
                  <FolderClosed size={11} /> {g.group.toUpperCase()}
                </p>
                {g.files.map((f) => (
                  <button
                    key={f.path}
                    onClick={() => setActive(f)}
                    className={`flex w-full items-center gap-2 px-4 py-2 text-left font-mono text-[11px] transition-colors ${
                      active.path === f.path
                        ? "bg-lime/10 text-lime"
                        : "text-dim hover:bg-panel2 hover:text-text"
                    }`}
                  >
                    <FileCode2 size={12} className="shrink-0 opacity-60" />
                    {f.path}
                  </button>
                ))}
              </div>
            ))}
            <div className="h-4" />
          </aside>

          {/* pane */}
          <div className="min-w-0">
            <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
              <span className="truncate font-mono text-[11px] text-cyan">{active.path}</span>
              <span className="shrink-0 font-mono text-[9.5px] tracking-[0.2em] text-faint">
                {lines.length} LINES · {active.src.length.toLocaleString("en-US")} BYTES
              </span>
            </div>
            <div className="code-scroll max-h-[520px] overflow-auto">
              <pre className="px-4 py-3 font-mono text-[11px] leading-[1.75]">
                {lines.map((line, i) => (
                  <div key={i} className="flex">
                    <span className="w-9 shrink-0 select-none pr-4 text-right text-faint/50">
                      {i + 1}
                    </span>
                    <span className="whitespace-pre text-[#aab6cf]">{line || " "}</span>
                  </div>
                ))}
              </pre>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
