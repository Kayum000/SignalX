/* SignalX browser bridge
 * This file intentionally does NOT call an external market-data service.
 * It exposes a small bridge API so a validated Quotex chart extractor can
 * publish candles to the SignalX page.
 *
 * IMPORTANT: Quotex can change its chart implementation. Do not guess private
 * websocket/protobuf formats. Wire extractValidatedCandles() to the current
 * visible chart implementation after inspecting the live page.
 */
(function(){
  window.SignalXQuotexBridge={
    publish(data){window.postMessage({source:"SignalXQuotex",type:"CHART_DATA",data}, "*")}
  };
  function extractValidatedCandles(){
    // Placeholder: return null until the current Quotex chart representation
    // is inspected. This prevents fabricated signals from fake/default data.
    return null;
  }
  setInterval(()=>{const d=extractValidatedCandles();if(d)SignalXQuotexBridge.publish(d)},1000);
})();