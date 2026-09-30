import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react';

export function Cartao({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={'rounded-2xl bg-white p-4 shadow-md shadow-marinha/10 ' + className}>{children}</div>;
}

export function Titulo({ children }: { children: ReactNode }) {
  return <h2 className="mt-5 mb-2 text-[13px] font-bold uppercase tracking-wide text-medio">{children}</h2>;
}

export function Texto({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <p className={'text-[13px] leading-relaxed text-tinta ' + className}>{children}</p>;
}

export function Botao({ primario = false, className = '', ...resto }: ButtonHTMLAttributes<HTMLButtonElement> & { primario?: boolean }) {
  const base = 'w-full rounded-xl px-4 py-3 text-[14px] font-semibold transition active:scale-[0.99] ';
  return (
    <button
      {...resto}
      className={base + (primario
        ? ' bg-marinha text-white hover:bg-[#123079] '
        : ' bg-claro text-marinha hover:bg-[#d8e8fa] ') + className}
    />
  );
}

export function Campo(props: InputHTMLAttributes<HTMLInputElement>) {
  const { className = '', ...resto } = props;
  return (
    <input
      {...resto}
      className={'w-full rounded-xl border border-[#C7D4E6] bg-white px-3 py-3 text-[14px] text-marinha '
        + 'placeholder:text-[#8A9BB5] focus:border-medio focus:outline-none ' + className}
    />
  );
}

export function Area(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const { className = '', ...resto } = props;
  return (
    <textarea
      {...resto}
      className={'w-full rounded-xl border border-[#C7D4E6] bg-white px-3 py-3 text-[14px] text-marinha '
        + 'placeholder:text-[#8A9BB5] focus:border-medio focus:outline-none ' + className}
    />
  );
}

/** Moldura VERTICAL (retrato 3:4), CENTRALIZADA no sentido horizontal. */
export function Moldura({ children, aoTocar, marcador }: { children?: ReactNode; aoTocar: () => void; marcador: string }) {
  return (
    <div className="flex justify-center">
      <div
        onClick={aoTocar}
        className="relative mt-1.5 aspect-[3/4] w-[62%] max-w-[300px] cursor-pointer overflow-hidden
                   rounded-xl border border-[#9DB8D9] bg-[#DCE9F8] p-1"
      >
        {children == null && (
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center whitespace-pre-line
                           p-3 text-center text-[13px] leading-snug text-[#6B84A3]">{marcador}</span>
        )}
        {children}
      </div>
    </div>
  );
}
