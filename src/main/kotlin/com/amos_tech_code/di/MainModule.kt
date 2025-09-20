package com.amos_tech_code.di

import com.amos_tech_code.application.configs.SMTPConfig
import com.amos_tech_code.data.repository.StatusRepositoryImpl
import com.amos_tech_code.data.repository.UserRepositoryImpl
import com.amos_tech_code.domain.repository.StatusRepository
import com.amos_tech_code.domain.repository.UserRepository
import com.amos_tech_code.domain.services.EmailService
import com.amos_tech_code.domain.services.EmailServiceImpl
import com.amos_tech_code.domain.services.ImageService
import com.amos_tech_code.domain.services.VideoService
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

    single<StatusRepository> { StatusRepositoryImpl() }

    single<UserRepository> { UserRepositoryImpl() }

}