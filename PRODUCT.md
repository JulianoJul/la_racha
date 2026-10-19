# PRODUCT.md — Contrato de producto

## Propósito y estado
La Racha es una app Android de hábitos, local y utilizable sin conexión, con acceso rápido desde la pantalla de inicio. Registrar una práctica debe requerir uno o dos toques.

Este documento define el comportamiento objetivo. El estado implementado y la última verificación están en `README.md`. El historial anterior y las tres frecuencias están implementados localmente; consultar la evidencia de compilación/migración antes de considerarlos verificados.

## Principios
- **Constancia (Habit Score)**: progreso acumulado por hábito, entre 0 y 100 %. Una falta lo reduce gradualmente, no lo reinicia.
- **Racha**: días naturales consecutivos completados. Es informativa y distinta de cumplir una frecuencia semanal.
- **Progreso de hoy**: estado de las tareas del día; no es el score.
- Una marca como máximo por hábito y fecha local. Sin contadores de repeticiones dentro del mismo día.
- Sin castigos visuales, protectores de racha, saltos, recompensas, anuncios ni cuentas propias.
- Historial local como autoridad. Calendar exporta únicamente planificación elegida por el usuario.

## Hábitos y frecuencias
Crear, editar nombre/emoji/color, ordenar, archivar, restaurar y eliminar hábitos. No limitar el número total de hábitos por el límite de selección de un widget.

| Frecuencia | Significado | Estado |
|---|---|---|
| Diario | Una oportunidad cada día | Implementada |
| Días fijos | Por ejemplo, lunes/miércoles/viernes; al menos un día elegido | Implementada |
| Cada X días | Intervalo entero positivo desde una fecha de referencia; X=1 equivale a diario | Implementada |
| Días por semana | Objetivo de 1 a 7 días distintos por semana, sin días fijos | Implementada |

Para frecuencias programadas, un día que no toca no es una falta. Para cuotas semanales, no evaluar un incumplimiento antes de cerrar la semana. Calendario local y semanas lunes–domingo. El cálculo, sus casos límite y las decisiones abiertas se concentran en `SCORE.md`.

No confundir frecuencia del hábito con hora/duración de un evento de Calendar. Una cuota semanal por sí sola no fija horarios que puedan exportarse.

## Historial y fechas
- Permitir marcar/desmarcar hoy y días pasados, incluidos los anteriores a la creación. No permitir marcas futuras.
- Conservar la fecha real de creación. El inicio efectivo del seguimiento es la menor entre creación y primera marca existente.
- Una marca antigua amplía el intervalo del cálculo: desde ese inicio, las oportunidades programadas sin completar cuentan como faltas una vez cerradas.
- Recalcular el inicio al eliminar la marca más antigua; nunca fabricar marcas para rellenar huecos.
- Editar historial recalcula score/rachas y actualiza pantallas y widgets afectados.
- Guardar fechas locales originales al viajar o cambiar de zona horaria. La zona actual determina qué fecha es «hoy».
- Archivar retira el hábito de las listas activas y preserva su historial; eliminar borra el hábito y sus marcas locales. Los vínculos Calendar deben tratarse según su política de exportación, no como borrado automático de eventos ajenos.

## Pantallas e interacciones
### Hoy
- Fecha, progreso diario y lista con estado, constancia y racha secundaria.
- Si el contenido excede la pantalla, ofrecer una señal visible de desplazamiento; nunca tapar el último hábito ni sus controles.
- Acceso a **Calendar junto a Ajustes** en la cabecera, con etiquetas accesibles, sin duplicarlo dentro de Ajustes. Abrir Calendar no activa la integración.
- Cada hábito abre su detalle; su check es una acción independiente.
- Antes de incorporar frecuencias, definir el denominador del resumen diario y la presentación de hábitos que no tocan hoy; ver decisiones abiertas.

### Crear/editar
- Nombre, emoji, color y frecuencia con campos específicos de su modalidad.
- Las cuatro modalidades se eligen en una única fila desplazable horizontalmente: Diario, Días fijos, Cada X días y Días por semana. El nombre de la cuota semanal no debe sugerir un intervalo fijo entre fechas; sus resúmenes indican, por ejemplo, «3 días por semana».
- Al abrir **Nuevo hábito**, enfocar nombre y mostrar el teclado automáticamente. No repetir el enfoque al recomponer o sobrescribir lo escrito.
- Validar nombre y frecuencia antes de guardar; conservar la entrada ante errores.

### Detalle
- Constancia, racha actual/mejor racha e historial editable.
- Mostrar claramente la frecuencia y, en cuotas, avance semanal separado del score.
- Al marcar una fecha anterior, explicar el nuevo inicio de seguimiento sin exigir confirmaciones repetitivas.

