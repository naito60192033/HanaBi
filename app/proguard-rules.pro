# jcifs-ng SMBクライアント
-keep class jcifs.** { *; }

# jcifs-ng が参照する未使用クラスのwarning抑制
-dontwarn javax.security.auth.callback.NameCallback
-dontwarn javax.security.auth.kerberos.KerberosKey
-dontwarn javax.security.auth.kerberos.KerberosPrincipal
-dontwarn javax.security.auth.kerberos.KerberosTicket
-dontwarn javax.security.auth.login.AppConfigurationEntry$LoginModuleControlFlag
-dontwarn javax.security.auth.login.AppConfigurationEntry
-dontwarn javax.security.auth.login.Configuration
-dontwarn javax.security.auth.login.LoginContext
-dontwarn javax.servlet.Filter
-dontwarn javax.servlet.FilterChain
-dontwarn javax.servlet.FilterConfig
-dontwarn javax.servlet.ServletConfig
-dontwarn javax.servlet.ServletException
-dontwarn javax.servlet.ServletOutputStream
-dontwarn javax.servlet.ServletRequest
-dontwarn javax.servlet.ServletResponse
-dontwarn javax.servlet.http.HttpServlet
-dontwarn javax.servlet.http.HttpServletRequest
-dontwarn javax.servlet.http.HttpServletRequestWrapper
-dontwarn javax.servlet.http.HttpServletResponse
-dontwarn javax.servlet.http.HttpSession
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.MessageProp
-dontwarn org.ietf.jgss.Oid
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
