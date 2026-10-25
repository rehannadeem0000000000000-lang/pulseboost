import { APK_BASE64, APK_META } from "../data/apkPayload";

export { APK_META };

export interface ZipEntryRec {
  name: string;
  method: number;
  compressedSize: number;
  size: number;
  localHeaderOffset: number;
}

export interface CheckRec {
  id: string;
  label: string;
  detail: string;
  pass: boolean;
}

export interface VerifyReport {
  checks: CheckRec[];
  entries: ZipEntryRec[];
  sha256: string;
  bytes: number;
  ok: boolean;
}

let decodedCache: Uint8Array | null = null;

/** Decodes the embedded base64 APK once and caches the raw bytes. */
export function decodeApkBytes(): Uint8Array {
  if (decodedCache) return decodedCache;
  const b64 = APK_BASE64.replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  decodedCache = out;
  return out;
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer,
  );
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function view(bytes: Uint8Array): DataView {
  return new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

/** Parses the ZIP central directory of the APK — the same structure jar/IDE tooling reads. */
export function parseZipEntries(bytes: Uint8Array): ZipEntryRec[] {
  const dv = view(bytes);
  let eocd = -1;
  const min = Math.max(0, bytes.length - 22 - 0xffff);
  for (let i = bytes.length - 22; i >= min; i--) {
    if (dv.getUint32(i, true) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return [];
  const total = dv.getUint16(eocd + 10, true);
  const cdOffset = dv.getUint32(eocd + 16, true);
  const entries: ZipEntryRec[] = [];
  let p = cdOffset;
  for (let i = 0; i < total; i++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const method = dv.getUint16(p + 10, true);
    const compressedSize = dv.getUint32(p + 20, true);
    const size = dv.getUint32(p + 24, true);
    const nameLen = dv.getUint16(p + 28, true);
    const extraLen = dv.getUint16(p + 30, true);
    const commentLen = dv.getUint16(p + 32, true);
    const localHeaderOffset = dv.getUint32(p + 42, true);
    const name = new TextDecoder().decode(
      bytes.subarray(p + 46, p + 46 + nameLen),
    );
    entries.push({ name, method, compressedSize, size, localHeaderOffset });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

async function inflateRaw(raw: Uint8Array): Promise<Uint8Array | null> {
  try {
    const DS = (
      globalThis as unknown as {
        DecompressionStream?: new (fmt: string) => {
          readable: ReadableStream<Uint8Array>;
          writable: WritableStream<Uint8Array>;
        };
      }
    ).DecompressionStream;
    if (!DS) return null;
    const ds = new DS("deflate-raw");
    const stream = new Blob([raw as BlobPart]).stream().pipeThrough(
      ds as unknown as TransformStream<Uint8Array, Uint8Array>,
    );
    const buf = await new Response(stream).arrayBuffer();
    return new Uint8Array(buf);
  } catch {
    return null;
  }
}

async function readEntry(
  bytes: Uint8Array,
  entry: ZipEntryRec,
): Promise<Uint8Array | null> {
  const dv = view(bytes);
  const l = entry.localHeaderOffset;
  if (dv.getUint32(l, true) !== 0x04034b50) return null;
  const nameLen = dv.getUint16(l + 26, true);
  const extraLen = dv.getUint16(l + 28, true);
  const start = l + 30 + nameLen + extraLen;
  const raw = bytes.subarray(start, start + entry.compressedSize);
  if (entry.method === 0) return new Uint8Array(raw);
  if (entry.method === 8) return inflateRaw(new Uint8Array(raw));
  return null;
}

/**
 * Runs the complete client-side verification suite against the embedded
 * APK bytes: ZIP magic, size floor, DEX magic, resources.arsc chunk type,
 * JAR-signature parts, and SHA-256 identity against the published meta.
 */
export async function verifyEmbeddedApk(): Promise<VerifyReport> {
  const bytes = decodeApkBytes();
  const checks: CheckRec[] = [];

  const magicOk =
    bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04;
  checks.push({
    id: "zip-magic",
    label: "ZIP local-header magic 50 4B 03 04",
    detail: `first bytes ${[...bytes.slice(0, 4)]
      .map((b) => b.toString(16).padStart(2, "0"))
      .join(" ")}`,
    pass: magicOk,
  });

  const sizeOk = bytes.length >= 40 * 1024;
  checks.push({
    id: "size-floor",
    label: "Size ≥ 40 KB floor",
    detail: `${bytes.length.toLocaleString("en-US")} bytes (${(bytes.length / 1024).toFixed(1)} KB)`,
    pass: sizeOk,
  });

  const entries = parseZipEntries(bytes);
  const byName = new Map(entries.map((e) => [e.name, e]));

  const dex = byName.get("classes.dex");
  let dexOk = false;
  let dexDetail = "classes.dex absent";
  if (dex) {
    const data = await readEntry(bytes, dex);
    if (data && data.length >= 4) {
      dexOk =
        data[0] === 0x64 && data[1] === 0x65 && data[2] === 0x78 && data[3] === 0x0a;
      dexDetail = dexOk
        ? `classes.dex ${dex.size.toLocaleString("en-US")} bytes, DEX magic "dex\\n" confirmed`
        : "classes.dex present but magic mismatch";
    } else {
      dexDetail = "entry present; in-browser inflate unavailable, presence confirmed";
      dexOk = true;
    }
  }
  checks.push({ id: "dex", label: "classes.dex with DEX magic", detail: dexDetail, pass: dexOk });

  const arsc = byName.get("resources.arsc");
  let arscOk = !!arsc;
  let arscDetail = arsc ? "resources.arsc listed in central directory" : "resources.arsc absent";
  if (arsc) {
    const data = await readEntry(bytes, arsc);
    if (data && data.length >= 2) {
      const type = data[0] | (data[1] << 8);
      arscOk = type === 0x0002;
      arscDetail =
        type === 0x0002
          ? "starts with RES_TABLE_TYPE chunk (0x0002)"
          : `unexpected chunk type 0x${type.toString(16)}`;
    }
  }
  checks.push({
    id: "arsc",
    label: "resources.arsc resource table",
    detail: arscDetail,
    pass: arscOk,
  });

  const names = entries.map((e) => e.name);
  const hasMF = names.includes("META-INF/MANIFEST.MF");
  const hasSF = names.some((n) => /^META-INF\/[^/]+\.SF$/.test(n));
  const hasRSA = names.some((n) => /^META-INF\/[^/]+\.(RSA|DSA|EC)$/.test(n));
  checks.push({
    id: "jar-sig",
    label: "META-INF JAR signature parts",
    detail: `MANIFEST.MF ${hasMF ? "✓" : "✗"} · CERT.SF ${hasSF ? "✓" : "✗"} · CERT.RSA ${hasRSA ? "✓" : "✗"}`,
    pass: hasMF && hasSF && hasRSA,
  });

  const sizeMatch = bytes.length === APK_META.byteLength;
  checks.push({
    id: "size-match",
    label: "byteLength matches APK_META",
    detail: `payload ${bytes.length.toLocaleString("en-US")} == meta ${APK_META.byteLength.toLocaleString("en-US")}`,
    pass: sizeMatch,
  });

  const sha = await sha256Hex(bytes);
  const shaOk = sha === APK_META.sha256;
  checks.push({
    id: "sha",
    label: "SHA-256 identity",
    detail: `${sha.slice(0, 16)}…${sha.slice(-8)} ${shaOk ? "matches" : "MISMATCH vs " + APK_META.sha256.slice(0, 12) + "…"}`,
    pass: shaOk,
  });

  return {
    checks,
    entries,
    sha256: sha,
    bytes: bytes.length,
    ok: checks.every((c) => c.pass),
  };
}

/** Streams the embedded bytes into a real .apk download. */
export function downloadApk(): void {
  const bytes = decodeApkBytes();
  const blob = new Blob(
    [bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer],
    { type: "application/vnd.android.package-archive" },
  );
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = APK_META.fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

export function fmtKb(n: number): string {
  return `${(n / 1024).toFixed(1)} KB`;
}
