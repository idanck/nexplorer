// AGP 9 מקמפל Kotlin בעצמו (built-in Kotlin) ונשען על KGP ‏2.2.10. התוסף של Kotlin נטען כאן בלי להחיל אותו,
// כדי ש-Gradle יבחר את KGP בגרסה של הקטלוג — אותה גרסה של קומפיילר ה-Compose.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
