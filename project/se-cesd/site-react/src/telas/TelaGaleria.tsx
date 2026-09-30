import { useEffect, useRef, useState } from 'react';
import type { Avisar } from '../App';
import { Botao, Cartao, Moldura, Texto } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import { bitmapComExif, girarBase64, paraBase64Jpeg } from '../lib/imagens';
import type { FotoSlot } from '../lib/tipos';

const MAXIMO = 4;

export default function TelaGaleria({ avisar }: { avisar: Avisar }) {
  const [galeria, setGaleria] = useState<(FotoSlot | null)[]>([]);
  const [legendaEdit, setLegendaEdit] = useState<{ i: number; valor: string } | null>(null);
  const slotRef = useRef<HTMLInputElement>(null);
  const pedindo = useRef(-1);

  useEffect(() => {
    cofre.lerDados().then((d) => {
      const lista: (FotoSlot | null)[] = [];
      for (let i = 0; i < MAXIMO; i++) lista.push(d.galeria[i] ?? null);
      setGaleria(lista);
    }).catch(() => setGaleria(Array(MAXIMO).fill(null)));
  }, []);

  async function persistir(lista: (FotoSlot | null)[], msg?: string) {
    const compacta = lista.map((f) => f ?? undefined);
    const dados = await cofre.lerDados();
    dados.galeria = compacta.filter((f) => f && f.foto) as FotoSlot[];
    await cofre.salvarDados(dados);
    setGaleria(lista);
    if (msg) avisar(msg);
  }

  async function aoEscolher(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0];
    e.target.value = '';
    const slot = pedindo.current;
    if (!arquivo || slot < 0) return;
    try {
      const bitmap = await bitmapComExif(arquivo);
      const base64 = await paraBase64Jpeg(bitmap, 1280);
      const nova = [...galeria];
      nova[slot] = { legenda: nova[slot]?.legenda ?? 'Foto ' + (slot + 1), foto: base64 };
      await persistir(nova, 'Foto inserida ✓ (rotação da câmera aplicada)');
    } catch {
      avisar('Não foi possível abrir a foto.', false);
    }
  }

  async function girar(i: number) {
    const f = galeria[i];
    if (!f?.foto) return;
    try {
      const nova = await girarBase64(f.foto, 270);
      const lista = [...galeria];
      lista[i] = { ...f, foto: nova };
      await persistir(lista, 'Foto girada ✓ (fica gravada girada)');
    } catch {
      avisar('Não consegui girar essa foto.', false);
    }
  }

  async function remover(i: number) {
    if (!window.confirm('Remover esta foto do local ' + (i + 1) + '?')) return;
    const lista = [...galeria];
    lista[i] = null;
    await persistir(lista, 'Foto removida.');
  }

  async function salvarLegenda() {
    if (!legendaEdit) return;
    const { i, valor } = legendaEdit;
    const f = galeria[i];
    if (!f?.foto || !valor.trim()) { setLegendaEdit(null); return; }
    const lista = [...galeria];
    lista[i] = { ...f, legenda: valor.trim() };
    await persistir(lista);
    setLegendaEdit(null);
  }

  const ocupados = galeria.filter((f) => f?.foto).length;

  return (
    <div className="mt-3">
      <div className="flex flex-col gap-4">
        {galeria.map((f, i) => (
          <Cartao key={i}>
            <p className="text-center text-[11px] font-bold tracking-wide text-ouro">LOCAL {i + 1} DE {MAXIMO}</p>
            {f?.foto ? (
              legendaEdit?.i === i ? (
                <div className="mt-1 flex items-center gap-2">
                  <input
                    className="w-full rounded-lg border border-[#C7D4E6] px-2 py-1.5 text-[14px]"
                    value={legendaEdit.valor}
                    onChange={(e) => setLegendaEdit({ i, valor: e.target.value })}
                    onKeyDown={(e) => { if (e.key === 'Enter') salvarLegenda(); }}
                    autoFocus
                  />
                  <button onClick={salvarLegenda} className="rounded-lg bg-marinha px-3 py-1.5 text-[12px] font-bold text-white">ok</button>
                </div>
              ) : (
                <button
                  onClick={() => setLegendaEdit({ i, valor: f.legenda ?? 'Foto ' + (i + 1) })}
                  className="mx-auto mt-1 block text-[15px] font-bold text-marinha"
                >
                  {f.legenda ?? 'Foto ' + (i + 1)} <span className="text-[12px]">✎</span>
                </button>
              )
            ) : (
              <p className="mt-1 text-center text-[15px] font-bold text-marinha">(vazio)</p>
            )}

            <Moldura
              marcador={'📷\nToque para\ninserir a foto'}
              aoTocar={() => { if (!f?.foto) { pedindo.current = i; slotRef.current?.click(); } }}
            >
              {f?.foto && (
                <img
                  src={'data:image/jpeg;base64,' + f.foto}
                  alt={f.legenda ?? 'Foto'}
                  className="h-full w-full rounded-lg object-contain"
                />
              )}
            </Moldura>
            <p className="mt-2 text-center text-[12px] text-tinta">
              {f?.foto ? '⟲ gira 90° anti-horário (fica gravado)' : 'Toque na moldura para inserir a fotografia aqui.'}
            </p>

            {f?.foto && (
              <div className="mt-3 flex gap-2">
                <button onClick={() => girar(i)} className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">⟲ Girar</button>
                <button onClick={() => { pedindo.current = i; slotRef.current?.click(); }}
                        className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">Trocar</button>
                <button onClick={() => remover(i)}
                        className="flex-1 rounded-xl bg-[#fde7ea] px-3 py-2 text-[13px] font-semibold text-[#B00020]">Remover</button>
              </div>
            )}
          </Cartao>
        ))}
      </div>

      <input ref={slotRef} type="file" accept="image/*" className="hidden" onChange={aoEscolher} />
      <Texto className="mt-4 text-center">{ocupados} de {MAXIMO} locais ocupados.</Texto>
    </div>
  );
}
