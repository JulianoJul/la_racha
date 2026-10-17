# Hallazgo para coordinación: frecuencias y Calendar

**Estado:** bloqueado/no soportado para frecuencias no diarias (2026-10-17).

La exportación Calendar actual se diseñó para planificar hábitos diarios y sus archivos están bajo ownership del agente Calendar. El modelo de dominio y Room ahora conservan Diario, Días fijos, Cada N días y Cuota semanal; no se han cambiado el paquete `calendar/`, `CalendarIntegrationScreen`, `CalendarDao`, `CalendarEntities`, `CalendarMigration`, `CalendarWorkScheduler` ni `CalendarExportWorker` para traducir estas modalidades.

Hasta que el owner Calendar acuerde e implemente una traducción explícita, la exportación no debe interpretarse como compatible con una frecuencia no diaria. En particular, no convertir cuotas sin días fijados en series diarias ni asumir que la racha/score afecta a eventos. Antes de habilitarlo, revisar creación y edición de planes, cambios de frecuencia, cancelación/reconciliación de series existentes y permisos/consentimiento. Las marcas e historial siguen siendo independientes de Calendar.
