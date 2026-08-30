/**
 * Local mock of the Cloudflare Worker runtime for end-to-end WebUI testing:
 * runs the real worker.js fetch handler against an in-memory KV.
 *
 * Start: node mock-worker.mjs [port=9999]
 */

import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const worker = await import("file://" + join(here, "worker.js").replace(/\\/g, "/"));

const store = new Map();

const env =
{
	ADMIN_TOKEN: "mock-token",
	CONTENT:
	{
		async get(key, type)
		{
			const value = store.get(key);

			if (value == null)
			{
				return null;
			}

			return type === "arrayBuffer" ? value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength) : new TextDecoder().decode(value);
		},
		async getWithMetadata(key, opts)
		{
			return { value: await this.get(key, opts && opts.type), metadata: store.get("$meta:" + key) || null };
		},
		async put(key, value, opts)
		{
			store.set(key, value instanceof ArrayBuffer ? new Uint8Array(value) : new TextEncoder().encode(String(value)));

			if (opts && opts.metadata)
			{
				store.set("$meta:" + key, opts.metadata);
			}
		},
		async delete(key)
		{
			store.delete(key);
			store.delete("$meta:" + key);
		}
	}
};

const port = Number(process.argv[2]) || 9999;

createServer(async (req, res) =>
{
	const chunks = [];

	for await (const chunk of req)
	{
		chunks.push(chunk);
	}

	const headers = { ...req.headers };

	delete headers["content-length"];
	delete headers.host;
	delete headers.connection;

	const request = new Request("http://127.0.0.1:" + port + req.url, {
		method: req.method,
		headers,
		body: req.method === "GET" || req.method === "HEAD" ? undefined : Buffer.concat(chunks)
	});

	const response = await worker.default.fetch(request, env);

	const out = Object.fromEntries(response.headers);

	res.writeHead(response.status, out);
	res.end(response.body == null ? undefined : Buffer.from(await response.arrayBuffer()));
}).listen(port, "127.0.0.1", () => console.log("[mock-worker] listening on http://127.0.0.1:" + port));
