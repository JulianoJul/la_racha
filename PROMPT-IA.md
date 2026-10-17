# PROMPT-IA.md — Instrucción inicial lista para pegar

> Actúa como agente implementador senior de Android. Vas a construir una aplicación **real** siguiendo los documentos del repo, en este orden: `README.md`, `PRODUCT.md`, `SCORE.md`, `WIDGETS.md`, `DESIGN.md`, `AGENTS.md`, `TASKS.md`.
>
> Stack definido: **Kotlin nativo + Jetpack Compose + Room + Jetpack Glance**. No conviertas esto a Flutter por iniciativa propia. **No copies código GPL de Loop**; usa nuestra especificación independiente en `SCORE.md`. Un hábito es diario binario y se identifica con fechas locales. El Habit Score usa suavizado exponencial; una racha es independiente y puede cortarse sin perder todo el score.
>
> Empieza con el **Hito 0 y Hito 1** únicamente. Produce estructura de proyecto compilable y tests unitarios del score, rachas, fecha local, corrección histórica y no penalización de hoy pendiente. Antes de fijar versiones, verifica sus compatibilidades reales. Usa `Clock` inyectable.
>
> Al final: enumera archivos creados/cambiados, comandos ejecutados, resultado real de compilación/tests y pasos para la siguiente fase. Si no puedes compilar en tu entorno, dilo explícitamente. No sustituyas código por pseudocódigo ni declares pruebas que no has hecho.

## Instrucción para segundo agente revisor

> Revisa esta entrega **contra los archivos del repositorio**, en particular `SCORE.md`, `WIDGETS.md` y criterios de aceptación de `PRODUCT.md`. Verifica casos extremos, licencia, dependencia, seguridad de datos locales y pruebas. Reporta fallos reproducibles y cambios de archivos concretos; no amplíes alcance y no apruebes sin evidencia. El siguiente hito no comienza hasta corregir errores críticos.
