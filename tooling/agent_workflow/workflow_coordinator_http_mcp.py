"""Fixed-catalog Streamable HTTP MCP profiles hosted by the coordinator ASGI app."""
from __future__ import annotations

import contextvars
import json

import anyio
import httpx
from mcp.server import Server
from mcp.server.streamable_http_manager import StreamableHTTPSessionManager
from mcp.server.transport_security import TransportSecuritySettings
from mcp.types import TextContent, Tool
from starlette.responses import JSONResponse

from workflow_coordinator_mcp import CoordinatorClient, MAX_HTTP_RESPONSE

PROFILE_PATHS = {"author": "/mcp/author", "author-static": "/mcp/author-static", "reviewer": "/mcp/reviewer"}
_principal = contextvars.ContextVar("workflow_mcp_principal", default=None)


class CoordinatorHTTPMCP:
    """One immutable SDK catalog/cache per profile, forwarding to the existing REST app in-process."""
    def __init__(self, coordinator, rest_app):
        self.coordinator, self.rest_app = coordinator, rest_app
        self.managers = {}
        self.servers = {}
        self._lifespans = []
        security = TransportSecuritySettings(enable_dns_rebinding_protection=True,
            allowed_hosts=["127.0.0.1", "localhost", "[::1]", "127.0.0.1:*", "localhost:*", "[::1]:*"],
            allowed_origins=["http://127.0.0.1", "http://localhost", "http://[::1]",
                "http://127.0.0.1:*", "http://localhost:*", "http://[::1]:*"])
        for profile, route in PROFILE_PATHS.items():
            server = Server("moonstation14-coordinator-" + profile)
            self.servers[profile] = server

            @server.list_tools()
            async def list_tools(profile=profile):
                principal = _principal.get()
                client = self._client(profile, principal)
                return [Tool(name=t["name"], description=t["description"], inputSchema=t["inputSchema"])
                        for t in client.tools()]

            @server.call_tool()
            async def call_tool(name, arguments, profile=profile):
                principal = _principal.get()
                if principal is None:
                    return [TextContent(type="text", text=json.dumps({"ok":False,"code":"UNAUTHORIZED","message":"Configured bearer token required."}))]
                args=arguments or {}; client=None; preparing=False
                try:
                    client = self._client(profile, principal)
                    preparing=True
                    name, args, url, data, headers = await anyio.to_thread.run_sync(client.prepare_call, name, args)
                    preparing=False
                    # A fresh async in-process ASGI request; no worker waits on a nested AnyIO request.
                    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=self.rest_app), base_url="http://coordinator", timeout=httpx.Timeout(300)) as http:
                        async with http.stream("POST" if data is not None else "GET", url.replace(client.endpoint, ""), content=data, headers=headers) as response:
                            chunks=[]; size=0
                            async for chunk in response.aiter_bytes():
                                size += len(chunk)
                                if size > MAX_HTTP_RESPONSE: raise ValueError("coordinator response exceeds limit")
                                chunks.append(chunk)
                            result = client.interpret_response(name, args, response.status_code, b"".join(chunks))
                except Exception as exc:
                    if client is None:
                        result={"ok":False,"code":"coordinator_client_unavailable","message":"Coordinator client could not be initialized for this configured profile.","next_action":"WAIT_FOR_RECOVERY"}
                    elif preparing and isinstance(exc,(ValueError,KeyError,OSError)):
                        result=CoordinatorClient.local_failure(exc,args)
                    else:
                        result = CoordinatorClient.transport_failure(name, args, exc)
                return [TextContent(type="text", text=CoordinatorClient.encode_response(name, args, result))]

            manager = StreamableHTTPSessionManager(server, json_response=True, stateless=True, security_settings=security)
            self.managers[profile] = manager

    def _client(self, profile, token):
        if not isinstance(token, str): raise ValueError("unauthenticated")
        if profile == "reviewer":
            binding = self.coordinator.review_bindings.get(token)
            if binding is None: raise ValueError("reviewer credential required")
            repo, mode = binding.worktree, "static"
            client_profile = "reviewer"
        elif profile == "author-static":
            binding = self.coordinator.bindings.get(token)
            if binding is None: raise ValueError("static author credential required")
            repo, mode, client_profile = binding.context.repo, "static", "author"
        else:
            binding = self.coordinator.author_worktrees.get(token)
            if binding is None: raise ValueError("worktree author credential required")
            repo, mode, client_profile = binding.repo, "worktree", "author"
        return CoordinatorClient("http://127.0.0.1", token, repo, profile=client_profile, author_mode=mode)

    async def request(self, profile, request):
        auth = request.headers.get("authorization", "")
        token = auth[7:] if auth.startswith("Bearer ") else None
        try: self._client(profile, token)
        except ValueError:
            return JSONResponse({"error":{"code":"UNAUTHORIZED","message":"Configured profile bearer token required."}}, status_code=403)
        marker = _principal.set(token)
        messages=[]
        async def send(message): messages.append(message)
        try: await self.managers[profile].handle_request(request.scope, request.receive, send)
        finally: _principal.reset(marker)
        from starlette.responses import Response
        start=next((m for m in messages if m["type"]=="http.response.start"),None)
        body=b"".join(m.get("body",b"") for m in messages if m["type"]=="http.response.body")
        if start is None: return Response(status_code=500)
        return Response(body,status_code=start["status"],headers={k.decode("latin1"):v.decode("latin1") for k,v in start.get("headers",[])})

    async def start(self):
        try:
            for manager in self.managers.values():
                cm = manager.run()
                await cm.__aenter__()
                self._lifespans.append(cm)
        except BaseException:
            await self.stop()
            raise

    async def stop(self):
        while self._lifespans:
            await self._lifespans.pop().__aexit__(None, None, None)
