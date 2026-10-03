import { useEffect, useRef, useState } from 'react';
import type { PropsTela } from '../App';
import { Area, Botao, Campo, Cartao, Moldura } from '../componentes/ui';
import * as cofre from '../lib/cofre';
import { bitmapComExif, girarBase64, paraBase64Jpeg } from '../lib/imagens';

export default function TelaCadastro({ avisar }: PropsTela) {
  const [nomeCompleto, setNome] = useState('');
  const [nomeGuerra, setGuerra] = useState('');
  const [especialidade, setEspecialidade] = useState('');
  const [om, setOm] = useState('');
  const [foto, setFoto] = useState('');
  const [carregando, setCarregando] = useState(true);
  const arquivoRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    cofre.lerDados().then((d) => {
      const c = d.cadastro;
      setNome(c.nomeCompleto ?? '');
      setGuerra(c.nomeGuerra ?? '');
      setEspecialidade(c.especialidade ?? '');
      setOm(c.om ?? '');
      setFoto(c.foto ?? '');
      setCarregando(false);
    }).catch(() => setCarregando(false));
  }, []);

  async function persistir(campos: { nomeCompleto?: string; nomeGuerra?: string; especialidade?: string; om?: string; foto?: string }) {
    const dados = await cofre.lerDados();
    dados.cadastro = {
      nomeCompleto: campos.nomeCompleto ?? nomeCompleto,
      nomeGuerra: campos.nomeGuerra ?? nomeGuerra,
      especialidade: campos.especialidade ?? especialidade,
      om: campos.om ?? om,
      foto: campos.foto !== undefined ? campos.foto : foto,
    };
    await cofre.salvarDados(dados);
  }

  async function aoEscolherFoto(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0];
    e.target.value = '';
    if (!arquivo) return;
    try {
      const bitmap = await bitmapComExif(arquivo);
      const base64 = await paraBase64Jpeg(bitmap, 1280);
      setFoto(base64);
      await persistir({ foto: base64 });
      avisar('Foto ajustada automaticamente ✓ (a rotação da câmera foi aplicada)');
    } catch {
      avisar('Não foi possível abrir a foto.', false);
    }
  }

  async function girar() {
    if (!foto) { avisar('Insira a foto primeiro.', false); return; }
    try {
      const nova = await girarBase64(foto, 270);
      setFoto(nova);
      await persistir({ foto: nova });
      avisar('Foto girada ✓ (fica gravada girada)');
    } catch {
      avisar('Não consegui girar essa foto.', false);
    }
  }

  async function salvar() {
    try {
      if (nomeCompleto.trim().length < 3) { avisar('Informe o nome completo.', false); return; }
      await persistir({ nomeCompleto: nomeCompleto.trim(), nomeGuerra: nomeGuerra.trim(), especialidade: especialidade.trim(), om: om.trim() });
      avisar('Cadastro salvo ✓ criptografado neste aparelho.');
    } catch {
      avisar('Não foi possível salvar.', false);
    }
  }

  return (
    <div className="mt-3">
      <Cartao>
        <Moldura marcador={'📷\nToque para\ninserir a foto'} aoTocar={() => arquivoRef.current?.click()}>
          {foto && <img src={'data:image/jpeg;base64,' + foto} alt="Fotografia" className="h-full w-full rounded-lg object-cover" />}
        </Moldura>
        <input ref={arquivoRef} type="file" accept="image/*" className="hidden" onChange={aoEscolherFoto} />
        <p className="mt-3 text-center text-[12px] text-tinta">
          Toque na moldura para escolher a fotografia (JPG). É salva criptografada com a sua senha.
        </p>
      </Cartao>

      <Cartao className="mt-4">
        <div className="flex flex-col gap-3">
          <Campo placeholder="Nome completo" value={nomeCompleto} onChange={(e) => setNome(e.target.value)} />
          <Campo placeholder="Nome de guerra" value={nomeGuerra} onChange={(e) => setGuerra(e.target.value)} />
          <Campo placeholder="Especialidade" value={especialidade} onChange={(e) => setEspecialidade(e.target.value)} />
          <Campo placeholder="OM (Organização Militar)" value={om} onChange={(e) => setOm(e.target.value)} />
          <Botao primario onClick={salvar}>Salvar cadastro</Botao>
        </div>
      </Cartao>
      {carregando && <p className="mt-2 text-center text-[12px] text-tinta">Carregando…</p>}
    </div>
  );
}
