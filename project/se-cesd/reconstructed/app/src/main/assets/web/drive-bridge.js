/*
 * SE • CESD — ponte de backup no Google Drive.
 *
 * Este script só entra em ação dentro do APK (o objeto nativo window.SecesoDrive
 * é injetado pelo DriveBackupManager). No navegador puro ele não mostra nada.
 *
 * O que ele faz:
 *   - Lê o cofre IndexedDB "se-cesd-cofre" (store "dados") — os registros JÁ estão
 *     criptografados (AES-GCM) pelo próprio aplicativo, indecifráveis sem a senha.
 *   - Empacota tudo em base64 e entrega ao nativo (SecesoDrive.backup).
 *   - Recebe o backup restaurado (SecesoDriveBridge.onRestore) e, após confirmação
 *     do usuário, grava os registros de volta no cofre e recarrega o app.
 *
 * Os parâmetros de proteção (sal PBKDF2, iterações e verificador de senha) são
 * registros como os outros: viajam no backup, então o mesmo desbloqueia o cofre
 * no outro aparelho com a MESMA senha.
 */
(function () {
  'use strict';

  var nativo = window.SecesoDrive;
  if (!nativo || typeof nativo.disponivel !== 'function' || !nativo.disponivel()) {
    return; // navegador puro: nada a fazer
  }

  var DB_NOME = 'se-cesd-cofre';
  var STORE = 'dados';
  var backupPendente = null; // base64 baixado, aguardando confirmação

  // ------------------------------------------------------------------
  // Painel flutuante
  // ------------------------------------------------------------------

  var css = document.createElement('style');
  css.textContent = [
    '#se-drive-painel{position:fixed;right:calc(12px + env(safe-area-inset-right));',
    'bottom:calc(12px + env(safe-area-inset-bottom));z-index:2147483647;',
    'font-family:system-ui,-apple-system,sans-serif;text-align:right}',
    '#se-drive-botao{width:48px;height:48px;border-radius:50%;border:0;cursor:pointer;',
    'background:#0a2463;color:#fff;box-shadow:0 2px 8px rgba(0,0,0,.35);',
    'display:flex;align-items:center;justify-content:center;padding:0}',
    '#se-drive-caixa{display:none;background:#fff;color:#0a2463;border-radius:14px;',
    'box-shadow:0 4px 16px rgba(0,0,0,.3);padding:14px;margin-bottom:8px;width:250px;text-align:left}',
    '#se-drive-caixa.aberto{display:block}',
    '#se-drive-caixa h3{margin:0 0 6px;font-size:14px}',
    '#se-drive-caixa p{margin:0 0 10px;font-size:12px;color:#444;line-height:1.4}',
    '.se-drive-acao{display:block;width:100%;margin:6px 0 0;border:0;border-radius:8px;',
    'padding:9px 10px;font-size:13px;cursor:pointer;background:#0a2463;color:#fff}',
    '.se-drive-acao.secundario{background:#e3ecf7;color:#0a2463}',
    '.se-drive-acao:disabled{opacity:.55;cursor:default}',
    '#se-drive-status{font-size:12px;margin-top:8px;min-height:15px;color:#333;word-wrap:break-word}'
  ].join('');
  document.head.appendChild(css);

  var painel = document.createElement('div');
  painel.id = 'se-drive-painel';
  painel.innerHTML =
    '<div id="se-drive-caixa">' +
    '  <h3>Backup no Google Drive</h3>' +
    '  <p>O cofre vai criptografado para a pasta “SE • CESD” do seu Drive. ' +
    'Para abrir em outro aparelho, use a mesma senha.</p>' +
    '  <button class="se-drive-acao" id="se-drive-enviar">Enviar backup</button>' +
    '  <button class="se-drive-acao secundario" id="se-drive-restaurar">Restaurar do Drive</button>' +
    '  <button class="se-drive-acao secundario" id="se-drive-conectar">Conectar ao Drive</button>' +
    '  <div id="se-drive-status"></div>' +
    '</div>' +
    '<button id="se-drive-botao" aria-label="Backup no Google Drive">' +
    '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
    'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/>' +
    '<path d="M12 12v9"/>' +
    '<path d="m16 16-4-4-4 4"/></svg></button>';
  document.body.appendChild(painel);

  var caixa = document.getElementById('se-drive-caixa');
  var statusEl = document.getElementById('se-drive-status');
  var bEnviar = document.getElementById('se-drive-enviar');
  var bRestaurar = document.getElementById('se-drive-restaurar');
  var bConectar = document.getElementById('se-drive-conectar');

  document.getElementById('se-drive-botao').addEventListener('click', function () {
    caixa.classList.toggle('aberto');
  });

  function status(texto, ok) {
    statusEl.textContent = texto || '';
    statusEl.style.color = ok ? '#1b5e20' : '#b00020';
  }

  function ocupar(ocupado) {
    bEnviar.disabled = ocupado;
    bRestaurar.disabled = ocupado;
    bConectar.disabled = ocupado;
  }

  // ------------------------------------------------------------------
  // Coletar o cofre e enviar
  // ------------------------------------------------------------------

  function abrirCofre() {
    return new Promise(function (resolver, rejeitar) {
      var pedido = indexedDB.open(DB_NOME);
      pedido.onupgradeneeded = function (evento) {
        // Banco ainda não existia: aborta a criação para não poluir o aparelho.
        if (evento.oldVersion === 0 && !evento.target.result.objectStoreNames.contains(STORE)) {
          evento.target.transaction.abort();
        }
      };
      pedido.onsuccess = function () {
        var db = pedido.result;
        if (!db.objectStoreNames.contains(STORE)) {
          db.close();
          rejeitar(new Error('cofre-ausente'));
          return;
        }
        resolver(db);
      };
      pedido.onerror = function () {
        rejeitar(pedido.error || new Error('indexeddb'));
      };
    });
  }

  function coletarRegistros() {
    return abrirCofre().then(function (db) {
      return new Promise(function (resolver, rejeitar) {
        var tx = db.transaction(STORE, 'readonly');
        var loja = tx.objectStore(STORE);
        var pedidoChaves = loja.getAllKeys();
        var pedidoValores = loja.getAll();
        tx.oncomplete = function () {
          db.close();
          var chaves = pedidoChaves.result || [];
          var valores = pedidoValores.result || [];
          var registros = [];
          for (var i = 0; i < chaves.length; i++) {
            registros.push({ c: String(chaves[i]), v: valores[i] });
          }
          resolver(registros);
        };
        tx.onerror = function () {
          db.close();
          rejeitar(tx.error || new Error('leitura'));
        };
        tx.onabort = function () {
          db.close();
          rejeitar(tx.error || new Error('leitura'));
        };
      });
    });
  }

  function paraBase64(texto) {
    var bytes = new TextEncoder().encode(texto);
    var binario = '';
    var BLOCO = 0x8000;
    for (var i = 0; i < bytes.length; i += BLOCO) {
      binario += String.fromCharCode.apply(null, bytes.subarray(i, i + BLOCO));
    }
    return btoa(binario);
  }

  function deBase64(base64) {
    var binario = atob(base64);
    var bytes = new Uint8Array(binario.length);
    for (var i = 0; i < binario.length; i++) {
      bytes[i] = binario.charCodeAt(i);
    }
    return new TextDecoder().decode(bytes);
  }

  bEnviar.addEventListener('click', function () {
    status('Lendo o cofre…', true);
    ocupar(true);
    coletarRegistros()
      .then(function (registros) {
        if (!registros.length) {
          ocupar(false);
          status('Nada para enviar ainda — crie seu acesso primeiro.', false);
          return;
        }
        var conteudo = JSON.stringify({
          app: 'SE-CESD',
          esquema: 1,
          criadoEm: new Date().toISOString(),
          registros: registros
        });
        status('Enviando para o Google Drive…', true);
        nativo.backup(paraBase64(conteudo));
      })
      .catch(function () {
        ocupar(false);
        status('Não foi possível ler o cofre neste aparelho.', false);
      });
  });

  bRestaurar.addEventListener('click', function () {
    if (backupPendente) {
      aplicarRestauracao();
      return;
    }
    ocupar(true);
    status('Procurando o backup no Drive…', true);
    nativo.restaurar();
  });

  bConectar.addEventListener('click', function () {
    ocupar(true);
    status('Conectando ao Google…', true);
    nativo.conectar();
  });

  // ------------------------------------------------------------------
  // Receber do nativo
  // ------------------------------------------------------------------

  window.SecesoDriveBridge = {
    onStatus: function (informacao) {
      ocupar(false);
      status(informacao && informacao.mensagem ? informacao.mensagem : '', !!informacao && informacao.ok);
    },
    onRestore: function (base64) {
      ocupar(false);
      try {
        var externo = JSON.parse(deBase64(base64));
        var interno = JSON.parse(deBase64(externo.dados));
        if (!interno || !interno.registros || !interno.registros.length) {
          status('O backup está vazio.', false);
          return;
        }
        backupPendente = interno;
        var quando = externo.criadoEm ? String(externo.criadoEm).replace('T', ' ').slice(0, 16) : '';
        bRestaurar.textContent = 'Confirmar restauração' + (quando ? ' (' + quando + ')' : '');
        status('Backup de ' + (quando || 'data desconhecida') + ' baixado. ' +
          'Ao confirmar, os dados DESTE aparelho serão substituídos pelos do backup. ' +
          'Toque novamente em “Confirmar restauração”.', true);
      } catch (e) {
        status('O backup baixado não pôde ser interpretado.', false);
      }
    }
  };

  function aplicarRestauracao() {
    if (!backupPendente) return;
    ocupar(true);
    status('Gravando o backup no cofre…', true);
    abrirCofre()
      .then(function (db) {
        return new Promise(function (resolver, rejeitar) {
          var tx = db.transaction(STORE, 'readwrite');
          var loja = tx.objectStore(STORE);
          loja.clear();
          var registros = backupPendente.registros;
          for (var i = 0; i < registros.length; i++) {
            loja.put(registros[i].v, registros[i].c);
          }
          tx.oncomplete = function () {
            db.close();
            resolver();
          };
          tx.onerror = function () {
            db.close();
            rejeitar(tx.error || new Error('gravação'));
          };
          tx.onabort = function () {
            db.close();
            rejeitar(tx.error || new Error('gravação'));
          };
        });
      })
      .then(function () {
        backupPendente = null;
        status('Backup restaurado ✓ Recarregando…', true);
        setTimeout(function () {
          location.reload();
        }, 900);
      })
      .catch(function () {
        ocupar(false);
        status('Não foi possível gravar o backup neste aparelho.', false);
      });
  }
})();
