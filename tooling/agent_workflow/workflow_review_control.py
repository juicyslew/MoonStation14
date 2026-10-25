"""Canonical pure helpers for review continuation controls and synthetic inbox records."""
import hashlib

try:
    import workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_store


def continuation_body(task, assignment, command_id, purpose='PROVIDER_RETRY'):
    task_id=task['task_id']
    message_id='msg_'+hashlib.sha256(
        f"{task_id}\0{task['evaluation_generation']}\0{task['assignment_generation']}\0{command_id}".encode()
    ).hexdigest()[:40]
    if purpose=='REVIEW_COMPLETION':
        text=(f'The existing bound review assignment {task_id} has no accepted result yet. Continue from this session checkpoint; '
              'do not rerun checks or create sessions. Inspect assigned evidence if needed, then use the advertised reviewer SDK '
              'complete_review with the canonical review_result or report_error with a canonical task_error, and wait for its '
              'accepted receipt before stopping. Final chat prose is not a submitted review.')
    elif purpose=='PROVIDER_RETRY':
        text=(f'Continue existing bound review task {task_id} from this session checkpoint. Do not rerun checks or create sessions. '
              'Claim/complete the existing assignment or report its precise blocker.')
    else:
        raise ValueError('unsupported review continuation purpose')
    metadata={'kind':'REVIEW_CONTINUATION','task_id':task_id,'run_id':task['run_id'],
        'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
        'input_digest':task['input_digest'],'original_prompt_id':'msg_'+hashlib.sha256(
            (task_id+assignment['body_sha256']).encode()).hexdigest()[:40],
        'assignment_body_sha256':assignment['body_sha256'],'command_id':command_id}
    body={'id':message_id,'text':text,'delivery':'steer','resume':True,'metadata':metadata}
    return body


def continuation_bodies(receipts, task, assignment, server):
    """Validate retained retry receipts and return only canonical owned controls."""
    if not isinstance(receipts, list): raise ValueError('retry receipts unavailable')
    task_id=task['task_id']; sid=task.get('assigned_session_id')
    original='msg_'+hashlib.sha256((task_id+assignment['body_sha256']).encode()).hexdigest()[:40]
    proof={'server':server.rstrip('/'),'session_id':sid,'message_id':original,'body_sha256':assignment['body_sha256'],
        'task_id':task_id,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
        'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
    binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
    controls=[]
    for receipt in receipts:
        if not isinstance(receipt,dict) or receipt.get('task_id')!=task_id: raise ValueError('retry receipt task mismatch')
        if receipt.get('mode')!='CONTROL_PROMPT': continue
        if receipt.get('status') not in ('INTENT','RESUMED','STOPPING') or receipt.get('binding')!=binding or receipt.get('proof')!=proof:
            raise ValueError('retry control receipt binding invalid')
        if not isinstance(receipt.get('command_id'),str) or not receipt['command_id']: raise ValueError('retry command missing')
        snapshots=receipt.get('controls_snapshot')
        if not isinstance(snapshots,list) or not snapshots: raise ValueError('retry control snapshot missing')
        expected=continuation_body(task,assignment,receipt['command_id'],receipt.get('purpose','PROVIDER_RETRY'))
        for saved in snapshots:
            if (not isinstance(saved,dict) or saved.get('id')!=expected['id'] or saved.get('session_id')!=sid or
                saved.get('text')!=expected['text'] or saved.get('metadata')!=expected['metadata'] or saved.get('body')!=expected or
                saved.get('body_sha256')!=hashlib.sha256(workflow_store.canonical_json(expected).encode()).hexdigest()):
                raise ValueError('retry control proof invalid')
            controls.append(expected)
    if len({x['id'] for x in controls})!=len(controls): raise ValueError('duplicate retry control')
    return controls


def synthetic_snapshot(item, session_id, lower_bound_ms):
    """Normalize only the captured OpenCode inbox shape; reject ambiguous timestamps."""
    if not isinstance(item,dict) or item.get('type')!='synthetic' or item.get('delivery') not in ('steer','queue'):
        return None
    if item.get('sessionID',item.get('session_id',item.get('sessionId'))) != session_id:
        return None
    stamp=item.get('time')
    created=stamp.get('created') if isinstance(stamp,dict) else None
    if isinstance(created,bool) or not isinstance(created,int) or created < lower_bound_ms:
        return None
    payload=item.get('payload')
    text=payload.get('text') if isinstance(payload,dict) else item.get('text')
    if not isinstance(text,str) or not isinstance(item.get('id'),str) or not item['id']:
        return None
    return {'id':item['id'],'session_id':session_id,'created_ms':created,
            'text_sha256':hashlib.sha256(text.encode()).hexdigest()}


def validate_synthetic_snapshot(saved, session_id, lower_bound_ms):
    if (not isinstance(saved,dict) or not isinstance(saved.get('id'),str) or not saved['id'] or
        saved.get('session_id')!=session_id or isinstance(saved.get('created_ms'),bool) or
        not isinstance(saved.get('created_ms'),int) or saved['created_ms']<lower_bound_ms or
        not isinstance(saved.get('text_sha256'),str) or len(saved['text_sha256'])!=64 or
        any(c not in '0123456789abcdef' for c in saved['text_sha256'])):
        return None
    return {'id':saved['id'],'session_id':session_id,'created_ms':saved['created_ms'],
            'text_sha256':saved['text_sha256']}
