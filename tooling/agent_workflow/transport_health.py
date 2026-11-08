"""Local API circuit guard around the coordinator's existing authenticated transport."""
from __future__ import annotations

import socket
import time
import urllib.error
import uuid

try:
    import workflow_transport_health
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_transport_health


LocalTransportUnavailable = workflow_transport_health.LocalTransportUnavailable


class TransportHealth:
    def __init__(self, circuit, base_request, *, clock_ms=None):
        self.circuit=circuit
        self.base_request=base_request
        self.clock_ms=clock_ms or (lambda:int(time.time()*1000))

    def snapshot(self): return self.circuit.snapshot()

    def retry(self, command_id, *, caller='SYSTEM'):
        return self.circuit.store.local_api_retry(key=self.circuit.key,command_id=command_id,caller=caller)

    def request(self, server, method, path, body=None, timeout=30):
        state=self.circuit.snapshot(server)
        if state['state'] in ('OPEN','BACKOFF'):
            raise LocalTransportUnavailable('local API circuit '+state['state'])
        try:
            status,response=self.base_request(server,method,path,body=body,timeout=timeout)
        except (OSError,urllib.error.URLError,socket.timeout,TimeoutError) as exc:
            self._failure(method,path,'transport')
            raise LocalTransportUnavailable(str(exc)) from exc
        except Exception as exc:
            message=str(exc)
            if '401' in message or '403' in message:
                self._failure(method,path,'auth',repair=True)
                raise LocalTransportUnavailable('local API authentication requires repair') from exc
            if any(code in message for code in ('502','503','504')):
                self._failure(method,path,'gateway')
            else:
                self._failure(method,path,'transport')
            raise LocalTransportUnavailable('local API transport unavailable') from exc
        if status in (401,403):
            self._failure(method,path,'auth',repair=True)
            raise LocalTransportUnavailable('local API authentication requires repair')
        if status in (502,503,504):
            self._failure(method,path,'gateway')
            raise LocalTransportUnavailable('local API gateway unavailable')
        return status,response

    def _failure(self,method,path,kind,repair=False):
        # Context contains no request body, headers, token, or provider response.
        context={'method':str(method),'path':str(path),'kind':kind}
        ident='local_failure_'+uuid.uuid4().hex
        self.circuit.observe_connection_failure(self.circuit.server,ident,self.clock_ms())
        if repair:
            # Auth failures are never transport-retried; an explicit repair is required.
            self.circuit.store.local_api_open(key=self.circuit.key,details={'kind':'AUTH_REPAIR_REQUIRED','context':context})

    def tick(self):
        snap=self.circuit.snapshot()
        if snap['state']!='BACKOFF' or snap.get('next_probe_ms') is None or self.clock_ms()<snap['next_probe_ms']:
            return {'executed':False,'state':snap['state']}
        probe_id='local_probe_'+str(snap['generation'])+'_'+str(snap['probes']+1)
        return self.circuit.probe(self.clock_ms(),probe_id)
