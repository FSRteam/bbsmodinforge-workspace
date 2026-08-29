/**
 * BBS film home publisher WebUI — zero-dependency local server.
 *
 * Start:  node server.mjs [--port 8642] [--open]
 *
 * Serves the editor page from public/ and proxies pushes to the Cloudflare
 * Worker with the admin token from config.local.json. The token never leaves
 * this machine: the browser talks only to this server. Binds 127.0.0.1 only.
 */

import { createServer } from "node:http";
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join, normalize, resolve, sep } from "node:path";
import { spawn } from "node:child_process";

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, "public");
const dataDir = join(here, "data");
const configPath = join(here, "config.local.json");

if (!existsSync(dataDir))
{
	mkdirSync(dataDir, { recursive: true });
}

function portArg()
{
	const index = process.argv.indexOf("--port");

	return index >= 0 ? Number(process.argv[index + 1]) : 8642;
}

function readConfig()
{
	if (existsSync(configPath))
	{
		try
		{
			return JSON.parse(readFileSync(configPath, "utf8"));
		}
		catch (e)
		{
			console.warn("[webui] config.local.json unreadable:", e.message);
		}
	}

	return {};
}

function saveConfig(patch)
{
	const config = { ...readConfig(), ...patch, updatedAt: new Date().toISOString() };

	writeFileSync(configPath, JSON.stringify(config, null, 2) + "\n", "utf8");

	return config;
}

const MIME =
{
	".html": "text/html; charset=utf-8",
	".js": "text/javascript; charset=utf-8",
	".css": "text/css; charset=utf-8",
	".json": "application/json; charset=utf-8",
	".svg": "image/svg+xml",
	".png": "image/png",
	".ico": "image/x-icon"
};

function sendJson(response, status, payload)
{
	const body = JSON.stringify(payload);

	response.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
	response.end(body);
}

function readBody(request, limit)
{
	return new Promise((resolveBody, reject) =>
	{
		const chunks = [];
		let total = 0;

		request.on("data", (chunk) =>
		{
			total += chunk.length;

			if (total > limit)
			{
				reject(new Error("payload too large"));
				request.destroy();

				return;
			}

			chunks.push(chunk);
		});
		request.on("end", () => resolveBody(Buffer.concat(chunks)));
		request.on("error", reject);
	});
}

async function workerFetch(config, path, options)
{
	const base = String(config.workerUrl || "").replace(/\/+$/, "");

	if (!base)
	{
		return { status: 0, body: JSON.stringify({ error: "workerUrl not configured" }) };
	}

	const response = await fetch(base + path, options);
	const body = await response.text();

	return { status: response.status, body };
}

function draftPath(type)
{
	return join(dataDir, "draft-" + type + ".json");
}

