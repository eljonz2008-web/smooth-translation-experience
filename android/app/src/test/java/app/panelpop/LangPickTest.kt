package app.panelpop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LangPickTest {
    @Test fun hangulIsKorean() = assertEquals("ko", LangPick.scriptLang("안녕하세요, 괜찮아?"))
    @Test fun koreanWithEnglishSfxIsStillKorean() = assertEquals("ko", LangPick.scriptLang("OK 정말 고마워요"))
    @Test fun kanaIsJapanese() = assertEquals("ja", LangPick.scriptLang("こんにちは"))
    @Test fun kanjiWithKanaIsJapanese() = assertEquals("ja", LangPick.scriptLang("今日はいい天気"))
    @Test fun hanOnlyIsChinese() = assertEquals("zh", LangPick.scriptLang("你好世界"))
    @Test fun lettersAreLatin() = assertEquals(LangPick.LATIN, LangPick.scriptLang("Where are you going?"))
    @Test fun punctuationOnlyIsNothing() = assertNull(LangPick.scriptLang("!!! ... ?"))

    // El fallo original: el detector adivinaba idiomas al azar y lanzaba descargas inútiles
    @Test fun randomLanguageGuessFallsBackToEnglish() =
        assertEquals("en", LangPick.latinLang(listOf("sw" to 0.9f, "la" to 0.05f)))
    @Test fun lowConfidenceFallsBackToEnglish() = assertEquals("en", LangPick.latinLang(listOf("fr" to 0.3f)))
    @Test fun confidentFrenchIsAccepted() = assertEquals("fr", LangPick.latinLang(listOf("fr" to 0.8f, "en" to 0.1f)))
    @Test fun spanishIsRecognised() = assertEquals("es", LangPick.latinLang(listOf("es" to 0.95f)))
    @Test fun noCandidatesMeansEnglish() = assertEquals("en", LangPick.latinLang(emptyList()))

    @Test fun normalizeStripsScriptAndRegion() {
        assertEquals("ja", LangPick.normalize("ja-Latn"))
        assertEquals("zh", LangPick.normalize("zh_Hant"))
        assertEquals("und", LangPick.normalize(null))
    }

    @Test fun autoUsesScript() {
        assertEquals("ko", LangPick.resolve("ko", "auto", "en", false))
        assertEquals("zh", LangPick.resolve("zh", "auto", "en", false))
        assertEquals("ja", LangPick.resolve("zh", "auto", "en", true)) // kanji en página japonesa
        assertEquals("fr", LangPick.resolve(LangPick.LATIN, "auto", "fr", false))
    }

    @Test fun chosenLanguageWins() = assertEquals("ko", LangPick.resolve("zh", "ko", "en", false))
    @Test fun latinTextInKoreanComicIsReadAsEnglish() = assertEquals("en", LangPick.resolve(LangPick.LATIN, "ko", "en", false))
    @Test fun englishChoiceIsKeptForLatin() = assertEquals("en", LangPick.resolve(LangPick.LATIN, "en", "fr", false))

    @Test fun nativeCharsCountsOwnScript() {
        assertEquals(5, LangPick.nativeChars("안녕하세요 hi", "ko"))
        assertEquals(0, LangPick.nativeChars("hello", "ja"))
    }

    @Test fun noiseIsSkipped() {
        assertFalse(LangPick.worthTranslating("A"))
        assertFalse(LangPick.worthTranslating("!!"))
        assertTrue(LangPick.worthTranslating("No!"))
    }
}
