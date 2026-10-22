"""Small shared browser-DOM shim used by gate-progress Node tests."""

NODE_DOM_HARNESS = r"""
const fixtureTimers=new Map();let fixtureTimerId=0;
const fixtureLocalStorage=new Map();global.localStorage={getItem(k){if(global.failLocalStorage)throw Error('storage unavailable');return fixtureLocalStorage.get(k)??null},setItem(k,v){if(global.failLocalStorage)throw Error('storage unavailable');fixtureLocalStorage.set(k,String(v))}};
global.setTimeout=(fn,delay=0)=>{const id=++fixtureTimerId;fixtureTimers.set(id,{fn,delay});return id};
global.clearTimeout=id=>fixtureTimers.delete(id);
global.location={protocol:'http:',origin:'http://fixture.test',href:'http://fixture.test/'};
global.window={listeners:{},addEventListener(k,fn){(this.listeners[k]||=[]).push(fn)}};
class FixtureElement {
 constructor(tag){this.tag=tag;this.children=[];this.hidden=false;this.className='';this._text='';this.attributes={};this.dataset={};this.value='';this.name='';this.open=false;this.onclick=null;this.listeners={};this.disabled=false;}
 append(...items){this.children.push(...items)}
 replaceChildren(...items){this.children=[...items]}
 set textContent(v){this._text=String(v);this.children=[]}
 get textContent(){return this._text+this.children.map(x=>x.textContent||'').join('')}
 get childNodes(){return this.children}
 setAttribute(k,v){this.attributes[k]=v}
 addEventListener(k,fn){(this.listeners[k]||=[]).push(fn)}
 querySelector(q){const name=q.match(/name=(\w+)/)?.[1],cls=q.match(/^\.([\w-]+)$/)?.[1];return this.children.find(x=>x.name===name||(q===x.tag)||(cls&&x.className===cls))||this.children.map(x=>x.querySelector?.(q)).find(Boolean)||null}
 querySelectorAll(q){const cls=q.match(/^\.([\w-]+)$/)?.[1],own=this.children.filter(x=>(q==='.workflow-card'&&x.className==='workflow-card')||(q==='.workflow-card button'&&x.tag==='button')||(cls&&x.className===cls));return own.concat(this.children.flatMap(x=>x.querySelectorAll?.(q)||[]))}
}
const elements=new Map(),metaRefresh={remove(){this.removed=true}};
const document={createElement:t=>new FixtureElement(t),getElementById:id=>{if(!elements.has(id))elements.set(id,new FixtureElement('div'));return elements.get(id)},querySelector:q=>q.includes('meta')?metaRefresh:(elements.get('columns')||(()=>{const x=new FixtureElement('div');elements.set('columns',x);return x})()),querySelectorAll:q=>elements.get('columns')?.querySelectorAll(q)||[]};
Date.now=()=>__NOW__*1000;
global.AbortController=global.AbortController||class{constructor(){this.signal={}}abort(){this.signal.aborted=true}};
"""


def run_node(node, script, *, timeout=15):
    """Execute browser-fixture scripts on stdin to avoid Windows argv limits."""
    import subprocess
    return subprocess.run([node, '--input-type=commonjs'], input=script, capture_output=True,
                          text=True, encoding='utf-8', timeout=timeout)
