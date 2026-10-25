import { useState } from "react";
import { Check, Copy } from "lucide-react";

export function CopyButton({ text, label }: { text: string; label?: string }) {
  const [done, setDone] = useState(false);
  return (
    <button
      onClick={() => {
        navigator.clipboard?.writeText(text).catch(() => {});
        setDone(true);
        setTimeout(() => setDone(false), 1600);
      }}
      className="inline-flex items-center gap-2 border border-line bg-panel px-3 py-1.5 font-mono text-[11px] tracking-wider text-dim transition-colors hover:border-lime/60 hover:text-lime"
    >
      {done ? <Check size={13} className="text-lime" /> : <Copy size={13} />}
      {done ? "COPIED" : (label ?? "COPY")}
    </button>
  );
}

export function CommandBlock({ cmd, label }: { cmd: string; label?: string }) {
  return (
    <div className="group border border-line bg-ink/80">
      <div className="flex items-center justify-between border-b border-line px-4 py-2">
        <span className="font-mono text-[10px] tracking-[0.18em] text-faint">
          {label ?? "TERMINAL"}
        </span>
        <CopyButton text={cmd} />
      </div>
      <pre className="console-scroll overflow-x-auto px-4 py-3 font-mono text-[12px] leading-relaxed text-cyan">
        {cmd}
      </pre>
    </div>
  );
}
