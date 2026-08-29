/**
 * Zero-dependency deploy script for the bbs-film-home Cloudflare Worker.
 *
 * Usage (from the worker/ directory):
 *   node deploy.mjs --token <CF_API_TOKEN> [--seed-ads <file>] [--seed-news <file>]
 *                   [--hostname <custom domain> --zone-id <zone id>]
 *
 * The token is also read from the CF_API_TOKEN environment variable or from
 * webui/config.local.json (cfApiToken). Credentials produced by the deploy
 * (worker URL, KV namespace id, admin token, token itself) are merged into
 * webui/config.local.json — that file is gitignored. The workers.dev route
 * is always disabled; content is served from the attached custom domain.
 */

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import crypto from "node:crypto";

const here = dirname(fileURLToPath(import.meta.url));
const webuiDir = join(here, "..", "webui");
const configPath = join(webuiDir, "config.local.json");
const SCRIPT_NAME = "bbs-film-home";
const KV_TITLE = "bbs_film_home";

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
			console.warn("[deploy] config.local.json unreadable, starting fresh:", e.message);
		}
	}

	return {};
}

function writeConfig(config)
{
	writeFileSync(configPath, JSON.stringify(config, null, 2) + "\n", "utf8");
	console.log("[deploy] credentials written to", configPath);
}

function arg(name)
{
	const index = process.argv.indexOf(name);

	return index >= 0 ? process.argv[index + 1] : undefined;
}

async function api(token, method, path, body, headers)
{
	const response = await fetch("https://api.cloudflare.com/client/v4" + path, {
		method,
		headers: { "Authorization": "Bearer " + token, ...(body && !(body instanceof FormData) ? { "Content-Type": "application/json" } : {}), ...(headers || {}) },
		body: body == null ? undefined : (body instanceof FormData || typeof body === "string" ? body : JSON.stringify(body))
	});

	let payload;

	try
	{
		payload = await response.json();
	}
	catch (e)
	{
		throw new Error(method + " " + path + " -> HTTP " + response.status + " (non-json response)");
	}

	if (!response.ok || payload.success === false)
	{
		const detail = payload.errors ? payload.errors.map((e) => e.code + " " + e.message).join("; ") : response.status;

		throw new Error(method + " " + path + " failed: " + detail);
	}

	return payload.result;
}

async function main()
{
	const config = readConfig();
	const token = process.env.CF_API_TOKEN || arg("--token") || config.cfApiToken;

	if (!token)
	{
		console.error("[deploy] no API token: pass --token <CF_API_TOKEN>, set CF_API_TOKEN, or put cfApiToken into webui/config.local.json");
		process.exit(1);
	}

	/* 1. Account */
	const accounts = await api(token, "GET", "/accounts");

	if (!accounts || accounts.length === 0)
	{
		throw new Error("token cannot see any accounts");
	}

	const account = accounts[0];

	console.log("[deploy] account:", account.id, account.name);

	/* 2. KV namespace (find or create) */
	const namespaces = await api(token, "GET", "/accounts/" + account.id + "/storage/kv/namespaces");
	let namespace = namespaces.find((ns) => ns.title === KV_TITLE);

	if (!namespace)
	{
		namespace = await api(token, "POST", "/accounts/" + account.id + "/storage/kv/namespaces", { title: KV_TITLE });
		console.log("[deploy] created KV namespace:", namespace.id);
	}
	else
	{
		console.log("[deploy] reusing KV namespace:", namespace.id);
	}

	/* 3. Admin token (keep across redeploys so clients/WebUI stay valid) */
	const adminToken = config.adminToken || crypto.randomBytes(24).toString("base64url");

	/* 4. Upload the worker script */
	const source = readFileSync(join(here, "worker.js"), "utf8");
	const metadata = {
		main_module: "worker.js",
		compatibility_date: "2024-09-23",
		bindings: [
			{ type: "kv_namespace", name: "CONTENT", namespace_id: namespace.id },
			{ type: "secret_text", name: "ADMIN_TOKEN", text: adminToken }
		]
	};

	const form = new FormData();

	form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
	form.append("worker.js", new Blob([source], { type: "application/javascript+module" }), "worker.js");

	await api(token, "PUT", "/accounts/" + account.id + "/workers/scripts/" + SCRIPT_NAME, form);
	console.log("[deploy] worker script uploaded:", SCRIPT_NAME);

	/* 5. Custom domain: attach via API (auto-creates DNS + cert) and keep
	 * the workers.dev route disabled (it is unreachable from CN networks).
	 * Pass --hostname <host> --zone-id <id> to (re)attach; otherwise the
	 * script runs on an existing attachment only. */
	const hostname = arg("--hostname");
	const zoneId = arg("--zone-id");

	if (hostname && zoneId)
	{
		await api(token, "PUT", "/accounts/" + account.id + "/workers/domains", {
			zone_id: zoneId,
			hostname,
			service: SCRIPT_NAME,
			environment: "production"
		});
		console.log("[deploy] custom domain attached:", hostname);
	}

	const attachments = await api(token, "GET", "/accounts/" + account.id + "/workers/domains");
	const attachment = (attachments || []).find((d) => d.service === SCRIPT_NAME);

	if (!attachment)
	{
		throw new Error("no custom domain attached to " + SCRIPT_NAME + "; pass --hostname <host> --zone-id <zone id> on first deploy");
	}

	await api(token, "POST", "/accounts/" + account.id + "/workers/scripts/" + SCRIPT_NAME + "/subdomain", {
		enabled: false,
		previews_enabled: false
	});

	const workerUrl = "https://" + attachment.hostname;
	console.log("[deploy] workers.dev route disabled; serving at:", workerUrl);

	/* 6. Seed content when the KV keys don't exist yet. The values endpoint
	 * answers with the raw stored value (no {success,result} envelope), so
	 * probe it directly: 200 = exists (never touch), 404 = missing (seed),
	 * anything else = ambiguous — abort the seed instead of risking a
	 * destructive overwrite of live content. */
	for (const [key, fileArg, empty] of [["content:ads", "--seed-ads", { items: [] }], ["content:news", "--seed-news", { items: [] }]])
	{
		const probe = await fetch("https://api.cloudflare.com/client/v4/accounts/" + account.id + "/storage/kv/namespaces/" + namespace.id + "/values/" + key, { headers: { "Authorization": "Bearer " + token } });

		if (probe.status === 200)
		{
			console.log("[deploy] KV", key, "already present, left untouched");

			continue;
		}

		if (probe.status !== 404)
		{
			throw new Error("[deploy] cannot verify KV " + key + " (HTTP " + probe.status + ") — refusing to seed over unknown state");
		}

		const seedFile = arg(fileArg);
		const value = seedFile ? readFileSync(resolve(seedFile), "utf8") : JSON.stringify(empty);

		await api(token, "PUT", "/accounts/" + account.id + "/storage/kv/namespaces/" + namespace.id + "/values/" + key, value);
		console.log("[deploy] seeded KV", key, seedFile ? "from " + seedFile : "empty");
	}

	/* 7. Persist credentials for the WebUI */
	writeConfig({
		...config,
		cfApiToken: token,
		workerUrl,
		accountId: account.id,
		kvNamespaceId: namespace.id,
		adminToken,
		updatedAt: new Date().toISOString()
	});

	console.log("\n[deploy] done. Worker URL: " + workerUrl);
	console.log("[deploy] push endpoints: PUT /api/ads, /api/news, /img/<key> with 'Authorization: Bearer <adminToken>'");
}

main().catch((error) =>
{
	console.error("[deploy] FAILED:", error.message);
	process.exit(1);
});
