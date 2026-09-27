/* SignalX isolated-world receiver.
 * No external market-data service is used.
 * page-bridge.js observes the current Quotex page's own WebSocket traffic and
 * posts only validated OHLC candle objects into this isolated world.
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
    })).filter(x=>Number.isFinite(x.open)&&Number.isFinite(x.high)&&Number.isFinite(x.low)&&Number.isFinite(x.close));
    if(candles.length<5) return null;
    return {
      market: typeof data.market==="string" ? data.market : "—",
      timeframe: data.timeframe==null ? "—" : String(data.timeframe),
      candles:candles.slice(-200)
    };
  }
  window.addEventListener("message",(event)=>{
    if(event.source!==window || !event.data || event.data.source!=="SignalXQuotexPage") return;
    const d=normalize(event.data.data);
    if(!d) return;
    const key=(d.market||"")+"|"+(d.timeframe||"")+"|"+d.candles.length+"|"+d.candles.at(-1).time+"|"+d.candles.at(-1).close;
    if(key===state.lastKey) return;
    state.lastKey=key;
    window.SignalXQuotexBridge={publish:()=>{}};
    window.postMessage({source:"SignalXQuotex",type:"CHART_DATA",data:d},"*");
  });
  window.addEventListener("message",(event)=>{
    if(event.source!==window || event.data?.source!=="SignalXQuotexPage") return;
    const d=normalize(event.data.data);
    if(d) window.postMessage({source:"SignalXQuotex",type:"CHART_DATA",data:d},"*");
  });
})();