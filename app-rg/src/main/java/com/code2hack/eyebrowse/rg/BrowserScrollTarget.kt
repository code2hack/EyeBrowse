package com.code2hack.eyebrowse.rg

/** Read current geometry only. The actual scroll remains a native WebView wheel event. */
internal const val BROWSER_SCROLL_TARGET = """
((u,v,pixels,width)=>{
    const root=document.scrollingElement;
    if(!root || document.visibilityState!=='visible') return [];
    const scale=width/innerWidth, delta=pixels/scale;
    const x=Math.max(0,Math.min(innerWidth-1,u*innerWidth));
    const y=(v>=0 && v<1 ? v : .5)*innerHeight;
    const hit=document.elementFromPoint(x,y);
    if(!hit || hit.tagName==='IFRAME') return [];
    function room(e) {
        return delta>0 ? e.scrollHeight-e.clientHeight-e.scrollTop : e.scrollTop;
    }
    function result(e,px,py) {
        const amount=Math.min(Math.abs(pixels),Math.floor(Math.max(0,room(e))*scale));
        if(amount<=0) return [];
        const r=e.getBoundingClientRect();
        const left=Math.max(0,r.left)+1, right=Math.min(innerWidth,r.right)-1;
        const top=Math.max(0,r.top)+1, bottom=Math.min(innerHeight,r.bottom)-1;
        // Native wheel hit testing must reach the selected ancestor, not its exhausted child.
        for(const [hx,hy] of [[px,py],[(left+right)/2,(top+bottom)/2],
            [left,top],[right,top],[left,bottom],[right,bottom]]) {
            if(hx<0 || hx>=innerWidth || hy<0 || hy>=innerHeight) continue;
            let hit=document.elementFromPoint(hx,hy);
            for(; hit && hit!==e; hit=hit.parentElement) {
                if(hit.tagName==='IFRAME' || (/^(auto|scroll|overlay)$/.test(getComputedStyle(hit).overflowY) &&
                    hit.scrollHeight>hit.clientHeight)) break;
            }
            if(hit===e) return [hx/innerWidth,hy/innerHeight,Math.sign(pixels)*amount];
        }
        return [];
    }
    function panel(e,px,py) {
        let nested=false,covered=false;
        for(; e && e!==root; e=e.parentElement) {
            const style=getComputedStyle(e);
            covered ||= style.position==='fixed' || e.matches('dialog[open],[aria-modal="true"]');
            if(/^(auto|scroll|overlay)$/.test(style.overflowY) && e.scrollHeight>e.clientHeight) {
                nested=true;
                if(room(e)>=1/scale) return result(e,px,py);
                if(style.overscrollBehaviorY==='contain' || style.overscrollBehaviorY==='none') return [];
            }
        }
        // Chain within a panel's ancestors, never from its endpoint into the page behind it.
        if(nested || covered) return [];
    }
    const pointed=panel(hit,x,y);
    if(pointed!==undefined) return pointed;
    const focus=document.activeElement;
    if(focus && focus!==document.body && focus!==root && focus.tagName!=='IFRAME') {
        const r=focus.getBoundingClientRect();
        const fx=(Math.max(0,r.left)+Math.min(innerWidth,r.right))/2;
        const fy=(Math.max(0,r.top)+Math.min(innerHeight,r.bottom))/2;
        if(r.width>0 && r.height>0 && fx>=0 && fx<innerWidth && fy>=0 && fy<innerHeight &&
            focus.contains(document.elementFromPoint(fx,fy))) {
            const focused=panel(focus,fx,fy);
            if(focused!==undefined) return focused;
        }
    }
    if(/^(hidden|clip)$/.test(getComputedStyle(root).overflowY)) return [];
    return result(root,x,y);
})
"""
