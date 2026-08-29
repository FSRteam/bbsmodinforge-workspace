/**
 * BBS film home content worker.
 *
 * Serves the film home editorial content (ads + news JSON, ad/news images)
 * from a Cloudflare KV namespace, and accepts authenticated pushes from the
 * local publisher WebUI.
 *
 * Bindings:
 * - KV namespace `CONTENT` (content:ads, content:news, img:<sha256>.<ext>)
 * - Secret `ADMIN_TOKEN` (Bearer auth for PUT/DELETE)
 *
 * Public surface (GET/HEAD, CORS *):
 * - /api/ads  -> {"items":[...]}   Cache-Control: max-age=60
 * - /api/news -> {"items":[...]}   Cache-Control: max-age=60
 * - /img/<key> -> image bytes      Cache-Control: max-age=86400
 *
 * Auth surface (Authorization: Bearer <ADMIN_TOKEN>):
 * - PUT /api/ads | /api/news   body: JSON object with an "items" array (<1 MB)
 * - PUT /img/<key>             body: image bytes (<10 MB), Content-Type image/*
 * - DELETE /img/<key>
 */

const MAX_JSON_BYTES = 1024 * 1024;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const IMG_KEY_RE = /^[a-z0-9][a-z0-9_-]{0,128}\.(png|jpg|jpeg|webp|gif)$/;

const CORS = {
	"Access-Control-Allow-Origin": "*",
	"Access-Control-Allow-Methods": "GET, HEAD, PUT, DELETE, OPTIONS",
	"Access-Control-Allow-Headers": "Authorization, Content-Type",
	"Access-Control-Max-Age": "86400"
};

function json(data, status, cache)
{
	const headers = { "Content-Type": "application/json; charset=utf-8", ...CORS };

	if (cache)
	{
		headers["Cache-Control"] = cache;
	}

	return new Response(JSON.stringify(data), { status, headers });
}

function emptyResponse(status, message)
{
	return json({ error: message || String(status) }, status);
}

function authorized(request, env)
{
	const token = env.ADMIN_TOKEN;

	if (!token)
	{
		return false;
	}

	const header = request.headers.get("Authorization") || "";

	return header === "Bearer " + token;
}

async function readBody(request, maxBytes)
{
	const length = Number(request.headers.get("Content-Length") || "0");

	if (length > maxBytes)
	{
		return null;
	}

	const buffer = await request.arrayBuffer();

	if (buffer.byteLength > maxBytes)
	{
		return null;
	}

	return buffer;
}

export default
{
	async fetch(request, env)
	{
		const url = new URL(request.url);
		const path = url.pathname.replace(/\/+$/, "") || "/";
		const method = request.method;

		if (method === "OPTIONS")
		{
			return new Response(null, { status: 204, headers: CORS });
		}

		if (method === "GET" || method === "HEAD")
		{
			if (path === "/" || path === "/health")
			{
				return json({ service: "bbs-film-home", ok: true }, 200);
			}

			if (path === "/api/ads" || path === "/api/news")
			{
				const key = path === "/api/ads" ? "content:ads" : "content:news";
				const value = await env.CONTENT.get(key);

				if (value == null)
				{
					return json({ items: [] }, 200, "public, max-age=60");
				}

				return new Response(value, {
					status: 200,
					headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=60", ...CORS }
				});
			}

			if (path.startsWith("/img/"))
			{
				const key = decodeURIComponent(path.slice("/img/".length));

				if (!IMG_KEY_RE.test(key))
				{
					return emptyResponse(404, "not found");
				}

				const value = await env.CONTENT.getWithMetadata("img:" + key, { type: "arrayBuffer" });

				if (!value.value)
				{
					return emptyResponse(404, "not found");
				}

				const type = (value.metadata && value.metadata.contentType) || "application/octet-stream";

				return new Response(value.value, {
					status: 200,
					headers: { "Content-Type": type, "Cache-Control": "public, max-age=86400", ...CORS }
				});
			}

			return emptyResponse(404, "not found");
		}

		if (method === "PUT" || method === "POST" || method === "DELETE")
		{
			if (!authorized(request, env))
			{
				return emptyResponse(401, "unauthorized");
			}

			if (method === "DELETE" && path.startsWith("/img/"))
			{
				const key = decodeURIComponent(path.slice("/img/".length));

				if (!IMG_KEY_RE.test(key))
				{
					return emptyResponse(400, "bad image key");
				}

				await env.CONTENT.delete("img:" + key);

				return json({ ok: true }, 200);
			}

			if ((method === "PUT" || method === "POST") && (path === "/api/ads" || path === "/api/news"))
			{
				const body = await readBody(request, MAX_JSON_BYTES);

				if (body == null)
				{
					return emptyResponse(413, "payload too large");
				}

				let parsed;

				try
				{
					parsed = JSON.parse(new TextDecoder().decode(body));
				}
				catch (e)
				{
					return emptyResponse(400, "malformed json");
				}

				if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.items))
				{
					return emptyResponse(400, 'expected {"items": [...]}');
				}

				const key = path === "/api/ads" ? "content:ads" : "content:news";

				await env.CONTENT.put(key, new TextDecoder().decode(body));

				return json({ ok: true, items: parsed.items.length }, 200);
			}

			if ((method === "PUT" || method === "POST") && path.startsWith("/img/"))
			{
				const key = decodeURIComponent(path.slice("/img/".length));

				if (!IMG_KEY_RE.test(key))
				{
					return emptyResponse(400, "bad image key");
				}

				const type = (request.headers.get("Content-Type") || "").split(";")[0].trim();

				if (!type.startsWith("image/"))
				{
					return emptyResponse(415, "image content type required");
				}

				const body = await readBody(request, MAX_IMAGE_BYTES);

				if (body == null)
				{
					return emptyResponse(413, "payload too large");
				}

				await env.CONTENT.put("img:" + key, body, { metadata: { contentType: type } });

				return json({ ok: true, bytes: body.byteLength }, 200);
			}

			return emptyResponse(404, "not found");
		}

		return emptyResponse(405, "method not allowed");
	}
};
