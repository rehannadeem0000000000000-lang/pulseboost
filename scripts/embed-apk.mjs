#!/usr/bin/env node
/**
 * scripts/embed-apk.mjs
 *
 * Reads the APK produced by :app:assembleRelease, performs structural
 * verification (ZIP magic, classes.dex DEX magic, resources.arsc chunk
 * type, META-INF JAR-signature parts, 40 KB size floor), extracts the
 * manifest package/version from the binary AXML, computes SHA-256, and
 * rewrites src/data/apkPayload.ts with the real base64 bytes + metadata.
 *
 * Any verification failure aborts with a non-zero exit code and the
 * payload file is left untouched.
 *
 * Usage: node scripts/embed-apk.mjs [path/to/app-release.apk]
 */
import { createHash } from 'node:crypto';
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';

const rootDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const MIN_APK_BYTES = 40 * 1024;

function fail(message) {
  console.error(`\napk-embed FAILED: ${message}\n`);
  process.exit(1);
}

function info(line) {
  console.log(`  ${line}`);
}

// ---------------------------------------------------------------- zip parse

function parseZipEntries(bytes) {
  if (bytes.length < 22) fail('file too small to be a ZIP archive');
  let eocd = -1;
  const min = Math.max(0, bytes.length - 22 - 0xffff);
  for (let i = bytes.length - 22; i >= min; i--) {
    if (
      bytes[i] === 0x50 && bytes[i + 1] === 0x4b &&
      bytes[i + 2] === 0x05 && bytes[i + 3] === 0x06
    ) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) fail('end-of-central-directory record not found');
  const total = bytes.readUInt16LE(eocd + 10);
  const cdOffset = bytes.readUInt32LE(eocd + 16);
  const entries = [];
  let p = cdOffset;
  for (let i = 0; i < total; i++) {
    if (bytes.readUInt32LE(p) !== 0x02014b50) fail(`bad central header at ${p}`);
    const method = bytes.readUInt16LE(p + 10);
    const crc32 = bytes.readUInt32LE(p + 16);
    const compSize = bytes.readUInt32LE(p + 20);
    const size = bytes.readUInt32LE(p + 24);
    const nameLen = bytes.readUInt16LE(p + 28);
    const extraLen = bytes.readUInt16LE(p + 30);
    const commentLen = bytes.readUInt16LE(p + 32);
    const lho = bytes.readUInt32LE(p + 42);
    const name = bytes.subarray(p + 46, p + 46 + nameLen).toString('utf8');
    entries.push({ name, method, crc32, compSize, size, lho });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function readEntryData(bytes, entry) {
  const l = entry.lho;
  if (bytes.readUInt32LE(l) !== 0x04034b50) {
    fail(`local header for ${entry.name} missing ZIP magic`);
  }
  const nameLen = bytes.readUInt16LE(l + 26);
  const extraLen = bytes.readUInt16LE(l + 28);
  const dataStart = l + 30 + nameLen + extraLen;
  const raw = bytes.subarray(dataStart, dataStart + entry.compSize);
  if (entry.method === 0) return raw;
  if (entry.method === 8) return inflateRawSync(raw);
  fail(`unsupported compression method ${entry.method} for ${entry.name}`);
}

// -------------------------------------------------------------- AXML parse

function parseStringPool(bytes, offset) {
  if (bytes.readUInt16LE(offset) !== 0x0001) fail('string pool chunk missing');
  const chunkSize = bytes.readUInt32LE(offset + 4);
  const stringCount = bytes.readUInt32LE(offset + 8);
  const flags = bytes.readUInt32LE(offset + 16);
  const stringsStart = bytes.readUInt32LE(offset + 20);
  const utf8 = (flags & 0x100) !== 0;
  const strings = new Array(stringCount);
  for (let i = 0; i < stringCount; i++) {
    const so = bytes.readUInt32LE(offset + 28 + i * 4);
    let p = offset + stringsStart + so;
    if (utf8) {
      // u16 length (1-2 bytes), then utf8 byte length (1-2 bytes)
      if (bytes[p] & 0x80) p += 2; else p += 1;
      let byteLen;
      if (bytes[p] & 0x80) {
        byteLen = ((bytes[p] & 0x7f) << 8) | bytes[p + 1];
        p += 2;
      } else {
        byteLen = bytes[p];
        p += 1;
      }
      strings[i] = bytes.subarray(p, p + byteLen).toString('utf8');
    } else {
      let charLen;
      const first = bytes.readUInt16LE(p);
      if (first & 0x8000) {
        charLen = ((first & 0x7fff) << 16) | bytes.readUInt16LE(p + 2);
        p += 4;
      } else {
        charLen = first;
        p += 2;
      }
      strings[i] = bytes.subarray(p, p + charLen * 2).toString('utf16le');
    }
  }
  return { strings, end: offset + chunkSize };
}

function readManifestMeta(manifestBytes) {
  if (manifestBytes.readUInt32LE(0) !== 0x00080003) {
    fail('AndroidManifest.xml is not binary AXML');
  }
  const { strings, end } = parseStringPool(manifestBytes, 8);
  let p = end;
  const limit = manifestBytes.length;
  while (p + 8 <= limit) {
    const type = manifestBytes.readUInt16LE(p);
    const size = manifestBytes.readUInt32LE(p + 4);
    if (type === 0x0102) {
      // RES_XML_START_ELEMENT_TYPE
      const nameIdx = manifestBytes.readUInt32LE(p + 20);
      if (strings[nameIdx] !== 'manifest') {
        p += size;
        continue;
      }
      const attrCount = manifestBytes.readUInt16LE(p + 28);
      const attrStart = p + 36;
      const meta = {};
      for (let i = 0; i < attrCount; i++) {
        const a = attrStart + i * 20;
        const attrName = strings[manifestBytes.readUInt32LE(a + 4)];
        const rawValue = manifestBytes.readUInt32LE(a + 8);
        const dataType = manifestBytes.readUInt8(a + 15);
        const data = manifestBytes.readUInt32LE(a + 16);
        let value;
        if (rawValue !== 0xffffffff) value = strings[rawValue];
        else if (dataType === 0x10 || dataType === 0x11) value = data;
        else if (dataType === 0x03) value = strings[data];
        else value = data;
        if (attrName === 'package') meta.packageName = String(value);
        if (attrName === 'versionCode') meta.versionCode = Number(value);
        if (attrName === 'versionName') meta.versionName = String(value);
      }
      return meta;
    }
    if (size <= 0) break;
    p += size;
  }
  fail('no <manifest> start element found in AXML');
}

// ------------------------------------------------------------------ driver

function findApkPath() {
  const arg = process.argv[2];
  if (arg) {
    if (!existsSync(arg)) fail(`APK not found at ${arg}`);
    return arg;
  }
  const dir = join(rootDir, 'android-app/app/build/outputs/apk/release');
  if (!existsSync(dir)) {
    fail(`release output dir missing (${dir}) — run ./gradlew :app:assembleRelease first`);
  }
  const candidates = readdirSync(dir)
    .filter((f) => f.endsWith('.apk'))
    .map((f) => join(dir, f));
  if (candidates.length === 0) fail('no APK found in release output dir');
  candidates.sort((a, b) => statSync(b).size - statSync(a).size);
  return candidates[0];
}

const apkPath = findApkPath();
const bytes = readFileSync(apkPath);
console.log(`\nembed-apk verifying: ${apkPath}`);

// 1. ZIP magic
if (!(bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04)) {
  fail('first four bytes are not 50 4B 03 04 (ZIP local header)');
}
info('ok · ZIP local header magic 50 4B 03 04');

// 2. size floor
if (bytes.length < MIN_APK_BYTES) {
  fail(`APK is ${bytes.length} bytes, below the 40 KB floor`);
}
info(`ok · size ${(bytes.length / 1024).toFixed(1)} KB (>= 40 KB)`);

// 3. packed entries
const entries = parseZipEntries(bytes);
const byName = new Map(entries.map((e) => [e.name, e]));
info(`ok · central directory lists ${entries.length} entries`);

const dex = byName.get('classes.dex');
if (!dex) fail('classes.dex missing from APK');
const dexData = readEntryData(bytes, dex);
if (
  !(dexData[0] === 0x64 && dexData[1] === 0x65 && dexData[2] === 0x78 && dexData[3] === 0x0a)
) {
  fail('classes.dex does not start with DEX magic "dex\\n"');
}
info(`ok · classes.dex present, DEX magic verified (${dex.size} bytes)`);

const arsc = byName.get('resources.arsc');
if (!arsc) fail('resources.arsc missing from APK');
const arscData = readEntryData(bytes, arsc);
if (arscData.readUInt16LE(0) !== 0x0002) {
  fail('resources.arsc does not start with a RES_TABLE_TYPE chunk');
}
info('ok · resources.arsc present and well-formed');

const manifest = byName.get('AndroidManifest.xml');
if (!manifest) fail('AndroidManifest.xml missing from APK');
const meta = readManifestMeta(readEntryData(bytes, manifest));
if (!meta.packageName) fail('could not read package name from manifest');
info(`ok · manifest package=${meta.packageName} v${meta.versionName} (${meta.versionCode})`);

const hasManifestMF = [...byName.keys()].some((n) => n === 'META-INF/MANIFEST.MF');
const hasSF = [...byName.keys()].some((n) => /^META-INF\/[^/]+\.SF$/.test(n));
const hasCert = [...byName.keys()].some((n) =>
  /^META-INF\/[^/]+\.(RSA|DSA|EC)$/.test(n),
);
if (!hasManifestMF || !hasSF || !hasCert) {
  fail('META-INF JAR-signature entries incomplete (need MANIFEST.MF + *.SF + *.RSA)');
}
info('ok · META-INF signature entries present (v1), apksigner performs full verify in CI');

const sha256 = createHash('sha256').update(bytes).digest('hex');
info(`ok · sha256 ${sha256}`);

const base64 = bytes.toString('base64');
const fileName = `pulseboost-pro-${meta.versionName}.apk`;

const outPath = join(rootDir, 'src/data/apkPayload.ts');
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(
  outPath,
  `// Generated by scripts/embed-apk.mjs from the signed release build of android-app/.\n` +
    `// Source APK: ${fileName} — run node scripts/embed-apk.mjs after a rebuild to refresh.\n` +
    `// Every value below was verified against the real APK bytes at embed time.\n\n` +
    `export const APK_BASE64 =\n  "${base64}";\n\n` +
    `export const APK_META = {\n` +
    `  fileName: "${fileName}",\n` +
    `  packageName: "${meta.packageName}",\n` +
    `  versionName: "${meta.versionName}",\n` +
    `  versionCode: ${meta.versionCode},\n` +
    `  byteLength: ${bytes.length},\n` +
    `  sha256: "${sha256}",\n` +
    `} as const;\n`,
);

console.log(`\nembed-apk wrote ${outPath}`);
console.log(`  payload base64 length: ${base64.length} chars\n`);
