package com.kharlos.finanzas.domain

import com.kharlos.finanzas.parser.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class LogicTest {

    private val cats = Cat.defaults
    private val rules = Cat.defaultRules.mapIndexed { i, (p, c) -> Rule(i + 1L, p, c, true) }
    private fun cat(desc: String, kind: Kind = Kind.COMPRA, income: Boolean = false, r: List<Rule> = rules) =
        Categorizer.categorize(desc, kind, income, r, cats)

    @Test fun categoriasPorComercio() {
        assertEquals(Cat.COMIDA, cat("Chef Burger Mixy Los"))
        assertEquals(Cat.SALUD, cat("DROGUERIA PASTEUR MI"))
        assertEquals(Cat.EDUCACION, cat("INSTITUTO UNIV PASCU"))
        assertEquals(Cat.TRANSPORTE, cat("MERCADO PAGO*WHOOSH"))
        assertEquals(Cat.SUSCRIPCIONES, cat("ANTHROPIC* CLAUDE SUB"))
        assertEquals(Cat.SUSCRIPCIONES, cat("Disney Plus"))
        assertEquals(Cat.SERVICIOS, cat("EMPRESAS PUBLICAS DE MEDELLIN", Kind.PAGO))
        assertEquals(Cat.OTROS_GASTOS, cat("LAURA CATALINA GIL S"))
    }

    @Test fun categoriasPorTipo() {
        assertEquals(Cat.TRANSPORTE, cat("Recarga Tarjeta Civica", Kind.RECARGA))
        assertEquals(Cat.SERVICIOS, cat("Recarga Saldo", Kind.RECARGA))
        assertEquals(Cat.TRANSFERENCIAS, cat("Transferencia a *3017413746", Kind.TRANSFERENCIA))
        assertEquals(Cat.EFECTIVO, cat("Retiro MXYCOLORES2", Kind.RETIRO))
        assertEquals(Cat.SALARIO, cat("Nomina de PRAGMA S A", Kind.NOMINA, true))
        assertEquals(Cat.PAGO_TARJETA, cat("Pago tarjeta de crédito", Kind.PAGO_TC))
    }

    @Test fun reglaPropiaGana() {
        val mine = rules + Rule(999, "3017413746", Cat.ENTRE_CUENTAS, false)
        assertEquals(Cat.ENTRE_CUENTAS, cat("Transferencia a *3017413746", Kind.TRANSFERENCIA, r = mine))
        assertEquals(Cat.TRANSFERENCIAS, cat("Transferencia a *3007377692", Kind.TRANSFERENCIA, r = mine))
    }

    @Test fun inicioDePalabra() {
        assertTrue(Categorizer.matches("pascu", "INSTITUTO UNIV PASCUAL"))
        assertFalse(Categorizer.matches("TIGO", "PAN CONTIGO"))
        assertTrue(Categorizer.matches("educación", "EDUCACION CONTINUA"))
    }

    @Test fun resumenExcluyeReversadosYPagoTarjeta() {
        val z = ZoneId.of("America/Bogota")
        fun t(d: Int, cents: Long, inc: Boolean, c: Long, excl: Boolean = false) = Tx(
            timeMillis = LocalDateTime.of(2026, 10, d, 12, 0).atZone(z).toInstant().toEpochMilli(),
            amountCents = cents, isIncome = inc, bank = "B", kind = "COMPRA", description = "x", categoryId = c, excluded = excl)
        val txs = listOf(
            t(27, 458800000, true, Cat.SALARIO),
            t(27, 5840000, false, Cat.COMIDA),
            t(27, 300000, false, Cat.SUSCRIPCIONES, excl = true),
            t(18, 18174100, false, Cat.PAGO_TARJETA),
            t(28, 30923300, false, Cat.SERVICIOS),
        )
        val s = Stats.summary(YearMonth.of(2026, 10), txs, cats, z)
        assertEquals(458800000L, s.incomeCents)
        assertEquals(5840000L + 30923300L, s.expenseCents)
        assertEquals(Cat.SERVICIOS, s.expenseByCategory.first().category.id)
        assertEquals(91, s.savingsRate)
        assertEquals(0L, Stats.summary(YearMonth.of(2026, 9), txs, cats, z).expenseCents)
    }

    @Test fun proyeccion() {
        assertEquals(3100000L, Stats.projection(1000000, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10)))
        assertEquals(1000000L, Stats.projection(1000000, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 10)))
    }

    @Test fun montoEscrito() {
        assertEquals(5840000L, Money.parseInput("58.400"))
        assertEquals(5840050L, Money.parseInput("58400,5"))
        assertEquals(null, Money.parseInput("abc"))
    }
}
