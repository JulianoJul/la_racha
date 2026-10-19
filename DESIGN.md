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
- Fila con emoji y nombre, constancia en una segunda línea y días de racha en una tercera, con check independiente. Tocar información abre detalle. La tarjeta de progreso muestra «Hoy» sin el mensaje «Un día cada vez».
- Lista desplazable con señal visible cuando hay contenido fuera del viewport. Puede ser una barra/indicador discreto; su visibilidad debe responder al desbordamiento y no tapar controles.
- Última fila accesible por encima de acciones fijas e insets. Evitar un indicador permanente cuando todo cabe.

### Editor
- En Nuevo hábito, enfocar nombre y mostrar teclado una vez al entrar.
- Mantener accesibles los campos/acciones con el teclado abierto; preservar entrada ante validación.
- Las cuatro modalidades están en una sola fila con scroll horizontal: «Diario», «Días fijos», «Cada X días», «Días por semana». En la cuota semanal, mostrar debajo «Días distintos por semana (1–7)» para explicar su objetivo. Ejemplos: «Lun, mié y vie», «Cada 3 días», «3 días por semana».
- Calendar solo se abre desde la cabecera de Hoy, no desde Ajustes. Sus cuatro opciones de recordatorio se distribuyen en dos filas de dos botones de igual ancho, sin huecos ni opciones fuera de pantalla.

### Detalle e historial
- Score destacado con etiqueta «Constancia»; rachas actual/mejor diferenciadas.
- Frecuencia y avance de cuota claramente separados de racha y score.
- Fechas pasadas editables, incluidas las anteriores a creación al implementar la ampliación. Futuras sin acción de completar.
- Dar feedback al guardar o ante error, sin saltar de mes o perder posición innecesariamente.

### Calendar
- Mostrar la frecuencia que se va a exportar y la primera aparición ajustada. En cuotas, seleccionar exactamente N días en controles que se distribuyen según el ancho; explicar que son solo planificación, no días obligatorios para el hábito.
- Al editar una frecuencia exportada, preguntar «¿Actualizar también Calendar?» y advertir que se cambia la serie completa, incluidas apariciones pasadas. Ofrecer «Mantener Calendar» sin modificar los eventos.
- Si la frecuencia del hábito y la planificación guardada difieren, mostrar el aviso y permitir actualizar desde Calendar con confirmación. Los campos cargan los valores del snapshot guardado; una recomposición ordinaria no debe sobrescribir la edición ni cancelar una publicación en curso.

### Widgets
- Individual 2×2: emoji/nombre, carrusel de fechas con hoy grande en el centro, vecinos a ambos lados y una métrica positiva opcional. Priorizar círculos legibles sobre ver los siete días. No añadir otro check separado.
- Individual con solo dos acciones: fondo general para detalle y hoy para completar; el nombre no es otro enlace independiente.
- Hoy azul; resto gris; completados con check recortado transparente. Métrica: `🔥 N` si N>0, en otro caso porcentaje positivo, o sin métrica.
- Lista: filas compactas y desplazamiento real, con espacio lateral reservado a la barra para que nunca pase sobre checks. Sin texto visible «nombre: pendiente/completado» junto al check; conservar nombre/estado como descripción accesible.
- La lista sustituye el checkbox por una tira de hasta siete fechas: inicial arriba, anteriores completados grises y hoy azul al final. Reglas por frecuencia en `WIDGETS.md`; las tiras largas van debajo del nombre, sin comprimir su texto ni el área de toque de hoy. Mantener 24dp de margen interior a la derecha para la barra.
- Fondo y espacios vacíos de la lista abren la app; información de cada hábito abre su detalle y checks/«Editar» conservan sus acciones propias.
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
