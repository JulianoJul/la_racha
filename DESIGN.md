# DESIGN.md — Sistema visual e interacción

## Dirección
La Racha debe sentirse sencilla, tranquila y rápida. Material 3 como base, lenguaje claro en español y jerarquía estable. Sin confeti, cadenas rotas, avisos culpabilizantes ni animaciones que retrasen completar.

Este documento define presentación y accesibilidad. Comportamiento en `PRODUCT.md`, matemáticas en `SCORE.md` y contrato de widgets en `WIDGETS.md`. Los cambios solicitados más recientes son objetivos pendientes, no pantallas ya entregadas.

## Jerarquía
1. Qué toca hacer y su estado.
2. Acción de completar/desmarcar.
3. Constancia acumulada, expresada en porcentaje.
4. Racha secundaria, expresada en días consecutivos.

El progreso de hoy y el objetivo semanal tienen etiquetas propias. Nunca presentar un «3/5 hoy» o «2/3 esta semana» como porcentaje de constancia.

## Paleta y tema
Tokens principales del tema existente (`ui/theme/Theme.kt`); no representan una nueva paleta por implementar:

| Rol Material | Claro | Oscuro |
|---|---|---|
| `background` | `#F4F5F1` | `#19241C` |
| `onBackground` | `#26322A` | `#F4F7F2` |
| `surface` | `#FFFFFF` | `#26392D` |
| `onSurface` | `#26322A` | `#F4F7F2` |
| `primary` | `#437A54` | `#92D9A5` |
| `onPrimary` | `#FFFFFF` | `#163720` |
| `onSurfaceVariant` | `#526056` | `#B6CDBB` |
| `outline` | `#C7CDC5` | `#64776B` |
| `error` | `#B34741` | `#FFA19A` |

Indicadores semanales del widget: azul `#2563EB` / `#8AB4FF` para hoy; gris `#6B7280` / `#9CA3AF` para otros días. Los colores de hábito son acentos, no sustitutos del color de texto legible.

- App: Claro/Oscuro/Sistema según preferencia. Widget: colores compatibles con el contexto del launcher; no asumir que conoce el tema forzado de la app.
- Pintar una raíz Material (`Surface`/`Scaffold`) con fondo y color de contenido; `MaterialTheme` por sí solo no pinta el fondo.
- Respetar insets de sistema y teclado. Calendar usa la misma raíz/tema que el resto de pantallas.
- El tema debe ser coherente **desde la ventana inicial/splash hasta el contenido**. Corregir el destello blanco también con arranque frío desde widget y preferencia Oscuro sobre un sistema claro; no basta con ajustar la pantalla después de cargar DataStore.
- Comprobar barras del sistema, primer frame y transición sin introducir una espera artificial.

## Tipografía, espacio y accesibilidad
- Fuente del sistema y roles tipográficos Material. Referencias: encabezados 26–30sp, nombre de hábito 16sp, secundarios 12–14sp; adaptar al espacio sin recortar información esencial.
- Escala de espaciado: 4/8/12/16/24/32dp. Radios orientativos: tarjetas 16dp, widgets 20dp cuando el host lo admita.
- Objetivo de toque 48×48dp. Separar área visual de área interactiva cuando el icono sea pequeño y comprobar que no se solapen acciones.
- Soportar fuentes grandes, nombres largos y navegación TalkBack. Un emoji no sustituye al nombre accesible.
- Texto normal con contraste objetivo mínimo 4,5:1; texto grande y controles significativos, 3:1. Verificar combinaciones reales, incluidos colores de hábito; no dar la paleta por auditada por figurar en una tabla.
- Estado completado visible por check/etiqueta además de color. No depender de un contorno tenue para una acción importante.

## Componentes y flujos
### Hoy
- Cabecera con fecha/progreso y accesos Calendar y Ajustes juntos, identificables por TalkBack.
- Fila con emoji, nombre, métricas secundarias y check independiente. Tocar información abre detalle.
- Lista desplazable con señal visible cuando hay contenido fuera del viewport. Puede ser una barra/indicador discreto; su visibilidad debe responder al desbordamiento y no tapar controles.
- Última fila accesible por encima de acciones fijas e insets. Evitar un indicador permanente cuando todo cabe.

### Editor
- En Nuevo hábito, enfocar nombre y mostrar teclado una vez al entrar.
- Mantener accesibles los campos/acciones con el teclado abierto; preservar entrada ante validación.
- Mostrar solo los controles necesarios para la frecuencia elegida. Usar ejemplos comprensibles: «Lun, mié y vie», «Cada 3 días», «3 veces por semana».

### Detalle e historial
- Score destacado con etiqueta «Constancia»; rachas actual/mejor diferenciadas.
- Frecuencia y avance de cuota claramente separados de racha y score.
- Fechas pasadas editables, incluidas las anteriores a creación al implementar la ampliación. Futuras sin acción de completar.
- Dar feedback al guardar o ante error, sin saltar de mes o perder posición innecesariamente.

### Widgets
- Individual 2×2: emoji/nombre, fila semanal como único check de hoy y una métrica positiva opcional. No conservar el check grande adicional del diseño anterior.
- Hoy azul; resto gris; completados con check recortado transparente. Métrica: `🔥 N` si N>0, en otro caso porcentaje positivo, o sin métrica.
- Lista: filas compactas y desplazamiento real, con espacio lateral reservado a la barra para que nunca pase sobre checks.
- Configuración de lista: «Todos» y «Elegidos» separados por 12dp.
- Verificar áreas táctiles y restricciones reales en launcher; no asumir que un composable de app funciona igual en Glance.

## Estados que deben diseñarse
Vacío, pendiente, completado, día no programado, fecha futura, cuota parcial/cumplida, hábito archivado/borrado, widget sin configurar y error de persistencia. No dibujar «sin datos» como «0 %» si el historial todavía está cargando.

Ejemplo TalkBack: «Leer, constancia 80 por ciento, racha 12 días, pendiente hoy. Completar». El control y el enlace a detalle deben ser acciones distinguibles.

## Revisión visual por entrega
- Claro, oscuro y preferencia forzada distinta del sistema.
- Arranque frío/caliente desde icono y widgets, incluyendo entrada desde Ajustes/Calendar.
- Nombre largo, fuente grande, teclado abierto, lista vacía y lista que excede pantalla.
- Widget redimensionado, hoy pendiente/completo, métrica visible/oculta y barra de scroll durante el gesto.
- Registrar dispositivo, capturas útiles y límites; una captura no valida acciones, persistencia o accesibilidad.

Referencias opcionales de revisión: [Impeccable](https://impeccable.style/) y [Awesome DESIGN.md](https://github.com/voltagent/awesome-design-md). No son dependencias del proyecto ni herramientas cuya disponibilidad pueda darse por supuesta.
