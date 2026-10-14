# כללי R8 לגרסת release. Compose ו-AndroidX מביאים את הכללים שלהם.

# Commons Compress: תלויות אופציונליות לפורמטים שלא בשימוש (Zstandard, Brotli, Pack200).
# בלעדיהן R8 נכשל על "Missing class"; הקוד שלהן לא נטען אף פעם.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# SMB (smbj): אוטובוס האירועים (MBassador) מוצא את המטפלים לפי האנוטציה @Handler ויוצר אותם
# ברפלקציה — בלי זה R8 מסיר "מתודות שאף אחד לא קורא להן", והחיבור לא מקבל תשובות.
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}
-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod
# אימות Kerberos/SPNEGO של smbj נטען רק מחוץ לאנדרואיד (SmbConfig בודק android.os.Build).
-dontwarn javax.security.auth.kerberos.**
-dontwarn org.ietf.jgss.**
-dontwarn com.sun.security.jgss.**
-dontwarn com.ibm.security.jgss.**
-dontwarn javax.el.**
# dcerpc זורק java.rmi.UnmarshalException על תשובה פגומה — אין java.rmi באנדרואיד. SmbFs.listShares
# תופס את ה-NoClassDefFoundError שיוצא משם, והופך אותו להודעה ("השרת לא מחזיר את רשימת השיתופים").
-dontwarn java.rmi.**
# Guava (דרך dcerpc): אנוטציות לזמן קומפילציה בלבד.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
