/* SignalX isolated-world receiver.
 * Reads only validated OHLC candles forwarded by the current Quotex page.
 * No external market-data service is used.
 */
(function(){
  const state={lastKey:""};

  function normalize(data){
    if(!data || !Array.isArray(data.candles) || !data.candles.length) return null;
    const candles=data.candles.map(x=>({
      time:Number(x.time),
      open:Number(x.open),
      high:Number(x.high),
      low:Number(x.low),
      close:Number(x.close)
    })).filter(x=>
      Number.isFinite(x.time) &&
      Number.isFinite(x.open) &&
      Number.isFinite(x.high) &&
      Number.isFinite(x.low) &&
      Number.isFinite(x.close) &&
      x.high>=Math.max(x.open,x.close) &&
      x.low<=Math.min(x.open,x.close)
    );
    if(candles.length<5) return null;
    return {
      market:typeof data.market==="string" ? data.market : "বর্তমান মার্কেট",
      timeframe:data.timeframe==null ? "—" : String(data.timeframe),
      candles:candles.slice(-200)
    };
  }

  window.addEventListener("message",(event)=>{
    if(event.source!==window || !event.data || event.data.source!=="SignalXQuotexPage") return;
    const d=normalize(event.data.data);
    if(!d) return;

    const last=d.candles.at(-1);
    const key=(d.market||"")+"|"+(d.timeframe||"")+"|"+d.candles.length+"|"+last.time+"|"+last.close;
    if(key===state.lastKey) return;
    state.lastKey=key;

    window.postMessage({
      source:"SignalXQuotex",
      type:"CHART_DATA",
      data:d
    },"*");
  });
})();