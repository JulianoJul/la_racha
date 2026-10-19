# Hallazgo para coordinación: frecuencias y Calendar

**Estado:** implementación local completada el 2026-10-18; pendiente de validación en Room Android, provider y Google Calendar. El bloqueo del 2026-10-17 era el estado anterior.

## Decisiones acordadas e implementación
- Diario: `FREQ=DAILY`.
- Días fijos: `FREQ=WEEKLY` y `BYDAY` de los días del hábito; primera aparición que cumpla esa selección.
- Cada X días: `FREQ=DAILY;INTERVAL=X`, alineando la primera aparición con la referencia original, no reiniciando el intervalo desde la fecha elegida para exportar.
- Días por semana: elegir exactamente N días en el momento de exportar. Se guardan solo como planificación de Calendar; no alteran la cuota ni las marcas.
- Preguntar al editar una frecuencia exportada si también se actualiza Calendar. Rechazar conserva la planificación anterior; aceptar modifica la serie completa con advertencia de apariciones pasadas. No se implementa una edición «solo desde ahora» ni se reescriben períodos del score.
- Room v4 guarda la recurrencia autorizada, su frecuencia de referencia y la selección de días de la cuota. Reintentos y cambios de nombre respetan ese snapshot. Las exportaciones anteriores se migran como diarias porque eso era lo que publicaban realmente.

## Evidencia y límites
`CalendarRecurrenceTest` cubre traducción y fechas; `CalendarFrequencyExportTest` cubre consentimiento, persistencia previa al efecto externo y recuperación con fakes. Esquema v4 exportado; sentencias de migración ejecutadas en SQLite aislado y pruebas instrumentadas de migración/reapertura añadidas. Resultados/comandos completos en `README.md`.

Sin dispositivo conectado, no se ejecutaron las nuevas pruebas Room ni el flujo visual/provider. Siguen pendientes recurrencias reales y DST, recordatorios, permisos/revocación, edición/limpieza y sincronización con Google. No se crearon eventos personales como fixtures. Las marcas, rachas y score permanecen independientes de Calendar.
