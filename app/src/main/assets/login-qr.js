(function () {
  'use strict';
  var nonce = __SESSION_NONCE__;
  if (window.__cloudCurtainQrSession === nonce) return;
  window.__cloudCurtainQrSession = nonce;
  var started = Date.now(), last = '', busy = false, clicked = [];
  function send(type, image) {
    if (window.CloudCurtainQr) window.CloudCurtainQr.postMessage(JSON.stringify({nonce: nonce, type: type, image: image || ''}));
  }
  function visible(e) {
    if (!e) return false;
    var r = e.getBoundingClientRect(), s = getComputedStyle(e);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
  }
  function identity(e) { return String(e.id || '') + ' ' + String(e.className && e.className.baseVal || e.className || ''); }
  function candidate(e) {
    if (!visible(e)) return false;
    var r = e.getBoundingClientRect();
    if (r.width < 80 || r.height < 80 || Math.abs(r.width-r.height) > Math.max(r.width,r.height)*.15) return false;
    var p = e, names = '', depth = 0;
    while (p && depth++ < 5) { names += ' ' + identity(p); p = p.parentElement; }
    // Reject download/share/payment QR codes even if the surrounding page also has a login button.
    if (/download|app-download|share|payment|pay-|vip|bindphone/i.test(names)) return false;
    if (/qrcode-display|qrcode-login|qr-login|login-qr|qrcode-img|qrcode-image|qr-code-image|qr-code-img|qrcode-container|qrcode-main|qrcode-box|qrlogin|qrCode|qr-code|qrcode/i.test(names)) return true;
    // Some current official login pages use generated class names. In that
    // case require nearby login wording before accepting a generic square.
    var context = '', q = e;
    for (var d = 0; q && d < 4; d++, q = q.parentElement) context += ' ' + (q.innerText || '');
    return r.width >= 120 && /登录|扫码|二维码/.test(context) && !/下载|分享|支付|会员|绑定手机/.test(context);
  }
  function publish(value) { if (value && value !== last) {last = value;send('qr', value);} }
  function pixels(e) {
    try {
      if (e.tagName.toLowerCase() === 'canvas') { publish(e.toDataURL('image/png'));return; }
      var src;
      if (e.tagName.toLowerCase() === 'svg') src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(e));
      else src = e.currentSrc || e.src;
      if (!src || busy) return;
      busy = true;var img = new Image();
      img.onload = function () {
        busy = false;
        try { var canvas = document.createElement('canvas');canvas.width = 512;canvas.height = 512;
          var ctx = canvas.getContext('2d');ctx.fillStyle = '#ffffff';ctx.fillRect(0,0,512,512);ctx.drawImage(img,0,0,512,512);publish(canvas.toDataURL('image/png'));
        } catch (_) { if (/^https:\/\//.test(src)) publish(src); }
      };
      img.onerror = function () {busy = false;};img.src = src;
    } catch (_) {}
  }
  function tick() {
    if (Date.now()-started > 180000 || window.__cloudCurtainQrSession !== nonce) {clearInterval(timer);return;}
    if (!document.body) return;
    var fields = document.querySelectorAll('[class*="qr"], [id*="qr"], [class*="scan"]');
    for (var k=0;k<fields.length;k++) {
      var f=fields[k];
      if (visible(f) && f.innerText && f.innerText.length < 250 && /二维码.*(失效|过期)|(失效|过期).*二维码/.test(f.innerText)) {last='';send('expired');return;}
    }
    var elements = document.querySelectorAll('canvas, svg, img');
    for (var i=0;i<elements.length;i++) if (candidate(elements[i])) {pixels(elements[i]);return;}
    // Only switch login presentation. Never click agreement, authorize, captcha, or confirmation controls.
    var toggles = document.querySelectorAll('button, a, [role="tab"], .login-type, .qrcode-target, .pass-login-tab');
    for (var j=0;j<toggles.length;j++) {
      var el=toggles[j], label=(el.innerText||el.getAttribute('title')||'').trim();
      if (visible(el) && clicked.indexOf(el)<0 && /^(扫码登录|二维码登录|扫码快捷登录)$/.test(label)) {clicked.push(el);el.click();break;}
    }
  }
  var timer=setInterval(tick,1000);tick();
}());
