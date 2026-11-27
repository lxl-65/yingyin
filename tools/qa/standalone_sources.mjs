#!/usr/bin/env node
/**
 * 独立源体检：不经过影视仓 App、不经过 PC 服务，直接按 docs/sub.json 测源。
 *
 * 用法：
 *   node tools/qa/standalone_sources.mjs
 *   node tools/qa/standalone_sources.mjs --json > reports/standalone-sources.json
 *
 * 检查项：
 *   1. list 能否在 7 秒内返回
 *   2. search(庆余年) 能否在 7 秒内返回
 *   3. 第一条结果的详情能否在 7 秒内返回
 *   4. 详情里至少有一条 m3u8/mp4 直链
 */
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const sub = JSON.parse(await readFile(resolve(root, 'docs/sub.json'), 'utf8'));
const sources = (sub.sources || []).filter((s) => s && s.api && !/127\.0\.0\.1|localhost/i.test(s.api));
const timeoutMs = Number(process.env.SOURCE_TIMEOUT_MS || 7000);
const jsonMode = process.argv.includes('--json');

function endpoint(api, params = {}) {
  const u = new URL(api);
  u.searchParams.set('ac', 'videolist');
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && `${v}` !== '') u.searchParams.set(k, v);
  return u.toString();
}

async function getJson(url) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  const started = Date.now();
  try {
    const r = await fetch(url, {
      signal: ctrl.signal,
      headers: { 'user-agent': 'Yingyin-QA/1.0' },
      redirect: 'follow',
    });
    const text = await r.text();
    return { ok: r.ok, status: r.status, ms: Date.now() - started, json: JSON.parse(text) };
  } catch (e) {
    return { ok: false, status: 0, ms: Date.now() - started, error: e?.name || String(e) };
  } finally {
    clearTimeout(timer);
  }
}

function itemsOf(json) {
  return Array.isArray(json?.list) ? json.list.filter((x) => x?.vod_id && x?.vod_name) : [];
}

function mediaCount(it) {
  const u = `${it?.vod_play_url || ''}`;
  return (u.match(/https?:\/\/[^\s#$]+/g) || []).filter((x) => /\.m3u8|\.mp4/i.test(x)).length;
}

async function checkOne(source) {
  const list = await getJson(endpoint(source.api, { pg: 1 }));
  const items = list.ok ? itemsOf(list.json) : [];
  const search = await getJson(endpoint(source.api, { wd: '庆余年' }));
  const searchItems = search.ok ? itemsOf(search.json) : [];
  let detail = { ok: false, ms: 0, items: 0, media: 0 };
  if (items.length) {
    const d = await getJson(endpoint(source.api, { ids: items[0].vod_id }));
    const list2 = d.ok ? itemsOf(d.json) : [];
    detail = {
      ok: d.ok && list2.length > 0,
      ms: d.ms,
      items: list2.length,
      media: list2.reduce((n, it) => n + mediaCount(it), 0),
      name: list2[0]?.vod_name || '',
    };
  }
  return {
    key: source.key,
    name: source.name,
    api: source.api,
    list: { ok: list.ok && items.length > 0, ms: list.ms, count: items.length, status: list.status, error: list.error },
    search: { ok: search.ok && searchItems.length > 0, ms: search.ms, count: searchItems.length },
    detail,
    pass: list.ok && items.length > 0 && search.ok && searchItems.length > 0 && detail.ok && detail.media > 0,
  };
}

const results = [];
for (const s of sources) {
  const r = await checkOne(s);
  results.push(r);
  if (!jsonMode) {
    const flag = r.pass ? 'PASS' : 'FAIL';
    console.log(
      `${flag}  ${String(r.name).padEnd(8)} list ${String(r.list.ms).padStart(4)}ms/${String(r.list.count).padStart(3)}  ` +
        `search ${String(r.search.ms).padStart(4)}ms/${String(r.search.count).padStart(3)}  ` +
        `detail ${String(r.detail.ms).padStart(4)}ms/${String(r.detail.media).padStart(2)} media`
    );
  }
}

const passed = results.filter((r) => r.pass).length;
const listOk = results.filter((r) => r.list.ok).length;
const searchOk = results.filter((r) => r.search.ok).length;
const detailOk = results.filter((r) => r.detail.ok).length;
const summary = { checked: results.length, passed, listOk, searchOk, detailOk, timeoutMs, results };
if (jsonMode) console.log(JSON.stringify(summary, null, 2));
else console.log(`\n${passed}/${results.length} 全链路通过 · list ${listOk} · search ${searchOk} · detail ${detailOk}`);

// 低于一半可用就当作真实故障，避免“看起来成功了”的假绿灯。
process.exit(passed >= Math.ceil(results.length / 2) ? 0 : 1);
