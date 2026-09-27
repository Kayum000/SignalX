(()=>{
  let candles=[],market="—",timeframe="—";
  const $=id=>document.getElementById(id);

  function renderCandleStrip(){
    const host=$("candles");
    if(!host)return;
    host.innerHTML=candles.slice(-32).map(c=>{
      const range=Math.max(c.high-c.low,1e-9);
      const body=Math.max(Math.abs(c.close-c.open)/range*72,8);
      const wick=Math.min(82,Math.max(35,(c.high-c.low)/range*82));
      const dir=c.close>=c.open?"up":"down";
      return '<div class="candle '+dir+'" title="O '+c.open+' | H '+c.high+' | L '+c.low+' | C '+c.close+'">'+
        '<i class="wick" style="height:'+wick+'%"></i>'+
        '<i class="body" style="--h:'+body+'%"></i>'+
      '</div>';
    }).join("");
  }

  function render(r){
    $("signal").textContent=r.signal;
    $("score").textContent=r.score;
    $("meter").style.width=r.score+"%";
    $("trend").textContent=r.trend;
    $("momentum").textContent=r.momentum;
    $("structure").textContent=r.structure;
    $("rsi").textContent=r.rsi==null?"—":r.rsi.toFixed(1);
    $("reason").textContent=r.reason;
    $("confidence").textContent=r.signal==="WAIT"?"নিশ্চিত কনফার্মেশন নেই":r.score>=80?"উচ্চ কনফার্মেশন":"মাঝারি কনফার্মেশন";
    $("signalCard").className="signal-card "+r.signal.toLowerCase();
    $("checks").innerHTML=(r.checks||[]).map(x=>'<span class="check '+x[1]+'">'+x[0]+"</span>").join("");
    $("candleCount").textContent=candles.length;
    renderCandleStrip();
  }

  function updateMeta(m,t){
    if(m&&m!=="—")market=m;
    if(t&&t!=="—")timeframe=t;
    $("market").textContent=market;
    $("tf").textContent="Timeframe "+timeframe;
  }

  function applyChartData(d){
    if(!d||!Array.isArray(d.candles))return;
    candles=d.candles;
    updateMeta(d.market,d.timeframe);
    $("statusText").textContent=candles.length?"চার্ট ডাটা সংযুক্ত":"চার্ট ডাটা অপেক্ষায়";
    $("dot").parentElement.classList.toggle("live",candles.length>0);
    render(SignalXEngine.analyze(candles));
  }

  window.SignalX={setChartData:applyChartData,setMarket:(m,t)=>updateMeta(m,t)};

  window.addEventListener("message",e=>{
    if(e.source!==window||e.data?.source!=="SignalXQuotex"||e.data?.type!=="CHART_DATA")return;
    applyChartData(e.data.data);
  });

  $("signalBtn").addEventListener("click",()=>{
    const b=$("signalBtn");
    if(!candles.length){
      render(SignalXEngine.analyze([]));
      return;
    }
    b.disabled=true;
    b.textContent="ANALYZING...";
    requestAnimationFrame(()=>{
      render(SignalXEngine.analyze(candles));
      b.disabled=false;
      b.textContent="GET SIGNAL";
    });
  });
})();