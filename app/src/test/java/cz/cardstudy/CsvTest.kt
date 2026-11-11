package cz.cardstudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test
    fun formulaInjectionIsNeutralisedAndRoundTrips() {
        assertEquals("'=1+1", Csv.quote("=1+1"))
        assertEquals("\"'=A1;B1\"", Csv.quote("=A1;B1"))
        assertEquals("-ito", Csv.unsafe(Csv.safe("-ito")))
        assertEquals("'abc", Csv.unsafe("'abc"))
        assertEquals("hola", Csv.quote("hola"))
    }

    @Test
    fun semicolonWithHeaderQuotesAndStatus() {
        val text = "španělsky;česky;poznámka;stav\r\nel perro;pes;;umím\r\n\"la cuenta; por favor\";\"účet, \"\"prosím\"\"\";;neumím\r\n"
        val rows = Csv.parse(text)
        assertTrue(Csv.isHeader(rows[0]))
        assertEquals("es-ES", Csv.headerLang(rows[0]))
        assertEquals(listOf("el perro", "pes", "", "umím"), rows[1])
        assertEquals("la cuenta; por favor", rows[2][0])
        assertEquals("účet, \"prosím\"", rows[2][1])
        assertEquals(true, Csv.status(rows[1][3]))
        assertEquals(false, Csv.status(rows[2][3]))
    }

    @Test
    fun languageFromHeader() {
        assertEquals("en-GB", Csv.headerLang(listOf("anglicky", "česky")))
        assertEquals("en-US", Csv.headerLang(listOf("anglicky US", "česky")))
        assertEquals("de-DE", Csv.headerLang(listOf("Deutsch", "Tschechisch")))
        assertEquals("fr-FR", Csv.headerLang(listOf("fr", "cs")))
        assertTrue(Csv.isHeader(listOf("otázka", "odpověď", "poznámka")))
        assertNull(Csv.headerLang(listOf("otázka", "odpověď")))
        // španělské "es" (= je) v prvním řádku není hlavička
        assertFalse(Csv.isHeader(listOf("es", "je")))
        assertFalse(Csv.isHeader(listOf("Hola", "Ahoj", "pozdrav", "neumím")))
    }

    @Test
    fun commaAndTabDelimiters() {
        assertEquals(',', Csv.detectDelimiter("hola,ahoj\nperro,pes\n"))
        assertEquals('\t', Csv.detectDelimiter("hola\tahoj, nazdar\nperro\tpes\n"))
        assertEquals(';', Csv.detectDelimiter("hola;ahoj, nazdar\nperro;pes\n"))
    }

    @Test
    fun windows1250FromExcel() {
        // pozn.: Windows-1250 nemá ñ – španělštinu je lepší ukládat jako "CSV UTF-8"
        val bytes = "está;příliš žluťoučký".toByteArray(charset("windows-1250"))
        assertEquals("está;příliš žluťoučký", Csv.decode(bytes))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "año;rok".toByteArray()
        assertEquals("año;rok", Csv.decode(bom))
    }

    @Test
    fun exportQuoting() {
        assertEquals("\"a;b\"", Csv.quote("a;b"))
        assertEquals("plain", Csv.quote("plain"))
    }

    @Test
    fun pronunciationScore() {
        assertEquals(1f, Pronunciation.evaluate("el perro", listOf("perro")).score, 0.001f)
        assertEquals(1f, Pronunciation.evaluate("¿Dónde está el baño?", listOf("dónde está el baño")).score, 0.001f)
        assertEquals(1f, Pronunciation.evaluate("tú / usted", listOf("usted")).score, 0.001f)
        assertEquals(1f, Pronunciation.evaluate("to speak", listOf("speak")).score, 0.001f)
        assertEquals(1f, Pronunciation.evaluate("der Hund", listOf("Hund")).score, 0.001f)
        assertTrue(Pronunciation.evaluate("hablar", listOf("Pablo")).score < 0.7f)
        assertEquals("perro", Pronunciation.evaluate("el perro", listOf("pero", "perro")).best)
    }
}
