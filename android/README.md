# PanelPop — app Android nativa (Kotlin)

## Obtener el APK
1. Cada cambio en `android/` lanza GitHub Actions → pestaña **Actions** → "Build PanelPop APK" → descarga el artefacto `PanelPop-debug-apk`.
2. O abre la carpeta `android/` en Android Studio y pulsa Run.

## Cómo funciona
- `MainActivity` — permisos (superposición, notificaciones) con botón «Permitir», botón ACTIVAR TRADUCTOR y ajustes.
- `TranslatorService` — servicio en primer plano tipo `mediaProjection`:
  1. Mientras hay traducciones en pantalla solo vigila si el contenido se mueve (firma 32×32, muy barata).
  2. Si desplazas o cambias de página, las traducciones se borran al instante.
  3. Cuando la pantalla se queda quieta (ajuste «Esperar al dejar de desplazar»), hace OCR ML Kit → decide el idioma (`LangPick`: por escritura hangul/kana/kanji; el texto latino se detecta una vez por página y solo entre idiomas conocidos) → ML Kit Translate al español (local, gratis, caché) → `OverlayView`.
  4. El resultado nuevo **sustituye** al anterior y nunca se lee la propia capa de traducciones, así que no se acumulan.
- `OverlayView` — capa no táctil (los toques pasan a la app de cómics). En modo «Cubrir» usa el color real del globo; en modo «Etiqueta» muestra un chip oscuro debajo del texto.
- Burbuja flotante: tocar = pausar/reanudar, arrastrar = mover (se pega al borde), mantener = cerrar. Notificación con Pausar/Cerrar.
- Al girar el teléfono la captura se ajusta al nuevo tamaño.

## Biblioteca y repositorios (como Mihon)
- La pantalla principal tiene pestañas **Biblioteca · Explorar · Traductor**.
- **Explorar**: cada web de mangas que añade el usuario es un *repositorio*. `SourceActivity` muestra sus series en cuadrícula y permite buscar (`?s=`).
- `SeriesActivity` — portada, ♡ Añadir a la biblioteca, lista de capítulos (leídos en gris; mantener pulsado = marcar).
- `ReaderActivity` — saca las páginas del capítulo y las muestra limpias en vertical, con Anterior/Siguiente y botón **ES** para encender el traductor.
- `BrowserActivity` — navegador interno para pasar verificaciones de la web o abrir una serie/capítulo a mano.
- `PageScraper` lee cada página en un WebView (con JS, cookies y carga perezosa); `WebParse` (Kotlin puro, con tests) decide qué son series, capítulos y páginas. `Library` guarda todo en un JSON local.
- Los capítulos salen en cuanto aparecen; los botones «Ver más» se pulsan solos (bloqueando anuncios que lleven a otra web) y también se leen los capítulos escondidos en los datos de la página. Probado con MHScans, LectorJPG, DragonTranslation, ManhwaWeb y Olympus Scan.

Las capturas solo viven en memoria y se descartan tras el OCR. No hay claves secretas.
La primera vez se descarga el modelo de traducción (~30 MB) por idioma. `LangModels` descarga siempre en segundo plano (nunca bloquea el traductor); la pantalla principal tiene la tarjeta «Idiomas descargados» para bajarlos antes con Wi‑Fi.

## Pruebas
`gradle :app:testDebugUnitTest` ejecuta las reglas de idioma, texto y lectura de webs (`src/test`). GitHub Actions las corre antes de crear el APK.
