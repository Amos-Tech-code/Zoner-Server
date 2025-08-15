package com.amos_tech_code.di

import com.amos_tech_code.configs.SMTPConfig
import com.amos_tech_code.services.EmailService
import com.amos_tech_code.services.EmailServiceImpl
import org.koin.dsl.module
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.mailer.MailerBuilder

val mainModule = module {

    single<Mailer> {
        MailerBuilder
            .withSMTPServer(SMTPConfig.SMTP_SERVER_HOST, SMTPConfig.SMTP_SERVER_PORT)
            .withTransportStrategy(TransportStrategy.SMTP_TLS)
            .withSMTPServerUsername(SMTPConfig.SMTP_SERVER_USER_NAME)
            .withSMTPServerPassword(SMTPConfig.SMTP_SERVER_PASSWORD)
            .buildMailer()
    }

    single<EmailService> {
        EmailServiceImpl(
            mailer = get<Mailer>()
        )
    }

}