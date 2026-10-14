# כללי R8 לגרסת release. Compose ו-AndroidX מביאים את הכללים שלהם.

# Commons Compress: תלויות אופציונליות לפורמטים שלא בשימוש (Zstandard, Brotli, Pack200).
# בלעדיהן R8 נכשל על "Missing class"; הקוד שלהן לא נטען אף פעם.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
