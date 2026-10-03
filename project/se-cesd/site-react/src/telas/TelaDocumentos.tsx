import { useEffect, useRef, useState } from 'react';
import type { Avisar } from '../App';
import { Botao, Cartao, Moldura, Texto } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import { bytesParaBase64, base64ParaBytes } from '../lib/cofre';
import { bitmapComExif, girarBase64, paraBase64Jpeg, lerBytes } from '../lib/imagens';
import type { DocSlot } from '../lib/tipos';

const MAXIMO = 4;
const LIMITE = 10 * 1024 * 1024; // 10 MB

export default function TelaDocumentos({ avisar }: { avisar: Avisar }) {
  const [documentos, setDocumentos] = useState<(DocSlot | null)[]>([]);
  const [nomeEdit, setNomeEdit] = useState<{ i: number; valor: string } | null>(null);
  const refArquivo = useRef<HTMLInputElement>(null);
  const pedindo = useRef(-1);

  useEffect(() => {
    cofre.lerDados().then((d) => {
      const lista: (DocSlot | null)[] = [];
      for (let i = 0; i < MAXIMO; i++) lista.push(d.documentos[i] ?? null);
      setDocumentos(lista);
    }).catch(() => setDocumentos(Array(MAXIMO).fill(null)));
  }, []);

  async function persistir(lista: (DocSlot | null)[], msg?: string) {
    const dados = await cofre.lerDados();
    dados.documentos = lista.filter((d) => d && d.doc) as DocSlot[];
    await cofre.salvarDados(dados);
    setDocumentos(lista);
    if (msg) avisar(msg);
  }

  async function aoEscolher(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0];
    e.target.value = '';
    const slot = pedindo.current;
    if (!arquivo || slot < 0) return;
    try {
      let doc: DocSlot;
      if (arquivo.type.startsWith('image/')) {
        const bitmap = await bitmapComExif(arquivo);
        doc = { tipo: 'imagem', doc: await paraBase64Jpeg(bitmap, 1280), legenda: arquivo.name || 'Documento ' + (slot + 1) };
      } else {
        const bytes = await lerBytes(arquivo, LIMITE);
        const cabecalho = new TextDecoder().decode(bytes.slice(0, 5));
        if (cabecalho !== '%PDF-') { avisar('Formato não suportado: use PDF ou imagem (JPG/PNG).', false); return; }
        doc = { tipo: 'pdf', doc: bytesParaBase64(bytes), legenda: arquivo.name || 'Documento ' + (slot + 1), nome: arquivo.name };
      }
      const nova = [...documentos];
      nova[slot] = doc;
      await persistir(nova, 'Documento guardado ✓ criptografado.');
    } catch (erro) {
      avisar(String(erro) === 'Error: muito grande'
        ? 'Documento muito grande (máximo 10 MB).'
        : 'Não foi possível inserir o documento.', false);
    }
  }

  async function girar(i: number) {
    const d = documentos[i];
    if (!d?.doc || d.tipo !== 'imagem') return;
    try {
      const nova = await girarBase64(d.doc, 270);
      const lista = [...documentos];
      lista[i] = { ...d, doc: nova };
      await persistir(lista, 'Imagem girada ✓');
    } catch {
      avisar('Não consegui girar esta imagem.', false);
    }
  }

  function abrirPdf(i: number) {
    const d = documentos[i];
    if (!d?.doc) return;
    const bytes = base64ParaBytes(d.doc);
    const blob = new Blob([bytes.buffer as ArrayBuffer], { type: 'application/pdf' });
    window.open(URL.createObjectURL(blob), '_blank');
  }

  async function remover(i: number) {
    if (!window.confirm('Remover este documento?')) return;
    const lista = [...documentos];
    lista[i] = null;
    await persistir(lista, 'Documento removido.');
  }

  async function salvarNome() {
    if (!nomeEdit) return;
    const { i, valor } = nomeEdit;
    const d = documentos[i];
    if (!d?.doc || !valor.trim()) { setNomeEdit(null); return; }
    const lista = [...documentos];
    lista[i] = { ...d, legenda: valor.trim() };
    await persistir(lista);
    setNomeEdit(null);
  }

  const ocupados = documentos.filter((d) => d?.doc).length;

  return (
    <div className="mt-3">
      <div className="flex flex-col gap-4">
        {documentos.map((d, i) => {
          const ehPdf = d?.tipo === 'pdf';
          const nome = d?.legenda ?? d?.nome ?? 'Documento ' + (i + 1);
          return (
            <Cartao key={i}>
              <p className="text-center text-[11px] font-bold tracking-wide text-ouro">DOCUMENTO {i + 1} DE {MAXIMO}</p>
              {d?.doc ? (
                nomeEdit?.i === i ? (
                  <div className="mt-1 flex items-center gap-2">
                    <input className="w-full rounded-lg border border-[#C7D4E6] px-2 py-1.5 text-[14px]"
                           value={nomeEdit.valor} onChange={(e) => setNomeEdit({ i, valor: e.target.value })}
                           onKeyDown={(e) => { if (e.key === 'Enter') salvarNome(); }} autoFocus />
                    <button onClick={salvarNome} className="rounded-lg bg-marinha px-3 py-1.5 text-[12px] font-bold text-white">ok</button>
                  </div>
                ) : (
                  <button onClick={() => setNomeEdit({ i, valor: nome })}
                          className="mx-auto mt-1 block max-w-full truncate text-[15px] font-bold text-marinha">
                    {nome} <span className="text-[12px]">✎</span>
                  </button>
                )
              ) : (
                <p className="mt-1 text-center text-[15px] font-bold text-marinha">(vazio)</p>
              )}

              <Moldura marcador={'📄\nToque para\ninserir o documento'}
                       aoTocar={() => { if (!d?.doc) { pedindo.current = i; refArquivo.current?.click(); } }}>
                {d?.doc && !ehPdf && (
                  <img src={'data:image/jpeg;base64,' + d.doc} alt={nome} className="h-full w-full rounded-lg object-contain" />
                )}
                {d?.doc && ehPdf && (
                  <div className="flex h-full w-full flex-col items-center justify-center rounded-lg bg-white">
                    <span className="text-4xl">📄</span>
                    <span className="mt-1 px-2 text-center text-[12px] font-semibold text-marinha">PDF</span>
                  </div>
                )}
              </Moldura>
              <p className="mt-2 text-center text-[12px] text-tinta">
                {d?.doc
                  ? ehPdf ? 'Toque em “Ver páginas” para folhear dentro do app.' : '⟲ gira a imagem 90° anti-horário (fica gravado).'
                  : 'Toque na moldura para inserir (PDF ou imagem, até 10 MB).'}
              </p>

              {d?.doc && (
                <div className="mt-3 flex gap-2">
                  {ehPdf ? (
                    <button onClick={() => abrirPdf(i)} className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">Ver páginas</button>
                  ) : (
                    <button onClick={() => girar(i)} className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">⟲ Girar</button>
                  )}
                  <button onClick={() => remover(i)} className="flex-1 rounded-xl bg-[#fde7ea] px-3 py-2 text-[13px] font-semibold text-[#B00020]">Remover</button>
                </div>
              )}
            </Cartao>
          );
        })}
      </div>

      <input ref={refArquivo} type="file" accept="application/pdf,image/*" className="hidden" onChange={aoEscolher} />
      <Texto className="mt-4 text-center">{ocupados} de {MAXIMO} locais ocupados.</Texto>
    </div>
  );
}
