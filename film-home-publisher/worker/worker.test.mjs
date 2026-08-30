/**
 * Local smoke test for worker.js: drives the fetch handler with an in-memory
 * KV mock. Verifies routing, auth, validation and CORS without network.
 *
 * Run: node worker.test.mjs
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const worker = await import("file://" + join(here, "worker.js").replace(/\\/g, "/"));

const store = new Map();

const env =
{
	ADMIN_TOKEN: "test-token",
	CONTENT:
	{
		async get(key, type)
		{
			const value = store.get(key);

			if (value == null)
			{
				return null;
			}

			return type === "arrayBuffer" ? value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength) : value;
		},
		async getWithMetadata(key, opts)
		{
			const entry = store.get("$meta:" + key);
			const value = await this.get(key, opts && opts.type);

			return { value, metadata: entry || null };
		},
		async put(key, value, opts)
		{
			const bytes = value instanceof ArrayBuffer ? new Uint8Array(value) : new TextEncoder().encode(String(value));

			store.set(key, bytes);

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

let passed = 0;
let failed = 0;

function check(name, condition, extra)
{
	if (condition)
	{
		passed++;
		console.log("  ok", name);
	}
	else
	{
		failed++;
		console.error("FAIL", name, extra === undefined ? "" : extra);
	}
}

function request(method, path, body, headers)
{
	return new Request("https://worker.test" + path, { method, body: body === undefined ? undefined : body, headers });
}

async function run(handler, request)
{
	const response = await worker.default.fetch(request, env);

	return response;
}

const json = (object) => JSON.stringify(object);

/* Health + public reads */
check("health 200", (await run(null, request("GET", "/health"))).status === 200);

let response = await run(null, request("GET", "/api/ads"));

check("empty ads 200 items[]", response.status === 200 && (await response.json()).items.length === 0);

store.set("content:news", json({ items: [{ id: "n1" }] }));
response = await run(null, request("GET", "/api/news"));

check("news served", response.status === 200 && (await response.json()).items[0].id === "n1");
check("news cache header", response.headers.get("Cache-Control") === "public, max-age=60");
check("cors header", response.headers.get("Access-Control-Allow-Origin") === "*");

/* Auth */
check("unauth put 401", (await run(null, request("PUT", "/api/ads", json({ items: [] }), { "Content-Type": "application/json" }))).status === 401);
check("wrong token 401", (await run(null, request("PUT", "/api/ads", json({ items: [] }), { "Authorization": "Bearer nope" }))).status === 401);

/* JSON push */
const auth = { "Authorization": "Bearer test-token", "Content-Type": "application/json" };

response = await run(null, request("PUT", "/api/ads", json({ items: [{ image: "x" }] }), auth));

check("auth put ads 200", response.status === 200);
check("ads persisted", (await run(null, request("GET", "/api/ads"))).status === 200 && JSON.parse(await (await run(null, request("GET", "/api/ads"))).text()).items.length === 1);
check("bad json 400", (await run(null, request("PUT", "/api/ads", "not json", auth))).status === 400);
check("missing items 400", (await run(null, request("PUT", "/api/news", json({ nope: 1 }), auth))).status === 400);

/* Releases push + serve */
const sha = "a".repeat(64);

response = await run(null, request("GET", "/api/version"));

check("empty version 200 releases[]", response.status === 200 && (await response.json()).releases.length === 0);
check("version cache header", response.headers.get("Cache-Control") === "public, max-age=300");

const goodRelease =
{
	version: "0.0.12",
	channel: "stable",
	date: "2026-08-30",
	url: "https://example.com/BBS_FSR.jar",
	sha256: sha,
	notes: "# 更新\n- 修复",
	mandatory: false,
	revoked: false,
	revokedReason: ""
};

response = await run(null, request("PUT", "/api/releases", json({ releases: [goodRelease] }), auth));

check("releases put 200", response.status === 200);
check("releases unauth 401", (await run(null, request("PUT", "/api/releases", json({ releases: [] }), { "Content-Type": "application/json" }))).status === 401);
check("releases bad json 400", (await run(null, request("PUT", "/api/releases", "nope", auth))).status === 400);
check("releases missing array 400", (await run(null, request("PUT", "/api/releases", json({ nope: 1 }), auth))).status === 400);
check("releases bad version 400", (await run(null, request("PUT", "/api/releases", json({ releases: [{ ...goodRelease, version: "v0.0.12" }] }), auth))).status === 400);
check("releases bad channel 400", (await run(null, request("PUT", "/api/releases", json({ releases: [{ ...goodRelease, channel: "beta" }] }), auth))).status === 400);
check("releases http url 400", (await run(null, request("PUT", "/api/releases", json({ releases: [{ ...goodRelease, url: "http://example.com/x.jar" }] }), auth))).status === 400);
check("releases bad sha 400", (await run(null, request("PUT", "/api/releases", json({ releases: [{ ...goodRelease, sha256: "zz" }] }), auth))).status === 400);
check("releases bad date 400", (await run(null, request("PUT", "/api/releases", json({ releases: [{ ...goodRelease, date: "tomorrow" }] }), auth))).status === 400);
check("releases duplicate 400", (await run(null, request("PUT", "/api/releases", json({ releases: [goodRelease, { ...goodRelease }] }), auth))).status === 400);
check("releases over cap 400", (await run(null, request("PUT", "/api/releases", json({ releases: Array.from({ length: 21 }, (_, i) => ({ ...goodRelease, version: "0.1." + i })) }), auth))).status === 400);

response = await run(null, request("GET", "/api/version"));

let served = await response.json();

check("releases served", response.status === 200 && served.releases.length === 1 && served.releases[0].version === "0.0.12");
check("releases normalized defaults", (await run(null, request("PUT", "/api/releases", json({ releases: [{ version: "0.0.13-beta.1", channel: "preview" }] }), auth))).status === 200);
served = await (await run(null, request("GET", "/api/version"))).json();
check("releases defaults filled", served.releases[0].mandatory === false && served.releases[0].revoked === false && served.releases[0].sha256 === "" && served.releases[0].date === "");

/* Image push + serve */
const bytes = new Uint8Array([137, 80, 78, 71, 1, 2, 3]);

response = await run(null, request("PUT", "/img/abc123.png", bytes, { "Authorization": "Bearer test-token", "Content-Type": "image/png" }));

check("image put 200", response.status === 200);

response = await run(null, request("GET", "/img/abc123.png"));

check("image served", response.status === 200 && response.headers.get("Content-Type") === "image/png" && new Uint8Array(await response.arrayBuffer()).length === bytes.length);
check("image cache header", response.headers.get("Cache-Control") === "public, max-age=86400");
check("traversal image key 400", (await run(null, request("PUT", "/img/%2e%2e%2fevil.png", bytes, { "Authorization": "Bearer test-token", "Content-Type": "image/png" }))).status === 400);
check("normalized traversal 404", (await run(null, request("PUT", "/img/../evil.png", bytes, { "Authorization": "Bearer test-token", "Content-Type": "image/png" }))).status === 404);
check("non-image 415", (await run(null, request("PUT", "/img/abc123.png", bytes, { "Authorization": "Bearer test-token", "Content-Type": "text/html" }))).status === 415);
check("image delete", (await run(null, request("DELETE", "/img/abc123.png", undefined, { "Authorization": "Bearer test-token" }))).status === 200);
check("deleted image 404", (await run(null, request("GET", "/img/abc123.png"))).status === 404);

console.log("\n" + (failed === 0 ? "ALL PASS" : failed + " FAILURES") + " (" + passed + " checks)");

process.exit(failed === 0 ? 0 : 1);