async function handleApi(request, response, url)
{
	const path = url.pathname;
	const config = readConfig();

	if (path === "/api/config" && request.method === "GET")
	{
		return sendJson(response, 200, {
			workerUrl: config.workerUrl || "",
			adminToken: config.adminToken || "",
			configured: Boolean(config.workerUrl && config.adminToken)
		});
	}

	if (path === "/api/config" && request.method === "POST")
	{
		const body = JSON.parse((await readBody(request, 64 * 1024)).toString("utf8") || "{}");
		const next = saveConfig({
			workerUrl: String(body.workerUrl || "").replace(/\/+$/, ""),
			adminToken: String(body.adminToken || "")
		});

		return sendJson(response, 200, { ok: true, workerUrl: next.workerUrl });
	}

	if (path === "/api/health" && request.method === "GET")
	{
		try
		{
			const result = await workerFetch(config, "/health");

			return sendJson(response, 200, { ok: result.status === 200, status: result.status, body: result.body });
		}
		catch (e)
		{
			return sendJson(response, 200, { ok: false, status: 0, body: String(e.message) });
		}
	}

	if (path === "/api/current" && request.method === "GET")
	{
		const type = url.searchParams.get("type") === "news" ? "news" : "ads";

		try
		{
			const result = await workerFetch(config, "/api/" + type);

			return sendJson(response, 200, { status: result.status, payload: result.status === 200 ? JSON.parse(result.body) : null });
		}
		catch (e)
		{
			return sendJson(response, 200, { status: 0, payload: null, error: String(e.message) });
		}
	}

	if (path === "/api/upload" && request.method === "POST")
	{
		const bytes = await readBody(request, 10 * 1024 * 1024);
		const ext = (url.searchParams.get("ext") || "png").toLowerCase().replace(/[^a-z0-9]/g, "") || "png";
		const contentType = request.headers["content-type"] || "image/png";
		const key = createHash("sha256").update(bytes).digest("hex").slice(0, 32) + "." + ext;

		try
		{
			const result = await workerFetch(config, "/img/" + key, {
				method: "PUT",
				headers: { "Authorization": "Bearer " + (config.adminToken || ""), "Content-Type": contentType },
				body: new Uint8Array(bytes)
			});

			if (result.status !== 200)
			{
				return sendJson(response, 502, { error: "worker upload failed: " + result.status, body: result.body });
			}

			return sendJson(response, 200, { key, url: config.workerUrl.replace(/\/+$/, "") + "/img/" + key });
		}
		catch (e)
		{
			return sendJson(response, 502, { error: String(e.message) });
		}
	}

	if (path === "/api/push" && request.method === "POST")
	{
		const type = url.searchParams.get("type") === "news" ? "news" : "ads";
		const raw = (await readBody(request, 1024 * 1024)).toString("utf8");

		try
		{
			const parsed = JSON.parse(raw);

			if (!parsed || !Array.isArray(parsed.items))
			{
				return sendJson(response, 400, { error: 'expected {"items":[...]}' });
			}
		}
		catch (e)
		{
			return sendJson(response, 400, { error: "malformed json: " + e.message });
		}

		try
		{
			const result = await workerFetch(config, "/api/" + type, {
				method: "PUT",
				headers: { "Authorization": "Bearer " + (config.adminToken || ""), "Content-Type": "application/json" },
				body: raw
			});

			if (result.status === 401)
			{
				return sendJson(response, 401, { error: "admin token rejected by worker (401)" });
			}

			return sendJson(response, result.status === 200 ? 200 : 502, { ok: result.status === 200, status: result.status, body: result.body });
		}
		catch (e)
		{
			return sendJson(response, 502, { error: String(e.message) });
		}
	}

	if (path === "/api/releases" && request.method === "GET")
	{
		try
		{
			const result = await workerFetch(config, "/api/version");

			return sendJson(response, 200, { status: result.status, payload: result.status === 200 ? JSON.parse(result.body) : null });
		}
		catch (e)
		{
			return sendJson(response, 200, { status: 0, payload: null, error: String(e.message) });
		}
	}

	if (path === "/api/releases" && request.method === "PUT")
	{
		const raw = (await readBody(request, 1024 * 1024)).toString("utf8");

		try
		{
			const parsed = JSON.parse(raw);

			if (!parsed || !Array.isArray(parsed.releases))
			{
				return sendJson(response, 400, { error: 'expected {"releases":[...]}' });
			}
		}
		catch (e)
		{
			return sendJson(response, 400, { error: "malformed json: " + e.message });
		}

		try
		{
			const result = await workerFetch(config, "/api/releases", {
				method: "PUT",
				headers: { "Authorization": "Bearer " + (config.adminToken || ""), "Content-Type": "application/json" },
				body: raw
			});

			if (result.status === 401)
			{
				return sendJson(response, 401, { error: "admin token rejected by worker (401)" });
			}

			return sendJson(response, result.status === 200 ? 200 : 502, { ok: result.status === 200, status: result.status, body: result.body });
		}
		catch (e)
		{
			return sendJson(response, 502, { error: String(e.message) });
		}
	}

	if (path === "/api/drafts" && request.method === "GET")
	{
		const type = url.searchParams.get("type") === "news" ? "news" : "ads";
		const file = draftPath(type);

		return sendJson(response, 200, existsSync(file) ? JSON.parse(readFileSync(file, "utf8")) : null);
	}

	if (path === "/api/drafts" && request.method === "POST")
	{
		const type = url.searchParams.get("type") === "news" ? "news" : "ads";
		const raw = (await readBody(request, 1024 * 1024)).toString("utf8");

		JSON.parse(raw);
		writeFileSync(draftPath(type), raw, "utf8");

		return sendJson(response, 200, { ok: true });
	}

	return sendJson(response, 404, { error: "not found" });
}

function serveStatic(response, url)
{
	let rel = normalize(decodeURIComponent(url.pathname)).replace(/^([/\\])+/, "");

	if (rel === "" || rel === ".")
	{
		rel = "index.html";
	}

	const file = resolve(publicDir, rel);

	if (!file.startsWith(publicDir + sep))
	{
		response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
		response.end("not found");

		return;
	}

	if (!existsSync(file))
	{
		response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
		response.end("not found");

		return;
	}

	const type = MIME[file.slice(file.lastIndexOf("."))] || "application/octet-stream";

	response.writeHead(200, { "Content-Type": type, "Cache-Control": "no-store" });
	response.end(readFileSync(file));
}

const server = createServer(async (request, response) =>
{
	const url = new URL(request.url, "http://127.0.0.1");

	try
	{
		if (url.pathname.startsWith("/api/"))
		{
			await handleApi(request, response, url);
		}
		else
		{
			serveStatic(response, url);
		}
	}
	catch (e)
	{
		console.error("[webui]", request.method, url.pathname, "->", e.message);

		if (!response.headersSent)
		{
			sendJson(response, 500, { error: e.message });
		}
	}
});

server.listen(portArg(), "127.0.0.1", () =>
{
	const address = "http://127.0.0.1:" + portArg();

	console.log("[webui] film home publisher running at " + address);
	console.log("[webui] push target: " + (readConfig().workerUrl || "(not configured — open 设置 tab)"));

	if (process.argv.includes("--open"))
	{
		spawn("cmd", ["/c", "start", "", address], { detached: true, stdio: "ignore" }).unref();
	}
});
