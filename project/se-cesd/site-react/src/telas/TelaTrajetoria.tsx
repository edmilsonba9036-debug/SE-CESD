import { useEffect, useState } from 'react';
import type { Avisar } from '../App';
import { Area, Botao, Campo, Cartao, Texto } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import type { Fato } from '../lib/tipos';

export default function TelaTrajetoria({ avisar }: { avisar: Avisar }) {
  const [etapas, setEtapas] = useState<Fato[]>([]);
  const [editIndice, setEditIndice] = useState(-1);
  const [titulo, setTitulo] = useState('');
  const [data, setData] = useState('');
  const [local, setLocal] = useState('');
  const [descricao, setDescricao] = useState('');

  useEffect(() => {
    cofre.lerDados().then((d) => setEtapas(d.trajetoria)).catch(() => setEtapas([]));
  }, []);

  async function persistir(nova: Fato[], msg: string) {
    const dados = await cofre.lerDados();
    dados.trajetoria = nova;
    await cofre.salvarDados(dados);
    setEtapas(nova);
    avisar(msg);
  }

  async function salvar() {
    if (titulo.trim().length < 3) { avisar('Dê um título à etapa (mínimo de 3 letras).', false); return; }
    const etapa: Fato = { titulo: titulo.trim(), data: data.trim(), local: local.trim(), descricao: descricao.trim() };
    const lista = [...etapas];
    if (editIndice >= 0) lista[editIndice] = etapa;
    else lista.push(etapa);
    lista.sort((a, b) => (a.data || '').localeCompare(b.data || ''));
    await persistir(lista, editIndice >= 0 ? 'Etapa atualizada ✓' : 'Etapa adicionada à trajetória ✓');
    setTitulo(''); setData(''); setLocal(''); setDescricao('');
    setEditIndice(-1);
  }

  function editar(i: number) {
    const e = etapas[i];
    if (!e) return;
    setEditIndice(i);
    setTitulo(e.titulo); setData(e.data); setLocal(e.local); setDescricao(e.descricao);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remover(i: number) {
    if (!window.confirm('Excluir esta etapa?')) return;
    const lista = [...etapas];
    lista.splice(i, 1);
    await persistir(lista, 'Etapa removida.');
  }

  return (
    <div className="mt-3">
      {/* FÓRMULÁRIO SEMPRE À VISTA */}
      <Cartao>
        <p className="text-[13px] font-bold text-marinha">
          {editIndice >= 0 ? 'EDITANDO ETAPA — altere os campos e toque em Salvar' : 'REGISTRAR ETAPA — os campos estão aqui à vista'}
        </p>
        <div className="mt-3 flex flex-col gap-3">
          <Campo placeholder="Título (ex.: Aprovação no concurso do CESD)" value={titulo} onChange={(e) => setTitulo(e.target.value)} />
          <Campo placeholder="Data (AAAA-MM-DD)" inputMode="numeric" value={data} onChange={(e) => setData(e.target.value)} />
          <Campo placeholder="Local ou OM (opcional)" value={local} onChange={(e) => setLocal(e.target.value)} />
          <Area rows={3} placeholder="Conte essa etapa com suas palavras." value={descricao} onChange={(e) => setDescricao(e.target.value)} />
          <Botao primario onClick={salvar}>{editIndice >= 0 ? 'Salvar alterações' : 'Salvar etapa'}</Botao>
          {editIndice >= 0 && (
            <Botao onClick={() => { setEditIndice(-1); setTitulo(''); setData(''); setLocal(''); setDescricao(''); }}>
              Cancelar edição
            </Botao>
          )}
        </div>
      </Cartao>

      <h2 className="mt-4 text-[15px] font-bold text-marinha">
        Etapas <span className="ml-1 text-[12px] font-semibold text-tinta">{etapas.length}</span>
      </h2>
      <div className="mt-3 flex flex-col gap-3">
        {etapas.length === 0 && <Texto>Nenhuma etapa ainda. Use o formulário acima para começar.</Texto>}
        {etapas.map((e, i) => (
          <Cartao key={i}>
            <h3 className="text-[15px] font-bold text-marinha">{e.titulo}</h3>
            {(e.data || e.local) && <p className="mt-0.5 text-[12.5px] text-tinta">{[e.data, e.local].filter(Boolean).join(' • ')}</p>}
            {e.descricao && <Texto className="mt-1">{e.descricao}</Texto>}
            <div className="mt-3 flex gap-2">
              <button onClick={() => editar(i)} className="flex-1 rounded-xl bg-claro px-3 py-2 text-[13px] font-semibold text-marinha">Editar</button>
              <button onClick={() => remover(i)} className="flex-1 rounded-xl bg-[#fde7ea] px-3 py-2 text-[13px] font-semibold text-[#B00020]">Remover</button>
            </div>
          </Cartao>
        ))}
      </div>
    </div>
  );
}
