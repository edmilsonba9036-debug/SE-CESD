import { useEffect, useState } from 'react';
import type { Avisar } from '../App';
import { Area, Botao, Campo, Cartao, Texto } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import { CHIP, CHAVES, CORES_FASE, Fato, Memorial, TRILHAS, TRILHAS_SUB } from '../lib/tipos';

export default function TelaMemorial({ avisar }: { avisar: Avisar }) {
  const [memorial, setMemorial] = useState<Memorial>({});
  const [fase, setFase] = useState(0);
  const [editIndice, setEditIndice] = useState(-1);
  const [verTudo, setVerTudo] = useState(false);
  const [titulo, setTitulo] = useState('');
  const [data, setData] = useState('');
  const [local, setLocal] = useState('');
  const [descricao, setDescricao] = useState('');

  useEffect(() => {
    cofre.lerDados().then((d) => setMemorial(d.memorial)).catch(() => setMemorial({}));
  }, []);

  const chaveAtual = CHAVES[fase];
  const fatos = memorial[chaveAtual] ?? [];

  async function persistir(novo: Memorial, msg: string) {
    const dados = await cofre.lerDados();
    dados.memorial = novo;
    await cofre.salvarDados(dados);
    setMemorial(novo);
    avisar(msg);
  }

  async function salvar() {
    if (titulo.trim().length < 3) { avisar('Dê um título ao fato (mínimo de 3 letras).', false); return; }
    const fato: Fato = { titulo: titulo.trim(), data: data.trim(), local: local.trim(), descricao: descricao.trim() };
    const lista = [...fatos];
    if (editIndice >= 0) lista[editIndice] = fato;
    else lista.push(fato);
    lista.sort((a, b) => (a.data || '').localeCompare(b.data || ''));
    await persistir({ ...memorial, [chaveAtual]: lista }, editIndice >= 0 ? 'Fato atualizado ✓' : 'História registrada no memorial ✓');
    setTitulo(''); setData(''); setLocal(''); setDescricao('');
    setEditIndice(-1);
  }

  function editar(i: number) {
    const f = fatos[i];
    if (!f) return;
    setEditIndice(i);
    setTitulo(f.titulo); setData(f.data); setLocal(f.local); setDescricao(f.descricao);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remover(faseRemover: keyof Memorial, i: number) {
    if (!window.confirm('Excluir este fato do memorial?')) return;
    const lista = [...(memorial[faseRemover] ?? [])];
    lista.splice(i, 1);
    await persistir({ ...memorial, [faseRemover]: lista }, 'Fato removido.');
  }

  const totalFases = CHAVES.reduce((soma, c) => soma + (memorial[c]?.length ?? 0), 0);

  function CartaoFato({ f, faseCor, nomeFase, aoEditar, aoRemover }: {
    f: Fato; faseCor: string; nomeFase?: string; aoEditar: () => void; aoRemover: () => void;
  }) {
    return (
      <Cartao>
        <h3 className="text-[15px] font-bold text-marinha">{f.titulo}</h3>
        {nomeFase && (
          <p className="mt-0.5 text-[11.5px] font-bold uppercase" style={{ color: faseCor }}>{nomeFase}</p>
        )}
        {(f.data || f.local) && (
          <p className="mt-0.5 text-[12.5px] text-tinta">{[f.data, f.local].filter(Boolean).join(' • ')}</p>
        )}
        {f.descricao && <Texto className="mt-1">{f.descricao}</Texto>}
        <div className="mt-3 flex gap-2">
          <button onClick={aoEditar} className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">Editar</button>
          <button onClick={aoRemover} className="flex-1 rounded-xl bg-[#fde7ea] px-3 py-2 text-[13px] font-semibold text-[#B00020]">Remover</button>
        </div>
      </Cartao>
    );
  }

  return (
    <div className="mt-3">
      {/* FÓRMULÁRIO SEMPRE À VISTA (campos fora de botões/janelinhas) */}
      <Cartao>
        <p className="text-[13px] font-bold text-marinha">
          {editIndice >= 0 ? 'EDITANDO FATO — altere os campos e toque em Salvar' : 'REGISTRAR FATO — os campos estão aqui à vista'}
        </p>
        <p className="mt-1 text-[12px] text-tinta">Fase: {TRILHAS[fase]}</p>
        <div className="mt-2 flex gap-1.5">
          {CHIP.map((rotulo, i) => (
            <button
              key={rotulo}
              onClick={() => { setFase(i); setEditIndice(-1); }}
              className={'flex-1 rounded-xl border px-2 py-2 text-[13px] font-bold transition ' +
                (fase === i ? 'text-white' : 'border-[#C7D4E6] bg-[#E8EEF6] text-marinha')}
              style={fase === i ? { backgroundColor: CORES_FASE[i], borderColor: CORES_FASE[i] } : undefined}
            >
              {rotulo}
            </button>
          ))}
        </div>
        <p className="mt-2 text-[12px] text-tinta">{TRILHAS_SUB[fase]}</p>
        <div className="mt-3 flex flex-col gap-3">
          <Campo placeholder="Fato/acontecimento (ex.: Aprovação no concurso do CESD)" value={titulo} onChange={(e) => setTitulo(e.target.value)} />
          <Campo placeholder="Data (AAAA-MM-DD)" inputMode="numeric" value={data} onChange={(e) => setData(e.target.value)} />
          <Campo placeholder="Unidade/Cidade (ex.: 3ª Cia — Exército; CESD — FAB)" value={local} onChange={(e) => setLocal(e.target.value)} />
          <Area rows={4} placeholder="Conte essa história com suas palavras." value={descricao} onChange={(e) => setDescricao(e.target.value)} />
          <Botao primario onClick={salvar}>{editIndice >= 0 ? 'Salvar alterações' : 'Salvar fato'}</Botao>
          {editIndice >= 0 && (
            <Botao onClick={() => { setEditIndice(-1); setTitulo(''); setData(''); setLocal(''); setDescricao(''); }}>
              Cancelar edição
            </Botao>
          )}
        </div>
      </Cartao>

      <div className="mt-4 flex items-center justify-between">
        <h2 className="text-[15px] font-bold text-marinha">
          {verTudo ? 'Linha do tempo' : TRILHAS[fase]}
          <span className="ml-2 text-[12px] font-semibold text-tinta">
            {verTudo ? totalFases + ' fatos' : fatos.length + (fatos.length === 1 ? ' fato' : ' fatos')}
          </span>
        </h2>
        <button onClick={() => setVerTudo(!verTudo)} className="rounded-lg bg-white px-3 py-1.5 text-[12px] font-bold text-medio shadow">
          {verTudo ? 'Ver por fase' : 'Ver linha do tempo'}
        </button>
      </div>

      <div className="mt-3 flex flex-col gap-3">
        {verTudo && totalFases === 0 && <Texto>Nenhum fato ainda. Use o formulário acima para começar.</Texto>}
        {verTudo && CHAVES.flatMap((c, i) =>
          (memorial[c] ?? []).map((f, j) => (
            <CartaoFato key={c + j} f={f} faseCor={CORES_FASE[i]} nomeFase={TRILHAS[i]}
                        aoEditar={() => { setFase(i); setVerTudo(false); editar(j); }}
                        aoRemover={() => remover(c, j)} />
          )),
        )}
        {!verTudo && fatos.length === 0 && <Texto>Nenhum fato nesta fase. Toque num chip (I, II, III, IV, EX) e registre o primeiro.</Texto>}
        {!verTudo && fatos.map((f, i) => (
          <CartaoFato key={i} f={f} faseCor={CORES_FASE[fase]} aoEditar={() => editar(i)} aoRemover={() => remover(chaveAtual, i)} />
        ))}
      </div>
    </div>
  );
}
