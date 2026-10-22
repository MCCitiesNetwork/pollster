plugins {
    `java-library`
    `pollster-conventions`
}

dependencies {
    // Only the Bukkit events in api.event touch the server API.
    compileOnly("io.papermc.paper:paper-api:26.3.build.211-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.211-stable")
}
