import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "PanelPop — Traductor flotante de cómics para Android" },
      {
        name: "description",
        content:
          "Traduce mangas, manhwas y cómics al español sobre cualquier app Android, con lectura de texto y traducción en el propio teléfono.",
      },
      { property: "og:title", content: "PanelPop — Traductor flotante de cómics" },
      {
        property: "og:description",
        content: "Lee mangas y manhwas en español sobre tu app favorita, sin pagar ni subir archivos.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

const steps = [
  { t: "Descarga la app", d: "En GitHub: Actions → «Build PanelPop APK» → descarga «PanelPop-debug-apk»." },
  { t: "Permite la superposición", d: "Pulsa «Permitir» en la app y activa «Mostrar sobre otras apps»." },
  { t: "Activa el traductor", d: "Acepta la captura de pantalla completa y abre tu app de cómics." },
  { t: "Lee en español", d: "Al desplazar, las traducciones se esconden; al parar, aparecen las nuevas." },
];

const features = [
  { k: "Biblioteca", t: "Tus webs como repositorios", d: "Añade tu web de mangas, guarda series y léelas sin anuncios." },
  { k: "Privado", t: "Todo en tu teléfono", d: "La lectura y la traducción funcionan sin servidores ni cuentas." },
  { k: "Al día", t: "Siempre en su sitio", d: "Las traducciones siguen al cómic y nunca se amontonan." },
  { k: "A medida", t: "Se funde con el globo", d: "Toma el color del globo original y ajusta la letra sola." },
];

const bubble = [
  { k: "Toca", v: "pausar o reanudar" },
  { k: "Arrastra", v: "moverla" },
  { k: "Mantén", v: "cerrar" },
];

function PhoneMock() {
  return (
    <div className="relative mx-auto w-[260px] rounded-[2.4rem] border border-border bg-card p-3 shadow-phone">
      <div className="mx-auto mb-2 h-1.5 w-16 rounded-full bg-muted" />
      <div className="halftone relative aspect-[9/16] overflow-hidden rounded-[1.8rem] bg-paper">
        {/* viñetas */}
        <div className="absolute inset-3 grid grid-rows-3 gap-2">
          <div className="rounded-md border-2 border-ink/80" />
          <div className="grid grid-cols-2 gap-2">
            <div className="rounded-md border-2 border-ink/80" />
            <div className="rounded-md border-2 border-ink/80" />
          </div>
          <div className="rounded-md border-2 border-ink/80" />
        </div>
        {/* globos traducidos */}
        <div className="animate-pop absolute left-7 top-8 max-w-[150px] rounded-2xl border-2 border-ink bg-paper px-3 py-2 text-center text-[11px] font-bold leading-tight text-ink">
          ¡No pienso rendirme ahora!
        </div>
        <div
          className="animate-pop absolute right-6 top-[44%] max-w-[90px] rounded-2xl border-2 border-ink bg-paper px-2 py-1.5 text-center text-[10px] font-bold leading-tight text-ink"
          style={{ animationDelay: "0.4s" }}
        >
          ¿Quién anda ahí?
        </div>
        <div
          className="animate-pop absolute bottom-10 left-6 max-w-[170px] rounded-2xl border-2 border-ink bg-paper px-3 py-2 text-center text-[11px] font-bold leading-tight text-ink"
          style={{ animationDelay: "0.8s" }}
        >
          Mañana empieza el torneo.
        </div>
        {/* burbuja flotante */}
        <div className="absolute right-3 top-3 grid h-11 w-11 place-items-center rounded-full border-2 border-primary-foreground bg-gradient-primary font-display text-xs font-extrabold text-primary-foreground shadow-glow">
          ES
        </div>
      </div>
    </div>
  );
}

function Index() {
  return (
    <main className="relative min-h-screen overflow-hidden bg-background text-foreground">
      <div className="bg-glow pointer-events-none absolute inset-x-0 top-0 h-[520px]" />

      <section className="relative mx-auto grid max-w-5xl items-center gap-12 px-6 pb-16 pt-14 md:grid-cols-[1.1fr_1fr] md:pt-24">
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs font-bold text-muted-foreground">
            <span className="h-2 w-2 rounded-full bg-success" /> App Android · gratis
          </span>
          <h1 className="mt-6 text-5xl font-extrabold leading-[0.95] sm:text-7xl">
            Lee cualquier cómic{" "}
            <span className="bg-gradient-primary bg-clip-text text-transparent">en español.</span>
          </h1>
          <p className="mt-5 max-w-md text-lg text-muted-foreground">
            PanelPop traduce mangas, manhwas y cómics encima de la app que ya usas. Sin subir archivos, sin anuncios y
            sin suscripciones.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a
              href="#instalar"
              className="inline-flex items-center rounded-2xl bg-gradient-primary px-6 py-3.5 font-bold text-primary-foreground shadow-glow transition-transform hover:-translate-y-0.5"
            >
              Cómo instalarla
            </a>
            <a
              href="#burbuja"
              className="inline-flex items-center rounded-2xl border border-border bg-card px-6 py-3.5 font-bold text-foreground transition-colors hover:bg-secondary"
            >
              Ver cómo se usa
            </a>
          </div>
        </div>
        <PhoneMock />
      </section>

      <section className="relative mx-auto max-w-5xl px-6 pb-8">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {features.map((f) => (
            <div key={f.k} className="rounded-3xl border border-border bg-card p-6">
              <span className="font-display text-sm font-extrabold uppercase tracking-widest text-primary">{f.k}</span>
              <h3 className="mt-3 text-xl font-extrabold">{f.t}</h3>
              <p className="mt-1.5 text-sm text-muted-foreground">{f.d}</p>
            </div>
          ))}
        </div>
      </section>

      <section id="instalar" className="relative mx-auto max-w-5xl px-6 py-16">
        <h2 className="text-3xl font-extrabold sm:text-4xl">Empieza en 4 pasos</h2>
        <ol className="mt-8 grid gap-4 sm:grid-cols-2">
          {steps.map((s, i) => (
            <li key={s.t} className="flex gap-4 rounded-3xl border border-border bg-card p-5">
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-gradient-primary font-display text-lg font-extrabold text-primary-foreground">
                {i + 1}
              </span>
              <div>
                <h3 className="font-bold">{s.t}</h3>
                <p className="mt-0.5 text-sm text-muted-foreground">{s.d}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section id="burbuja" className="relative mx-auto max-w-5xl px-6 pb-20">
        <div className="rounded-[2rem] border border-border bg-secondary p-8 sm:p-10">
          <h2 className="text-3xl font-extrabold">La burbuja flotante</h2>
          <p className="mt-2 text-muted-foreground">Siempre a mano, sin tapar los botones de tu app de lectura.</p>
          <div className="mt-6 flex flex-wrap gap-3">
            {bubble.map((b) => (
              <div key={b.k} className="flex items-center gap-3 rounded-2xl bg-card px-4 py-3">
                <span className="rounded-lg bg-muted px-2.5 py-1 text-sm font-bold text-primary">{b.k}</span>
                <span className="text-sm">{b.v}</span>
              </div>
            ))}
          </div>
        </div>
        <p className="mt-10 text-center text-xs text-muted-foreground">
          Las capturas solo viven en memoria y se descartan al instante. La primera vez se descarga cada idioma (~30 MB).
        </p>
      </section>
    </main>
  );
}
