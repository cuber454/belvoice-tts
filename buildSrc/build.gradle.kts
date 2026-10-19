plugins { `kotlin-dsl` }

repositories { mavenCentral() }

dependencies { implementation("org.json:json:20240303") }

// тот же StressBin.kt, что в приложении: stress.bin пишет один код и в сборке, и в JVM-тестах
sourceSets.main { kotlin.srcDir("../app/src/main/java/ru/kost/ruvoice/bin") }
