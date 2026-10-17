# SCORE.md — Constancia, fechas y rachas

## Contrato y estado
Este documento es la fuente de las reglas de cálculo. La fórmula **diaria** ya existe; el inicio histórico ampliado y las frecuencias no diarias están acordados pero pendientes de implementación. Las decisiones no aprobadas se identifican al final.

La implementación es original. El suavizado exponencial es una referencia conceptual; no se copia código de Loop Habit Tracker (GPLv3) ni se afirma reproducir su algoritmo completo.

## 1. Entradas y calendario
- `today`: fecha local obtenida de un `Clock` inyectable y la zona actual del dispositivo.
- `createdOn`: fecha de creación, preservada aunque se registre historia anterior.
- `completedDays`: conjunto de fechas locales únicas persistidas; no un contador de taps.
- `trackingStart = min(createdOn, primera marca válida existente)`; si no hay marcas, usar creación. Esta ampliación está pendiente en código.
- Una fecha válida de completado no está en el futuro. La API de escritura debe rechazar fechas futuras; el cálculo no debe premiarlas si recibe datos inválidos.
- Los días avanzan como fechas (`plusDays`), no como bloques de 24 horas. Semanas locales de lunes a domingo, incluidas las que cruzan mes/año.
- Un cambio de zona modifica «hoy», no las claves de marcas ya guardadas.

No hay faltas anteriores a `trackingStart`. Al borrar la marca más antigua, se deriva de nuevo este inicio y se recalcula. La fecha de referencia de un intervalo de N días es un dato diferente: añadir una marca antigua no debe desplazar silenciosamente su calendario.

## 2. Fórmula diaria
Puntuación interna `S` en `[0,1]`, inicializada a cero antes de `trackingStart`:

```text
a = 0.5^(1/13) ≈ 0.9480775
update(S, x) = a*S + (1-a)*x
x = 1 si se completó; 0 si no
porcentaje = round(100*S)
```

1. Recorrer todos los días cerrados desde el inicio hasta ayer, en orden, aplicando `update`.
2. Si hoy está completado, aplicar una actualización con `x=1`.
3. Si hoy está pendiente, devolver el valor hasta ayer: **no aplicar `x=0` todavía**.
4. Al desmarcar hoy, recomputar el valor hasta ayer, sin castigo adicional.
5. Redondear solo al presentar. Mantener precisión `Double` durante todo el cálculo y un resultado finito en rango.

La implementación diaria actual parte de creación; al ampliar el historial deberá partir de `trackingStart`. Se debe conservar el resultado de los hábitos que no tienen marcas anteriores.

### Ejemplos de referencia
Para `n` días perfectos, `S = 1 - a^n`.

| Historial evaluado | Porcentaje aproximado |
|---|---:|
| Sin éxitos | 0 % |
| 7 días perfectos | 31 % |
| 13 días perfectos | 50 % |
| 30 días perfectos | 80 % |
| 60 días perfectos | 96 % |
| 30 perfectos + 1 falta cerrada | 76 % |

Trece faltas diarias cerradas reducen a la mitad el valor previo. Un día pendiente no es una falta cerrada. Ninguna de estas cifras describe todavía hábitos semanales.

## 3. Rachas: días consecutivos reales
- **Actual**: longitud de la secuencia terminada hoy si hoy está marcado; si no, la terminada ayer. Si ayer tampoco está marcado, cero.
- **Mejor**: mayor secuencia de fechas consecutivas marcadas en el historial válido.
- No usar score redondeado para calcular rachas. Desmarcar una fecha puede dividir una secuencia.
- La definición sigue siendo la misma para hábitos no diarios. Lunes/miércoles/viernes puede cumplir perfectamente su frecuencia y no tener una racha de tres días.
- No hay protectores ni días omitidos que mantengan la secuencia. Un día no programado no penaliza el **score**, pero tampoco inventa un día completado para la **racha**.

La interfaz debe explicar esta diferencia. En el widget individual, mostrar racha positiva o, si es cero, constancia positiva; ver `WIDGETS.md`.

