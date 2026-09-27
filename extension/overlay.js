/* SignalX floating overlay for the current Quotex chart. */
(function(){
  if(window.top!==window.self)return;
  let candles=[], market="বর্তমান মার্কেট", timeframe="—", lastRender=null, root;

  const esc=s=>String(s??"").replace(/[&<>"]/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[m]));
  function mount(){
    if(document.getElementById("signalx-overlay-host"))return;
    const host=document.createElement("div");
    host.id="signalx-overlay-host";
    document.documentElement.appendChild(host);
    root=host.attachShadow({mode:"open"});
    root.innerHTML=`<style>
      :host{all:initial}
      .box{position:fixed;right:16px;top:96px;width:286px;z-index:2147483647;font-family:Arial,sans-serif;color:#eef4ff;background:rgba(12,17,28,.96);border:1px solid rgba(120,160,220,.25);border-radius:16px;box-shadow:0 14px 40px rgba(0,0,0,.45);backdrop-filter:blur(12px);overflow:hidden}
      .head{display:flex;align-items:center;justify-content:space-between;padding:11px 12px;cursor:move;border-bottom:1px solid rgba(255,255,255,.08)}
      .brand{font-weight:800;letter-spacing:.5px}.brand small{display:block;font-size:9px;color:#91a0b8;font-weight:500;letter-spacing:0}
      button{border:0;color:#fff;background:transparent;cursor:pointer}.mini{font-size:18px}
      .body{padding:12px}.meta{font-size:11px;color:#aebbd0;margin-bottom:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      .signal{font-size:34px;font-weight:900;text-align:center;letter-spacing:1px;padding:8px 0}.wait{color:#f1c96b}.call{color:#55e39a}.put{color:#ff7180}
      .score{display:flex;justify-content:space-between;font-size:12px;margin:5px 0}.bar{height:7px;background:#202a3a;border-radius:99px;overflow:hidden}.fill{height:100%;width:0;background:#6da8ff;transition:width .2s}
      .grid{display:grid;grid-template-columns:1fr 1fr;gap:7px;margin-top:10px}.item{background:#151e2d;border-radius:9px;padding:7px}.label{font-size:9px;color:#8f9db2}.val{font-size:12px;margin-top:3px}
      .reason{font-size:11px;line-height:1.45;color:#cbd6e8;background:#151e2d;border-radius:9px;padding:9px;margin-top:9px}
      .btn{width:100%;margin-top:10px;padding:10px;border-radius:10px;background:#3d78ff;color:white;font-weight:800;letter-spacing:.5px}.btn:disabled{opacity:.55}
      .status{font-size:9px;color:#7f90aa;text-align:center;margin-top:8px}.hidden .body{display:none}
      @media(max-width:600px){.box{width:calc(100vw - 24px);max-width:286px;right:12px;top:70px}.body{padding:10px}}
    </style>
    <div class="box">
      <div class="head"><div class="brand">SignalX<small>QUOTEX CHART</small></div><button class="mini" aria-label="Minimize">−</button></div>
      <div class="body">
        <div class="meta"><span id="market">বর্তমান মার্কেট</span> · <span id="tf">—</span></div>
        <div id="signal" class="signal wait">WAIT</div>
        <div class="score"><span>Signal Score</span><b id="score">0/100</b></div>
        <div class="bar"><div id="fill" class="fill"></div></div>
        <div class="grid">
          <div class="item"><div class="label">ট্রেন্ড</div><div id="trend" class="val">—</div></div>
          <div class="item"><div class="label">মোমেন্টাম</div><div id="momentum" class="val">—</div></div>
          <div class="item"><div class="label">স্ট্রাকচার</div><div id="structure" class="val">—</div></div>
          <div class="item"><div class="label">RSI</div><div id="rsi" class="val">—</div></div>
        </div>
        <div id="reason" class="reason">চার্ট ডাটা অপেক্ষায়...</div>
        <button id="btn" class="btn">GET SIGNAL</button>
        <div id="status" class="status">External market-data API ব্যবহার করা হচ্ছে না</div>
      </div>
    </div>`;
    const box=root.querySelector(".box");
    root.querySelector(".mini").onclick=()=>box.classList.toggle("hidden");
    root.querySelector(".btn").onclick=analyze;
    drag(root.querySelector(".head"),box);
  }
  function drag(handle,el){
    let down=false,sx=0,sy=0,sl=0,st=0;
    handle.addEventListener("pointerdown",e=>{down=true;sx=e.clientX;sy=e.clientY;const r=el.getBoundingClientRect();sl=r.left;st=r.top;handle.setPointerCapture?.(e.pointerId)});
    handle.addEventListener("pointermove",e=>{if(!down)return;const x=Math.max(6,Math.min(innerWidth-el.offsetWidth-6,sl+e.clientX-sx));const y=Math.max(6,Math.min(innerHeight-el.offsetHeight-6,st+e.clientY-sy));el.style.left=x+"px";el.style.top=y+"px";el.style.right="auto"});
    handle.addEventListener("pointerup",()=>down=false);handle.addEventListener("pointercancel",()=>down=false);
  }
  function render(r){
    if(!root)return;
    lastRender=r;
    const q=id=>root.getElementById(id);
    q("signal").textContent=r.signal;q("signal").className="signal "+r.signal.toLowerCase();
    q("score").textContent=r.score+"/100";q("fill").style.width=Math.max(0,Math.min(100,r.score))+"%";
    q("trend").textContent=r.trend||"—";q("momentum").textContent=r.momentum||"—";q("structure").textContent=r.structure||"—";q("rsi").textContent=r.rsi==null?"—":r.rsi.toFixed(1);q("reason").textContent=r.reason||"—";
  }
  function analyze(){
    const b=root?.getElementById("btn");if(b)b.disabled=true;
    render(SignalXEngine.analyze(candles));
    if(b)b.disabled=false;
  }
  function apply(d){
    if(!d||!Array.isArray(d.candles))return;
    candles=d.candles;market=d.market||market;timeframe=d.timeframe||timeframe;
    root.getElementById("market").textContent=market;root.getElementById("tf").textContent=timeframe;
    root.getElementById("status").textContent=candles.length+"টি চার্ট ক্যান্ডেল সংযুক্ত";
    analyze();
  }
  mount();
  window.addEventListener("message",e=>{
    if(e.source===window&&e.data?.source==="SignalXQuotex"&&e.data?.type==="CHART_DATA")apply(e.data.data);
  });
})();