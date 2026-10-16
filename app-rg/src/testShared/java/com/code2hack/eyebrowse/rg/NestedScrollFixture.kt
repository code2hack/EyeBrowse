package com.code2hack.eyebrowse.rg

/** Owned content only; no service, account, cookies, external media or private page observation. */
internal object NestedScrollFixture {
    const val HTML = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        html,body {margin:0;height:100%;overflow:hidden;background:#000;color:#fff}
        button,input {height:36px;box-sizing:border-box}
        .panel {position:fixed;top:50px;bottom:12px;overflow:auto;width:44%;border:1px solid white}
        #left {left:2%} #right {right:2%}
        .long {height:2400px;padding:4px}
        #inner {height:120px;overflow:auto;margin-top:20px}
        #inner>div {height:900px}
        dialog {width:65%;height:240px;padding:12px;background:#000;color:#fff;overflow:auto}
        dialog::backdrop {background:#0008}
        #modal-content {height:1600px}
        #cover {display:none;position:fixed;inset:0;background:#000;z-index:100}
        </style>
        <button id="popup" onclick="document.getElementById('modal').showModal()">Popup</button>
        <button id="background" onclick="document.documentElement.style.overflow='auto';document.body.style.overflow='visible';document.body.style.height='4000px'">Background</button>
        <div id="left" class="panel"><div class="long">
          Left panel <input id="editor" value="owned" aria-label="Owned editor">
          <div id="inner"><div>Nested panel</div></div>
        </div></div>
        <div id="right" class="panel"><div class="long">Right panel</div></div>
        <dialog id="modal"><div id="modal-content">
          <button id="close" onclick="document.getElementById('modal').close()">Close</button>Modal content
        </div></dialog>
        <div id="cover">Non-scrollable overlay</div>
    """
}
