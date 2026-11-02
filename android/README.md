# PanelPop — app Android nativa (Kotlin)

## Obtener el APK
1. Cada cambio en `android/` lanza GitHub Actions → pestaña **Actions** → "Build PanelPop APK" → descarga el artefacto `PanelPop-debug-apk`.
2. O abre la carpeta `android/` en Android Studio y pulsa Run.

## Cómo funciona
- `MainActivity` — permisos (superposición, notificaciones) con botón «Permitir», botón ACTIVAR TRADUCTOR y ajustes.
- `TranslatorService` — servicio en primer plano tipo `mediaProjection`:
  1. Mientras hay traducciones en pantalla solo vigila si el contenido se mueve (firma 32×32, muy barata).
  2. Si desplazas o cambias de página, las traducciones se borran al instante.
  3. Cuando la pantalla se queda quieta (ajuste «Esperar al dejar de desplazar»), hace OCR ML Kit → identifica el idioma → ML Kit Translate al español (local, gratis, caché) → `OverlayView`.
  4. El resultado nuevo **sustituye** al anterior y nunca se lee la propia capa de traducciones, así que no se acumulan.
- `OverlayView` — capa no táctil (los toques pasan a la app de cómics). En modo «Cubrir» usa el color real del globo; en modo «Etiqueta» muestra un chip oscuro debajo del texto.
- Burbuja flotante: tocar = pausar/reanudar, arrastrar = mover (se pega al borde), mantener = cerrar. Notificación con Pausar/Cerrar.
- Al girar el teléfono la captura se ajusta al nuevo tamaño.

Las capturas solo viven en memoria y se descartan tras el OCR. No hay claves secretas.
La primera vez se descarga el modelo de traducción (~30 MB) por idioma.
