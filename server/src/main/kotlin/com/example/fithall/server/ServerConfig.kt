package com.example.fithall.server

/** All settings come from environment variables so the same image runs anywhere. */
data class ServerConfig(
    val port: Int = 8080,
    val anthropicApiKey: String,
    /** Signs device tokens. Changing it signs every device out (they re-register automatically). */
    val tokenSecret: String,
    /** Set when running behind a load balancer that sets X-Forwarded-For (Cloud Run, Fly, Render...). */
    val trustProxy: Boolean = false,
    val limits: UsageLimits = UsageLimits(),
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: error("Missing required environment variable $name")
            fun int(name: String, default: Int) = env[name]?.toIntOrNull() ?: default

            val secret = required("TOKEN_SECRET")
            require(secret.length >= 32) { "TOKEN_SECRET must be at least 32 characters" }
            val defaults = UsageLimits()
            return ServerConfig(
                port = int("PORT", 8080),
                anthropicApiKey = required("ANTHROPIC_API_KEY"),
                tokenSecret = secret,
                trustProxy = env["TRUST_PROXY"].toBoolean(),
                limits = UsageLimits(
                    deviceUnitsPerDay = int("DEVICE_UNITS_PER_DAY", defaults.deviceUnitsPerDay),
                    deviceRequestsPerMinute = int("DEVICE_REQUESTS_PER_MINUTE", defaults.deviceRequestsPerMinute),
                    globalUnitsPerDay = int("GLOBAL_UNITS_PER_DAY", defaults.globalUnitsPerDay),
                    registrationsPerIpPerHour = int("REGISTRATIONS_PER_IP_PER_HOUR", defaults.registrationsPerIpPerHour),
                ),
            )
        }
    }
}
