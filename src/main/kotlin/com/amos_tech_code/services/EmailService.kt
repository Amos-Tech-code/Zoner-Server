package com.amos_tech_code.services

import com.amos_tech_code.configs.SMTPConfig
import com.amos_tech_code.utils.EmailTemplateUtil
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.email.EmailBuilder

class EmailServiceImpl(private val mailer: Mailer) : EmailService {

    override suspend fun sendVerificationCode(email: String, name: String, code: String): Boolean {
        val html = EmailTemplateUtil.generateVerificationEmail(name, code)
        val emailData = EmailData(
            emailFrom = SMTPConfig.EMAIL_FROM,
            emailTo = email,
            subject = "Your Zoner Verification Code",
            message = html
        )

        val email = EmailBuilder.startingBlank()
            .from("Zoner Support", emailData.emailFrom)
            .to(name, emailData.emailTo)
            .withSubject(emailData.subject)
            .withHTMLText(emailData.message)
            .withPlainText("""
                Password Reset Request
                Hello $name,
                
                We received a request to reset your password.
                Your reset code is: $code
                
                This code expires in 10 minutes.
                
                If you didn’t request this, please ignore this email or contact our support team.
                
                Best regards,
                Zoner Team
            """.trimIndent())
            .buildEmail()

        return try {
            mailer.sendMail(email)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun sendPasswordResetCode(
        email: String,
        name: String,
        code: String
    ): Boolean {
        val html = EmailTemplateUtil.generatePasswordResetEmail(name, code)
        val emailData = EmailData(
            emailFrom = SMTPConfig.EMAIL_FROM,
            emailTo = email,
            subject = "Your Zoner Password Reset Code",
            message = html
        )

        val email = EmailBuilder.startingBlank()
            .from("Zoner Support", emailData.emailFrom)
            .to(name, emailData.emailTo)
            .withSubject(emailData.subject)
            .withHTMLText(emailData.message)
            .withPlainText("""
                Password reset Request
                Hello $name,
                
                Your password reset code is: $code
                
                This code expires in 10 minutes.
                
                If you didn't request this, please ignore this email.
                
                Best regards,
                Zoner Team
            """.trimIndent())
            .buildEmail()

        return try {
            mailer.sendMail(email)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

interface EmailService {

    suspend fun sendVerificationCode(email: String, name: String, code: String) : Boolean

    suspend fun sendPasswordResetCode(email: String, name: String, code: String) : Boolean


}

data class EmailData(
    val emailFrom:String,
    val emailTo:String,
    val subject:String,
    val message:String
)