package app.panelpop

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFixTest {
    // Caso real de la captura: "I'D LIKE TO / TAKE IT WITH ME / IF I COULD,"
    @Test fun comicCapsBecomeSentenceCase() =
        assertEquals("I'd like to take it with me if I could,",
            TextFix.prepareLatin(listOf("I'D LIKE TO", "TAKE IT WITH ME", "IF I COULD,")))

    @Test fun strayJapaneseCharsAreRemovedFromEnglish() =
        assertEquals("I'd like to", TextFix.fixCaps(TextFix.cleanLatin("I'D ワ LIKE TO")))

    @Test fun hyphenatedWordIsJoined() =
        assertEquals("The secret record", TextFix.joinLines(listOf("The se-", "cret record"), false))

    @Test fun newSentenceIsCapitalised() =
        assertEquals("Wait! Where are you going?", TextFix.fixCaps("WAIT! WHERE ARE YOU GOING?"))

    @Test fun normalTextIsUntouched() = assertEquals("Hello there", TextFix.fixCaps("Hello there"))

    @Test fun cjkLinesJoinWithoutSpaces() = assertEquals("안녕하세요", TextFix.joinLines(listOf("안녕", "하세요"), true))

    @Test fun whiteBubbleWithBlackLettersStaysWhite() {
        val samples = IntArray(30) { if (it < 8) TextFix.BLACK else 0xFFF6F6F6.toInt() }
        assertEquals(TextFix.WHITE, TextFix.pickBackground(samples))
    }

    @Test fun darkBoxStaysDark() {
        val samples = IntArray(30) { if (it < 5) TextFix.WHITE else 0xFF101010.toInt() }
        assertEquals(TextFix.BLACK, TextFix.pickBackground(samples))
    }

    @Test fun latinReaderWinsOnEnglishPage() {
        val t = "I'D LIKE TO TAKE IT WITH ME"
        assert(TextFix.readerScore(t, "latin") > TextFix.readerScore(t, "ja"))
    }
}