### Widgets y ajustes
- Individual y lista configurables por instancia; especificación completa en `WIDGETS.md`.
- Tema Claro/Oscuro/Sistema y explicación breve de constancia.
- Abrir desde un widget debe llegar al hábito correcto, incluso con la app en Ajustes/Calendar, y respetar el tema desde la transición de arranque.

## Modelo conceptual y consistencia
| Dato | Responsabilidad |
|---|---|
| Hábito | ID estable, nombre, emoji, color, creación local, archivo, orden; frecuencia pendiente de añadir |
| Marca | Clave única `(habitId, localDate)`; ausencia significa sin completar |
| Configuración de widget | `appWidgetId`, tipo, modo, IDs elegidos y orden; independiente por instancia |
| Preferencias | Tema y preferencias de integración; no duplican el historial |
| Plan/vínculos Calendar | Plan opt-in y referencias necesarias para reconciliar eventos exportados |
| Valores derivados | Inicio efectivo, score, rachas, semana visible y resúmenes; recalculables |

Este es un modelo de responsabilidades, no una orden de crear tablas con esos nombres. Room mantiene hábitos e historial; DataStore puede mantener configuración de widgets y preferencias.

Toda escritura usa una operación idempotente y transaccional. Tras el commit se refrescan observadores y widgets. Un fallo de refresco no revierte una marca persistida. El cierre de día se detecta al abrir/interactuar y mediante respaldo permitido por Android, sin garantía de ejecución exacta a medianoche.

## Calendar y privacidad
- Apagado inicialmente; solicita `READ_CALENDAR`/`WRITE_CALENDAR` al activar la función.
- Destinos: calendarios existentes de cuenta Google visibles, escribibles y con sincronización habilitada. No crear calendarios ni cuentas.
- Exportar nombre/horario solo de hábitos configurados explícitamente. No exportar completados, score ni rachas; no importar cambios externos.
- Traducción acordada: Diario → diaria; Días fijos → los días elegidos; Cada X días → intervalo anclado en su referencia original. La fecha inicial de Calendar es un límite inferior: la primera aparición se ajusta a una fecha que cumpla la regla.
- En Días por semana, el usuario elige exactamente N días concretos al exportar. Esta selección pertenece solo a la planificación de Calendar y no cambia la cuota ni el score del hábito.
- Cada planificación guarda la recurrencia autorizada. Reintentos y cambios de nombre conservan esa regla; no deducen una frecuencia nueva en segundo plano.
- Al editar una frecuencia ya exportada, preguntar si también se actualiza Calendar. Rechazar conserva los eventos y deja un aviso de frecuencia desactualizada. Aceptar actualiza la serie completa, incluidas sus apariciones pasadas, con advertencia explícita; no equivale a editar «solo desde ahora». Calendar usa la última frecuencia elegida para la planificación, no reconstruye el historial de reglas del score.
- Insertar un evento en el proveedor local no acredita su llegada a Google Calendar web. La sincronización remota depende de Android.
- Sin backend, OAuth propio ni permiso `INTERNET`. Los permisos de infraestructura aportados por bibliotecas se documentan y revisan en el manifiesto fusionado.
- Base, preferencias e IDs locales excluidos de backup/transferencia. Los eventos ya exportados pueden permanecer tras desinstalar.

## Criterios de aceptación
| Área | Resultado observable |
|---|---|
| Persistencia | Marcar/desmarcar y reabrir conserva exactamente el historial; pulsaciones repetidas no duplican fechas |
| Score diario | 30 días perfectos ≈80 %; una falta cerrada ≈76 %; hoy pendiente no baja el score |
| Historial antiguo | Marcar antes de creación amplía el seguimiento; desmarcar recalcula; futuros rechazados |
| Frecuencias | Días no programados no penalizan; cuota abierta no se considera incumplida; migración conserva hábitos diarios |
| Racha | Cuenta días naturales consecutivos, sin protectores ni confundirla con objetivos semanales |
| Widgets | Instancias independientes; completar sin Activity; navegación al ID correcto; scroll sin solapar checks |
| Inicio/tema | Sin destello blanco al abrir desde widgets en oscuro, tanto en frío como con proceso vivo |
| Hoy/editor | Scroll descubrible, Calendar junto a Ajustes y teclado al crear |
| Calendar | Opt-in y permisos respetados; completar no escribe eventos; no afirmar sincronización remota sin verificar |

Los criterios describen aceptación futura, no resultados de pruebas ya realizadas.

## Decisiones abiertas antes del código afectado
- Presentación y resumen de Hoy para cuotas y días no programados; evitar denominadores que cambien al marcar.
- Política temporal al archivar/restaurar: decidir cómo tratar ese intervalo al recalcular, sin introducir skips implícitos.

Fuera del alcance actual: valores numéricos por día, reportes avanzados, recordatorios propios, invitados/ubicaciones Calendar, IA dentro de la app y sincronización bidireccional.
