/* SignalX page-world Quotex observer.
 * It does not connect to any external feed. It observes WebSocket messages
 * already opened by the current Quotex page and extracts only OHLC-shaped data.
 */
(function(){
  if(window.__SignalXPageBridgeLoaded) return;
  window.__SignalXPageBridgeLoaded=true;

  const seen=new WeakSet();

  function num(v){const n=Number(v);return Number.isFinite(n)?n:null;}
  function candleFromObject(o){
    if(!o || typeof o!=="object" || Array.isArray(o)) return null;
    const open=num(o.open ?? o.o), high=num(o.high ?? o.h), low=num(o.low ?? o.l), close=num(o.close ?? o.c);
    if(open===null||high===null||low===null||close===null) return null;
    if(high<Math.max(open,close) || low>Math.min(open,close)) return null;
    const time=num(o.time ?? o.timestamp ?? o.ts ?? o.t ?? o.from);
    return {time:time===null?Date.now():time,open,high,low,close};
  }

  function collect(value,out,depth){
    if(depth>7 || out.length>=200 || value==null) return;
    if(Array.isArray(value)){
      const c=value.length>=4 && value.length<=8 && value.every(v=>Number.isFinite(Number(v))) ?
        {time:Number(value[0]),open:Number(value[1]),high:Number(value[2]),low:Number(value[3]),close:Number(value[4]??value[3])}:null;
      if(c && c.high>=Math.max(c.open,c.close) && c.low<=Math.min(c.open,c.close)){out.push(c);return;}
      for(const x of value) collect(x,out,depth+1);
      return;
    }
    if(typeof value!=="object") return;
    const c=candleFromObject(value);
    if(c){out.push(c);return;}
    for(const k of Object.keys(value).slice(0,80)) collect(value[k],out,depth+1);
  }

  function market(){
    const selectors=[
      '[data-testid*="asset"]','[class*="asset"]','[class*="symbol"]',
      '[class*="instrument"]','[class*="pair"]'
    ];
    for(const s of selectors){
      const el=document.querySelector(s);
      const t=el?.textContent?.trim();
      if(t && /^[A-Z0-9._/-]{3,20}(?:\s*\/\s*[A-Z0-9._-]{3,20})?$/.test(t)) return t;
    }
    return "বর্তমান মার্কেট";
  }

  function timeframe(){
    const els=[...document.querySelectorAll('button,[role="button"],[class*="time"],[class*="period"]')];
    for(const el of els){
      const t=el.textContent?.trim();
      if(t && /^(5s|10s|15s|30s|1m|2m|3m|5m|10m|15m|30m|1h|4h|1d)$/i.test(t)) return t;
    }
    return "—";
  }

  function publish(raw){
    let value=raw;
    if(typeof raw==="string"){
      if(raw.startsWith("42")) raw=raw.slice(2);
      try{value=JSON.parse(raw);}catch{return;}
    }
    const found=[];
    collect(value,found,0);
    if(found.length<5) return;
    const unique=new Map();
    for(const c of found){
      const key=c.time+"|"+c.open+"|"+c.high+"|"+c.low+"|"+c.close;
      unique.set(key,c);
    }
    const candles=[...unique.values()].sort((a,b)=>a.time-b.time).slice(-200);
    if(candles.length<5) return;
    window.postMessage({
      source:"SignalXQuotexPage",
      data:{market:market(),timeframe:timeframe(),candles}
    },"*");
  }

  const NativeWS=window.WebSocket;
  window.WebSocket=function(url,protocols){
    const ws=protocols===undefined?new NativeWS(url):new NativeWS(url,protocols);
    ws.addEventListener("message",e=>publish(e.data));
    return ws;
  };
  window.WebSocket.prototype=NativeWS.prototype;
  window.WebSocket.CONNECTING=NativeWS.CONNECTING;
  window.WebSocket.OPEN=NativeWS.OPEN;
  window.WebSocket.CLOSING=NativeWS.CLOSING;
  window.WebSocket.CLOSED=NativeWS.CLOSED;

  const OriginalFetch=window.fetch;
  window.fetch=function(...args){
    return OriginalFetch.apply(this,args).then(r=>{
      try{r.clone().text().then(publish).catch(()=>{});}catch(_){}
      return r;
    });
  };
})();