## 4. Frecuencias acordadas, pendientes de implementación
| Modalidad | Unidad de cumplimiento | Regla confirmada |
|---|---|---|
| Diario | Día local | Fórmula de la sección 2 |
| Días fijos | Fecha cuyo día semanal está seleccionado | No penalizar fechas fuera de la selección |
| Cada N días | Fecha congruente con una referencia local módulo N | No penalizar días intermedios; N entero positivo |
| N veces por semana | N fechas distintas dentro de lunes–domingo | No penalizar la semana abierta antes de su cierre |

En cuotas, varias pulsaciones el mismo día aportan como máximo una fecha. Un objetivo de 3/semana no obliga a completar lunes/martes/miércoles ni convierte el resto de días en fallos.

### Diseño de score aún por cerrar
No basta con añadir un selector de frecuencia y reutilizar el bucle diario. Antes de implementar, fijar con ejemplos y pruebas:

| Decisión | Qué debe quedar definido |
|---|---|
| Ponderación | Si la velocidad de crecimiento/decadencia se mide por oportunidades programadas o por tiempo transcurrido; qué significa la semivida para cada modalidad |
| Cuota semanal | Cómo contribuyen cumplimiento parcial, objetivo alcanzado y exceso; cómo se refleja progreso en la semana abierta sin penalización anticipada |
| Semana inicial parcial | Objetivo aplicable al empezar, por ejemplo, un viernes; incluir el inicio ampliado por marcas antiguas |
| Fuera de programación | Cómo afectan al score las marcas en un día libre o anterior a la referencia de un intervalo; siempre conservar el hecho histórico y su efecto en rachas |
| Edición de frecuencia | Desde cuándo entra en vigor y si requiere historial de reglas; no reinterpretar silenciosamente el pasado |
| Archivo/restauración | Corte del cálculo y tratamiento de intervalos archivados, coherente con el contrato de producto |

**Orientación de diseño, no fórmula aprobada:** evaluar solo oportunidades exigibles en días fijos/intervalos, y objetivos semanales en cuotas. La elección del peso de cada unidad debe conservar el caso diario y evitar que una frecuencia baja parezca un incumplimiento permanente. Estas decisiones deben cerrarse en este documento antes del código correspondiente.

## 5. Recomputación y consistencia
- Recalcular desde las fuentes al corregir historial, cambiar fecha local o restaurar una sesión. No sumar/restar score por pulsación.
- App, detalle y widgets deben usar las mismas entradas y reglas para una fecha dada.
- Room es la autoridad; una caché futura debe poder invalidarse/reconstruirse y no sustituir al historial.
- Las escrituras repetidas son idempotentes y los cálculos deterministas. Tras semanas sin abrir, evaluar todos los períodos cerrados que correspondan.
- Refrescar UI/widgets después de persistir; Android puede retrasar el refresco de un widget. No confundir la hora del refresco con la fecha de la marca.

## 6. Matriz de pruebas
Los casos de ampliación son pruebas a añadir/cambiar; no se consideran cubiertos por el recuento histórico de `README.md`.

| Área | Casos mínimos |
|---|---|
| Diario | Ejemplos numéricos, cero inicial, largos períodos sin marcas, límites `[0,1]`, sin NaN |
| Hoy | Marcar, repetir, desmarcar; pendiente no penaliza; ayer faltante sí al cerrar |
| Rachas | Hoy/ayer, huecos, mejor racha, desmarcar en medio, fechas duplicadas |
| Inicio histórico | Marca anterior a creación, huecos desde ella, borrar primera marca, todas las marcas eliminadas, creación preservada |
| Calendario | Medianoche local distinta de UTC, DST en ambos sentidos, bisiesto, fin de año/semana, cambio de zona sin mover historial |
| Días fijos | Día exigible/libre, semana cruzando año, hoy pendiente |
| Intervalos | N=1 equivalente a diario, intervalos cruzando meses, referencia estable al ampliar historial |
| Cuotas | Parcial, exacta y excedida; semana abierta/cerrada; semana inicial parcial; desmarcar; una fecha no cuenta dos veces |
| Persistencia | Reabrir, pulsaciones concurrentes, corrección histórica y migración sin pérdida |

## Texto breve para la app
«Tu constancia refleja el cumplimiento acumulado de tu hábito. Cumplir tu objetivo la aumenta; dejarlo sin cumplir la reduce poco a poco. La racha cuenta días consecutivos: puede terminar sin borrar tu progreso».
