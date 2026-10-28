# Finanzas

App Android personal que lee los SMS de Bancolombia y Banco Caja Social, registra cada movimiento y da presupuestos, alertas, metas y analítica mes a mes. Todo queda en el teléfono (SQLite), sin servidores.

## Publicar y compilar

1. Crea el repo `Kharlos2/finanzas` en GitHub (vacío).
2. Desde esta carpeta:
   ```
   git remote add origin https://github.com/Kharlos2/finanzas.git
   git push -u origin main
   ```
3. GitHub Actions corre las pruebas del lector de SMS, compila y publica `finanzas.apk` en *Releases*. Cada push genera una versión nueva que se instala encima sin borrar datos (firma fija en `app/finanzas.keystore`).

## Primer uso

1. Instala el APK y concede **SMS** y **Notificaciones**.
   Si Android muestra "Ajuste restringido": Ajustes › Apps › Finanzas › ⋮ › *Permitir ajustes restringidos*, y vuelve a concederlos.
2. Ajustes › **Importar SMS antiguos** (3 o 6 meses) para tener historial desde el primer día.
3. Ajustes › **Presupuestos**: pon un tope total y por categoría. Avisa al 80% y al pasar el 100%.

## Qué entiende

| Banco | SMS | Se registra como |
|---|---|---|
| Bancolombia | Compraste … con tu T.Deb | Gasto, categoría por comercio |
| Bancolombia | Transferiste … a la cuenta | Gasto · Transferencias |
| Bancolombia | Recargaste … a Tarjeta Civica / Saldo | Transporte / Servicios |
| Bancolombia | Pagaste … a EMPRESAS PUBLICAS | Servicios |
| Bancolombia | Retiraste … | Efectivo |
| Bancolombia | Recibiste un pago de Nomina | Ingreso · Salario |
| Caja Social | TX EXITOSA … COMPRA CON TARJETA DE CREDITO | Gasto, categoría por comercio |
| Caja Social | TX REVERSADA | Anula la compra original |
| Caja Social | Su pago por … para TARJETA DE CREDITO | Pago tarjeta (no cuenta como gasto) |

Un SMS bancario con formato nuevo no se pierde: se guarda marcado con ⚠️ para que lo revises.

**Pago de tarjeta**: las compras con la tarjeta de crédito ya cuentan como gasto cuando llegan, así que el pago de la tarjeta queda en la categoría *Pago tarjeta*, que no suma en los análisis para no contar doble.

## Categorías y reglas

Orden de decisión: tus reglas › tipo de operación (retiro, nómina, pago TC) › palabras clave predeterminadas › por defecto.
Al cambiar la categoría de un movimiento puedes marcar "aplicar siempre": crea una regla y recategoriza los anteriores. Ejemplo: una transferencia a tu propio Nequi (`3017413746`) → *Entre mis cuentas*, que no cuenta como gasto.

## Registro manual

Botón **+** en Resumen o Movimientos. Para los pagos de Caja Social que no llegan por SMS.

## Respaldo

Ajustes › Exportar CSV. Si desinstalas la app los datos se borran.

## Código

- `parser/SmsParser.kt` — lectura de los SMS (con pruebas en `app/src/test`)
- `domain/` — modelos, categorización y cálculos (sin Android, probado)
- `data/Db.kt`, `data/Repo.kt` — SQLite, ingestión, alertas y notificaciones
- `SmsReceiver.kt` — recibe los SMS en vivo
- `ui/` — pantallas en Jetpack Compose
