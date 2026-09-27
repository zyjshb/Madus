// Optional live diagnostic, not a CI test. No login or stored cookies are used.
// Reads only two audio bytes and never prints signed CDN URLs.
const ua = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const bvid = process.argv[2] || 'BV1xx411c7mu';
if (!/^BV[A-Za-z0-9]{10}$/.test(bvid)) throw new Error('Pass a complete BV identifier.');
const headers = { 'User-Agent': ua, Referer: 'https://www.bilibili.com/' };
const home = await fetch('https://www.bilibili.com/', { headers, signal: AbortSignal.timeout(20000) });
const cookie = home.headers.getSetCookie().map(value => value.split(';')[0]).join('; ');
await home.body?.cancel();
async function api(path, params) {
  const response = await fetch('https://api.bilibili.com' + path + '?' + new URLSearchParams(params), {
    headers: { ...headers, Cookie: cookie }, signal: AbortSignal.timeout(20000)
  });
  const json = await response.json();
  if (!response.ok || json.code !== 0) throw new Error(`Bilibili ${response.status}/${json.code}: ${json.message}`);
  return json.data;
}
const view = await api('/x/web-interface/view', { bvid });
console.log(JSON.stringify({ check: 'view', title: view.title, parts: view.pages?.length, cid: view.cid }));
try {
  const search = await api('/x/web-interface/search/type', { search_type: 'video', keyword: '纯音乐', page: '1', page_size: '30', order: 'totalrank' });
  console.log(JSON.stringify({ check: 'search', count: search.result?.length ?? 0 }));
} catch (error) { console.log(JSON.stringify({ check: 'search', error: error.message })); }
const play = await api('/x/player/playurl', { bvid, cid: String(view.cid), qn: '64', fnval: '16', fourk: '0' });
const audio = play.dash?.audio?.filter(track => track.codecs?.startsWith('mp4a')).sort((a, b) => b.bandwidth - a.bandwidth)[0];
if (!audio) throw new Error('No AAC audio in response.');
console.log(JSON.stringify({ check: 'audio', codec: audio.codecs, mime: audio.mimeType || audio.mime_type }));
const media = await fetch(audio.baseUrl || audio.base_url, {
  headers: { ...headers, Referer: 'https://www.bilibili.com/video/' + bvid, Range: 'bytes=0-1' },
  signal: AbortSignal.timeout(20000)
});
console.log(JSON.stringify({ check: 'range', status: media.status, contentRange: media.headers.get('content-range') }));
await media.body?.cancel();
if (media.status !== 206) process.exitCode = 1;
