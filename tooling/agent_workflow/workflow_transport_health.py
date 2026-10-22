"""Explicit, read-only health observation for a configured local OpenCode API."""
from __future__ import annotations

import ipaddress
import json
import socket
import urllib.error
import urllib.parse
import urllib.request


class LocalTransportUnavailable(Exception):
    pass


class LocalApiRepairRequired(Exception):
    pass


def _server(server):
    parsed=urllib.parse.urlparse(server)
    try: loopback=ipaddress.ip_address(parsed.hostname).is_loopback
    except (ValueError,TypeError): loopback=False
    if not loopback or parsed.scheme not in ('http','https') or parsed.username or parsed.password or parsed.path not in ('','/') or parsed.query or parsed.fragment:
        raise ValueError('server must be an explicit loopback base URL')
    return server.rstrip('/')


def _request(server, path='/api/info', *, opener=None, request=None, timeout=2):
    """GET-only wrapper; recognizes transport/gateway/auth failures locally."""
    if path != '/api/info': raise ValueError('only GET /api/info is permitted')
    url=_server(server)+path
    try:
        if request is not None:
            status, data=request(server,'GET','/api/info',timeout=timeout)
        else:
            response=(opener or urllib.request.urlopen)(urllib.request.Request(url,method='GET'),timeout=timeout)
            status=getattr(response,'status',200)
            body=response.read()
            data=None
    except urllib.error.HTTPError as exc:
        status=exc.code; body=b''
    except (OSError, urllib.error.URLError, socket.timeout, TimeoutError) as exc:
        raise LocalTransportUnavailable(str(exc)) from exc
    except Exception as exc:
        # The injected OpenCode CLI adapter reports HTTP/transport failures as
        # exceptions instead of exposing a response object.
        raise LocalTransportUnavailable(type(exc).__name__) from exc
    if status in (401,403): raise LocalApiRepairRequired('local API authentication/configuration requires repair')
    if status in (502,503,504): raise LocalTransportUnavailable('local API gateway unavailable')
    if status < 200 or status >= 300: raise LocalApiRepairRequired('unexpected local API response')
    if request is None:
        try: data=json.loads(body)
        except (ValueError,TypeError) as exc: raise LocalTransportUnavailable('invalid local API info response') from exc
    if (not isinstance(data,dict) or not isinstance(data.get('version'),str) or
        type(data.get('pid')) is not int or data['pid']<=0 or
        not isinstance(data.get('urls'),list) or not all(isinstance(item,str) for item in data['urls']) or
        not isinstance(data.get('paths'),dict) or not isinstance(data['paths'].get('tmp'),str)):
        raise LocalTransportUnavailable('invalid local API info shape')
    normalized=[]
    for item in data['urls']:
        try: normalized.append(_server(item))
        except (ValueError,TypeError): continue
    if _server(server) not in normalized:
        raise LocalTransportUnavailable('local API info server identity mismatch')
    return data


class WorkflowTransportHealth:
    def __init__(self, store, server, *, opener=None, request=None):
        """request may be the existing authenticated gate_dispatch._request adapter."""
        self.store=store; self.server=_server(server); self.opener=opener; self.request=request
        self.key='LOCAL_API:'+self.server

    def observe_connection_failure(self, server, failure_id, now_ms):
        if _server(server)!=self.server: raise ValueError('server identity mismatch')
        return self.store.local_api_failure(key=self.key,failure_id=failure_id,now_ms=int(now_ms),details={'kind':'LOCAL_TRANSPORT_UNAVAILABLE'})

    def snapshot(self, server=None):
        if server is not None and _server(server)!=self.server: raise ValueError('server identity mismatch')
        row=self.store.local_api_snapshot(self.key)
        if row is None: return {'state':'UNKNOWN','action':'NONE','server':self.server,'probes':0,'failures':0}
        state=row['state']; row.update(server=self.server,action='EXPLICIT_RETRY' if state=='OPEN' else ('WAIT' if state=='BACKOFF' else 'NONE'),status='PAUSED_LOCAL_API' if state in ('OPEN','BACKOFF') else state)
        return row

    def probe(self, now_ms, probe_id):
        permit=self.store.local_api_permit(key=self.key,now_ms=int(now_ms),probe_id=probe_id)
        if permit is None: return {'state':'UNKNOWN','action':'WAIT','executed':False}
        try: info=_request(self.server,opener=self.opener,request=self.request)
        except LocalApiRepairRequired as exc:
            self.store.local_api_result(key=self.key,generation=permit['generation'],probe_id=probe_id,healthy=False,details={'kind':'AUTH_REPAIR_REQUIRED','message':str(exc)})
            return {'state':'OPEN','action':'REPAIR_LOCAL_API','executed':True}
        except LocalTransportUnavailable as exc:
            self.store.local_api_result(key=self.key,generation=permit['generation'],probe_id=probe_id,healthy=False,details={'kind':'LOCAL_TRANSPORT_UNAVAILABLE','message':str(exc)})
            return self.snapshot()
        self.store.local_api_result(key=self.key,generation=permit['generation'],probe_id=probe_id,healthy=True,details={'kind':'CONNECTED','version':info['version'],'pid':info['pid']})
        return {'state':'CONNECTED','scope':'LOCAL_API_ONLY','inference_health':'UNKNOWN','task_resumed':False,'info':info,'executed':True}
