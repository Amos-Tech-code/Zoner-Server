package com.amos_tech_code.application.configs

import io.github.cdimascio.dotenv.dotenv

object SMTPConfig {

    private val env = dotenv {
        ignoreIfMissing = true
        filename = ".env"
    }

    val SMTP_SERVER_HOST = env["SMTP_SERVER_HOST"].toString()
    val SMTP_SERVER_PORT = env["SMTP_SERVER_PORT"].toInt()
    val SMTP_SERVER_USER_NAME = env["SMTP_SERVER_USER_NAME"].toString()
    val SMTP_SERVER_PASSWORD = env["SMTP_SERVER_PASSWORD"].toString()
    val EMAIL_FROM = env["EMAIL_FROM"].toString()
}