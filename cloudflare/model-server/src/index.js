const PARTS_CONFIG = {
  "mobileclip_s2.tflite": {
    totalSize: 396881784,
    sha256: "d9dc97573d4c190e722e3bc95795a6ca866b36d8ea045be5aea3db586728baa2",
    parts: [
      { name: "mobileclip_s2.tflite.part_0", size: 134217728, offset: 0 },
      { name: "mobileclip_s2.tflite.part_1", size: 134217728, offset: 134217728 },
      { name: "mobileclip_s2.tflite.part_2", size: 128446328, offset: 268435456 }
    ]
  }
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const key = decodeURIComponent(url.pathname.slice(1));

    if (!key) {
      return new Response(JSON.stringify({
        status: "online",
        service: "Kiwi Photos AI Model CDN",
        models: Object.keys(PARTS_CONFIG)
      }, null, 2), {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*"
        }
      });
    }

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
          "Access-Control-Allow-Headers": "*",
        },
      });
    }

    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405 });
    }

    // ── Manifest Endpoint ──────────────────────────────────────
    if (key === "manifest.json" || key === "models/manifest.json") {
      return new Response(JSON.stringify({
        version: "1.0",
        baseUrl: url.origin,
        models: PARTS_CONFIG
      }, null, 2), {
        headers: {
          "Content-Type": "application/json",
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "public, max-age=3600"
        }
      });
    }

    // ── Multipart Virtual File (e.g. mobileclip_s2.tflite) ─────
    if (PARTS_CONFIG[key]) {
      const config = PARTS_CONFIG[key];
      const rangeHeader = request.headers.get("Range");

      let rangeStart = 0;
      let rangeEnd = config.totalSize - 1;
      let isPartial = false;

      if (rangeHeader) {
        const match = rangeHeader.match(/bytes=(\d*)-(\d*)/);
        if (match) {
          isPartial = true;
          if (match[1] !== "") rangeStart = parseInt(match[1], 10);
          if (match[2] !== "") rangeEnd = parseInt(match[2], 10);
          rangeEnd = Math.min(rangeEnd, config.totalSize - 1);
        }
      }

      if (rangeStart > rangeEnd || rangeStart >= config.totalSize) {
        return new Response("Range Not Satisfiable", {
          status: 416,
          headers: {
            "Content-Range": `bytes */${config.totalSize}`,
            "Access-Control-Allow-Origin": "*"
          }
        });
      }

      const contentLength = rangeEnd - rangeStart + 1;

      // Identify which parts intersect [rangeStart, rangeEnd]
      const neededParts = config.parts.filter(p => {
        const pEnd = p.offset + p.size - 1;
        return rangeStart <= pEnd && rangeEnd >= p.offset;
      });

      const { readable, writable } = new TransformStream();

      // Stream parts asynchronously
      (async () => {
        const writer = writable.getWriter();
        try {
          for (const p of neededParts) {
            const pEnd = p.offset + p.size - 1;
            const sliceStart = Math.max(0, rangeStart - p.offset);
            const sliceEnd = Math.min(p.size - 1, rangeEnd - p.offset);
            const sliceLen = sliceEnd - sliceStart + 1;

            const partObj = await env.KIWI_MODELS.get(p.name, {
              range: { offset: sliceStart, length: sliceLen }
            });

            if (partObj && partObj.body) {
              const reader = partObj.body.getReader();
              while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                await writer.write(value);
              }
            }
          }
        } catch (err) {
          console.error("Stream error:", err);
        } finally {
          await writer.close();
        }
      })();

      const headers = new Headers();
      headers.set("Content-Type", "application/octet-stream");
      headers.set("Accept-Ranges", "bytes");
      headers.set("Access-Control-Allow-Origin", "*");
      headers.set("Cache-Control", "public, max-age=31536000, immutable");
      headers.set("Content-Length", contentLength.toString());

      if (isPartial) {
        headers.set("Content-Range", `bytes ${rangeStart}-${rangeEnd}/${config.totalSize}`);
        return new Response(readable, { status: 206, headers });
      } else {
        return new Response(readable, { status: 200, headers });
      }
    }

    // ── Direct R2 Object Request ───────────────────────────────
    const rangeHeader = request.headers.get("Range");
    let options = {};
    if (rangeHeader) {
      options.range = request.headers;
    }

    const object = await env.KIWI_MODELS.get(key, options);

    if (object === null) {
      return new Response("Object Not Found: " + key, {
        status: 404,
        headers: { "Access-Control-Allow-Origin": "*" }
      });
    }

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("etag", object.httpEtag);
    headers.set("Access-Control-Allow-Origin", "*");
    headers.set("Cache-Control", "public, max-age=31536000, immutable");
    headers.set("Accept-Ranges", "bytes");

    if (key.endsWith(".tflite")) {
      headers.set("Content-Type", "application/octet-stream");
    }

    if (object.range) {
      const start = object.range.offset;
      const end = object.range.offset + object.range.length - 1;
      headers.set("Content-Range", `bytes ${start}-${end}/${object.size}`);
      headers.set("Content-Length", object.range.length.toString());
      return new Response(object.body, { status: 206, headers });
    }

    headers.set("Content-Length", object.size.toString());
    return new Response(object.body, { status: 200, headers });
  },
};
