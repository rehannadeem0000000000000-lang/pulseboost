import { useEffect, useState } from "react";
import {
  CheckCircle2,
  Download,
  FileArchive,
  Loader2,
  PackageCheck,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import {
  APK_META,
  downloadApk,
  fmtKb,
  verifyEmbeddedApk,
  type VerifyReport,
} from "../lib/apk";
import { CommandBlock } from "./Copy";

const INSTALL_INTENT = `am start -a android.intent.action.VIEW \\
  -d "file:///sdcard/Download/${APK_META.fileName}" \\
  -t "application/vnd.android.package-archive" \\
  -f 0x10000000`;

export function VerifyConsole() {
  const [report, setReport] = useState<VerifyReport | null>(null);
  const [running, setRunning] = useState(true);
  const [showEntries, setShowEntries] = useState(false);

  useEffect(() => {
    let alive = true;
    setRunning(true);
    verifyEmbeddedApk()
      .then((r) => {
        if (alive) setReport(r);
      })
      .finally(() => alive && setRunning(false));
    return () => {
      alive = false;
    };
  }, []);

  return (
    <section id="verify" className="border-y border-line bg-panel/40">
      <div className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div data-reveal className="mb-12">
          <p className="font-mono text-[10px] tracking-[0.3em] text-cyan">VERIFICATION CONSOLE</p>
          <h2 className="mt-3 font-display text-4xl font-bold tracking-tight sm:text-5xl">
            DON'T TRUST. <span className="text-cyan">DECODE.</span>
          </h2>
          <p className="mt-4 max-w-2xl text-[13.5px] leading-relaxed text-dim">
            The APK inside this page is not a link — it's the bytes themselves, base64-encoded in{" "}
            <span className="font-mono text-cyan">src/data/apkPayload.ts</span>. This console
            decodes them in your browser, parses the ZIP central directory, inflates{" "}
            <span className="font-mono">classes.dex</span>, checks the JAR signature parts and
            recomputes the SHA-256 — live, right now.
          </p>
        </div>

        <div data-reveal className="brackets border border-line2 bg-panel">
          {/* status strip */}
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-line px-5 py-4">
            <div className="flex items-center gap-3">
              {running ? (
                <Loader2 size={17} className="animate-spin text-cyan" />
              ) : report?.ok ? (
                <ShieldCheck size={17} className="text-lime" />
              ) : (
                <XCircle size={17} className="text-red" />
              )}
              <span className="font-mono text-[11px] tracking-[0.2em]">
                {running
                  ? "DECODING EMBEDDED APK…"
                  : report?.ok
                    ? "ALL CHECKS PASS — BYTE-FOR-BYTE THE CI BUILD"
                    : "CHECK FAILED — INSPECT BELOW"}
              </span>
            </div>
            <div className="flex items-center gap-2 font-mono text-[10px] tracking-widest text-faint">
              <FileArchive size={12} />
              {APK_META.fileName} · {fmtKb(APK_META.byteLength)}
            </div>
          </div>

          {/* checks */}
          <div className="grid gap-px bg-line md:grid-cols-2 xl:grid-cols-4">
            {(report?.checks ?? []).map((c) => (
              <div key={c.id} className="bg-panel px-5 py-4">
                <div className="flex items-center gap-2">
                  {c.pass ? (
                    <CheckCircle2 size={14} className="shrink-0 text-lime" />
                  ) : (
                    <XCircle size={14} className="shrink-0 text-red" />
                  )}
                  <span className="font-mono text-[10.5px] font-medium tracking-wider text-text">
                    {c.label}
                  </span>
                </div>
                <p className="mt-2 break-all font-mono text-[10px] leading-relaxed text-faint">
                  {c.detail}
                </p>
              </div>
            ))}
            {running &&
              Array.from({ length: 7 }).map((_, i) => (
                <div key={i} className="animate-pulse bg-panel px-5 py-4">
                  <div className="h-3 w-2/3 rounded bg-line" />
                  <div className="mt-3 h-2 w-full rounded bg-line/60" />
                </div>
              ))}
          </div>

          {/* zip entries */}
          {report && (
            <div className="border-t border-line">
              <button
                onClick={() => setShowEntries((s) => !s)}
                className="flex w-full items-center justify-between px-5 py-3 font-mono text-[10px] tracking-[0.22em] text-dim transition-colors hover:text-lime"
              >
                <span className="flex items-center gap-2">
                  <PackageCheck size={12} />
                  ZIP CENTRAL DIRECTORY · {report.entries.length} ENTRIES
                </span>
                <span>{showEntries ? "HIDE −" : "SHOW +"}</span>
              </button>
              {showEntries && (
                <div className="console-scroll max-h-64 overflow-auto border-t border-line px-5 py-3">
                  <table className="w-full font-mono text-[10.5px]">
                    <thead>
                      <tr className="text-left text-faint">
                        <th className="pb-2 pr-4 font-normal tracking-wider">ENTRY</th>
                        <th className="pb-2 pr-4 font-normal tracking-wider">METHOD</th>
                        <th className="pb-2 pr-4 text-right font-normal tracking-wider">STORED</th>
                        <th className="pb-2 text-right font-normal tracking-wider">UNCOMPRESSED</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.entries.map((e) => (
                        <tr key={e.name} className="border-t border-line/50">
                          <td className="py-1.5 pr-4 text-cyan/90">{e.name}</td>
                          <td className="py-1.5 pr-4 text-dim">{e.method === 8 ? "deflated" : "stored"}</td>
                          <td className="py-1.5 pr-4 text-right text-dim">
                            {e.compressedSize.toLocaleString("en-US")}
                          </td>
                          <td className="py-1.5 text-right text-dim">
                            {e.size.toLocaleString("en-US")}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* on-device verification recipe */}
          <div className="grid gap-4 border-t border-line p-5 lg:grid-cols-2">
            <CommandBlock
              label="ON DESKTOP — SIGNATURE GATE"
              cmd={`apksigner verify --verbose --print-certs ${APK_META.fileName}
# → Verifies · APK Signature Scheme v2 · signer CN=PulseBoost Pro`}
            />
            <CommandBlock label="ON DEVICE — INSTALL VIA INTENT" cmd={INSTALL_INTENT} />
          </div>

          <div className="flex flex-wrap items-center gap-4 border-t border-line px-5 py-4">
            <button
              onClick={downloadApk}
              className="flex items-center gap-2 bg-lime px-5 py-2.5 font-mono text-[11px] font-semibold tracking-[0.18em] text-ink transition-transform hover:-translate-y-0.5"
            >
              <Download size={13} /> DOWNLOAD {APK_META.fileName.toUpperCase()}
            </button>
            <p className="font-mono text-[10px] leading-relaxed tracking-wide text-faint">
              sha256 <span className="break-all text-dim">{APK_META.sha256}</span>
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
