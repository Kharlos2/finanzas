package com.kharlos.finanzas.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmsParserTest {

    private val zone = ZoneId.of("America/Bogota")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private fun p(sms: String): ParsedTx = SmsParser.parse(sms, zone).also { assertNotNull("No parseó: $sms", it) }!!

    @Test fun montos() {
        assertEquals(15000000L, SmsParser.amountToCents("$150,000.00"))
        assertEquals(5840000L, SmsParser.amountToCents("$58.400,00"))
        assertEquals(5000000L, SmsParser.amountToCents("$50,000"))
        assertEquals(2000000L, SmsParser.amountToCents("20,000.00"))
        assertEquals(6433715L, SmsParser.amountToCents("$64,337.15"))
        assertEquals(458800000L, SmsParser.amountToCents("$4,588,000.00"))
        assertEquals(18174100L, SmsParser.amountToCents("$181.741,00"))
        assertEquals(30923300L, SmsParser.amountToCents("$309,233.00"))
    }

    @Test fun bancolombiaTransferencia() {
        val t = p("Bancolombia: Transferiste \$150,000.00 desde tu cuenta *2084 a la cuenta *3017413746 el 19/10/26 a las 11:45. ¿Dudas? Llamanos al 018000931987. Estamos cerca.")
        assertEquals(Kind.TRANSFERENCIA, t.kind); assertFalse(t.isIncome)
        assertEquals(15000000L, t.amountCents)
        assertEquals("Transferencia a *3017413746", t.description)
        assertEquals("Cta *2084", t.product)
        assertEquals(at(2026, 10, 19, 11, 45), t.timeMillis)

        val t2 = p("Bancolombia: Transferiste \$50,000 desde tu cuenta *2084 a la cuenta *00939170910 el 28/10/2026 a las 08:23. ¿Dudas? Llamanos al 018000931987. Estamos cerca.")
        assertEquals(5000000L, t2.amountCents); assertEquals(at(2026, 10, 28, 8, 23), t2.timeMillis)

        val t3 = p("Bancolombia: Transferiste \$20,000.00 desde tu cuenta *2084 a la cuenta *3184966628 el 24/10/26 a las 00:22. ¿Dudas? Llamanos al 018000931987. Estamos cerca.")
        assertEquals(at(2026, 10, 24, 0, 22), t3.timeMillis)
    }

    @Test fun bancolombiaRecargas() {
        val t = p("Bancolombia: Recargaste 20,000.00 a Tarjeta Civica desde tu cuenta *2084 el 25/10/2026 10:14 ¿Dudas? Llama al 018000945555 y las resolvemos.")
        assertEquals(Kind.RECARGA, t.kind); assertEquals(2000000L, t.amountCents)
        assertEquals("Recarga Tarjeta Civica", t.description); assertEquals(at(2026, 10, 25, 10, 14), t.timeMillis)

        val s = p("Bancolombia: Recargaste 4,000.00 a Saldo desde tu cuenta *2084 el 27/10/2026 19:15 ¿Dudas? Llama al 018000945555 y las resolvemos.")
        assertEquals("Recarga Saldo", s.description); assertEquals(400000L, s.amountCents)
    }

    @Test fun bancolombiaNomina() {
        val t = p("Bancolombia: Recibiste un pago de Nomina de PRAGMA S A por \$4,588,000.00 en tu cuenta de Ahorros el 27/10/2026 a las 10:24. Si tienes dudas, llamanos al 018000931987. A tu lado siempre.")
        assertEquals(Kind.NOMINA, t.kind); assertTrue(t.isIncome)
        assertEquals(458800000L, t.amountCents)
        assertEquals("Nomina de PRAGMA S A", t.description)
        assertEquals(at(2026, 10, 27, 10, 24), t.timeMillis)
    }

    @Test fun bancolombiaCompras() {
        val t = p("Bancolombia: Compraste \$58.400,00 en Chef Burger Mixy Los con tu T.Deb *8773, el 27/10/2026 a las 13:19. Si tienes dudas, encuentranos aqui: 6045109095 o 018000931987. Estamos cerca.")
        assertEquals(Kind.COMPRA, t.kind); assertEquals(5840000L, t.amountCents)
        assertEquals("Chef Burger Mixy Los", t.description); assertEquals("T.Deb *8773", t.product)
        assertEquals(at(2026, 10, 27, 13, 19), t.timeMillis)

        assertEquals("DROGUERIA PASTEUR MI", p("Bancolombia: Compraste \$41.111,00 en DROGUERIA PASTEUR MI con tu T.Deb *8773, el 27/10/2026 a las 13:24. Si tienes dudas, encuentranos aqui: 6045109095 o 018000931987. Estamos cerca.").description)
        assertEquals("MERCADO PAGO*WHOOSH", p("Bancolombia: Compraste \$6.150,00 en MERCADO PAGO*WHOOSH con tu T.Deb *8773, el 06/09/2026 a las 18:00. Si tienes dudas, encuentranos aqui: 6045109095 o 018000931987. Estamos cerca.").description)
        val u = p("Bancolombia: Compraste \$14.600,00 en INSTITUTO UNIV PASCU con tu T.Deb *8773, el 27/10/2026 a las 14:42. Si tienes dudas, encuentranos aqui: 6045109095 o 018000931987. Estamos cerca.")
        assertEquals(1460000L, u.amountCents)
    }

    @Test fun bancolombiaPagoYRetiro() {
        val t = p("Bancolombia: Pagaste \$309,233.00 a EMPRESAS PUBLICAS DE MEDELLIN desde tu producto 2084 el 28/10/2026 08:52:25. ¿Dudas? Llamanos al 6045109095. Estamos cerca")
        assertEquals(Kind.PAGO, t.kind); assertEquals(30923300L, t.amountCents)
        assertEquals("EMPRESAS PUBLICAS DE MEDELLIN", t.description)
        assertEquals(at(2026, 10, 28, 8, 52, 25), t.timeMillis)

        val r = p("Bancolombia: Retiraste \$90.000,00 en MXYCOLORES2 de tu T.Deb **8773 el 27/10/2026 a las 12:25. ¿Dudas?, llamanos al 6045109095. Estamos cerca")
        assertEquals(Kind.RETIRO, r.kind); assertEquals(9000000L, r.amountCents)
        assertEquals("Retiro MXYCOLORES2", r.description); assertEquals("T.Deb *8773", r.product)
    }

    @Test fun cajaSocialCompras() {
        val t = p("TX EXITOSA en *3069.El 2026.10.25 a las 16:53 un(a) COMPRA CON TARJETA DE CREDITO por \$64,337.15 en ANTHROPIC* CLAUDE SUB.Banco Caja Social")
        assertEquals(SmsParser.CAJA_SOCIAL, t.bank); assertEquals(Kind.COMPRA, t.kind)
        assertEquals(6433715L, t.amountCents); assertEquals("ANTHROPIC* CLAUDE SUB", t.description)
        assertEquals("TC *3069", t.product); assertFalse(t.reversal)
        assertEquals(at(2026, 10, 25, 16, 53), t.timeMillis)

        assertEquals("Disney Plus", p("TX EXITOSA en *3069.El 2026.08.28 a las 11:05 un(a) COMPRA CON TARJETA DE CREDITO por \$25,900.00 en Disney Plus.Banco Caja Social").description)

        val rev = p("TX REVERSADA en *3069.El 2026.08.26 a las 14:26 un(a) COMPRA CON TARJETA DE CREDITO por \$3,000.00 en GOOGLE *TEMPORARY HOLD.Banco Caja Social")
        assertTrue(rev.reversal); assertEquals(300000L, rev.amountCents); assertEquals("GOOGLE *TEMPORARY HOLD", rev.description)
    }

    @Test fun cajaSocialPagoTarjeta() {
        val t = p("Su pago por \$181.741,00 para TARJETA DE CREDITO quedo Aprobado. 10/18/2026 20:37. Banco Caja Social Tel Bta 601 542 6446 ? Nal 018000910038.")
        assertEquals(Kind.PAGO_TC, t.kind); assertEquals(18174100L, t.amountCents)
        assertEquals(at(2026, 10, 18, 20, 37), t.timeMillis)

        val t2 = p("Su pago por \$643.330,00 para TARJETA DE CREDITO quedo Aprobado. 08/18/2026 14:19. Banco Caja Social Tel Bta 601 542 6446 ? Nal 018000910038. ")
        assertEquals(64333000L, t2.amountCents); assertEquals(at(2026, 8, 18, 14, 19), t2.timeMillis)
    }

    @Test fun formatoDesconocidoQuedaParaRevisar() {
        val t = p("Bancolombia: Recibiste una transferencia por \$80,000 de JUAN PEREZ en tu cuenta **2084, el 20/10/2026 a las 09:10.")
        assertTrue(t.isIncome); assertFalse(t.recognized); assertEquals(8000000L, t.amountCents)
        assertEquals(at(2026, 10, 20, 9, 10), t.timeMillis)
    }

    @Test fun ignoraSmsNoBancarios() {
        assertNull(SmsParser.parse("Tu código de verificación es 123456", zone))
        assertNull(SmsParser.parse("Bancolombia: Tu clave dinamica es 445566. No la compartas.", zone))
    }
}
