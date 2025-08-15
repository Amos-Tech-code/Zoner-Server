package com.amos_tech_code.services

import com.amos_tech_code.configs.SMTPConfig
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.email.EmailBuilder

class EmailServiceImpl(private val mailer: Mailer) : EmailService {

    private fun generateVerificationEmail(name: String, code: String): String {
        return """
        <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email Verification</title>
                <style>
                    /* Base Styles */
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: #333333;
                        background-color: #f7f7f7;
                        margin: 0;
                        padding: 0;
                    }
                    
                    /* Container */
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #ffffff;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
                    }
                    
                    /* Header */
                    .header {
                        text-align: center;
                        padding: 20px 0;
                        border-bottom: 1px solid #eeeeee;
                    }
                    
                    .logo {
                        max-width: 150px;
                        height: auto;
                    }
                    
                    /* Content */
                    .content {
                        padding: 30px 20px;
                    }
                    
                    h1 {
                        color: #2c3e50;
                        font-size: 24px;
                        margin-top: 0;
                    }
                    
                    .verification-code {
                        background-color: #f8f9fa;
                        border: 1px dashed #dee2e6;
                        padding: 15px;
                        text-align: center;
                        font-size: 32px;
                        font-weight: bold;
                        letter-spacing: 5px;
                        color: #2c3e50;
                        margin: 25px 0;
                        border-radius: 6px;
                    }
                    
                    /* Footer */
                    .footer {
                        text-align: center;
                        padding: 20px;
                        font-size: 12px;
                        color: #7f8c8d;
                        border-top: 1px solid #eeeeee;
                    }
                    
                    /* Responsive */
                    @media only screen and (max-width: 600px) {
                        .email-container {
                            width: 100%;
                            border-radius: 0;
                        }
                        
                        .verification-code {
                            font-size: 28px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <div class="header">
                        <!-- Replace with your logo URL -->
                        <img src="https://yourdomain.com/logo.png" alt="Zoner Logo" class="logo">
                    </div>
                    
                    <div class="content">
                        <h1>Email Verification Request</h1>
                        
                        <p>Hello $name,</p>
                        
                        <p>We received a request to verify your email address. Use the following verification code to proceed:</p>
                        
                        <div class="verification-code">$code</div>
                        
                        <p>This code will expire in <strong>10 minutes</strong> for security purposes.</p>
                        
                        <p>If you did not request this verification, please ignore this email or contact support if you have any concerns.</p>
                        
                        <p>Best regards,<br>The Zoner Team</p>
                    </div>
                    
                    <div class="footer">
                        <p>&copy; 2023 Zoner. All rights reserved.</p>
                        <p>
                            <a href="https://yourdomain.com/privacy" style="color: #7f8c8d; text-decoration: none;">Privacy Policy</a> | 
                            <a href="https://yourdomain.com/terms" style="color: #7f8c8d; text-decoration: none;">Terms of Service</a>
                        </p>
                    </div>
                </div>
            </body>
        </html>
        """.trimIndent()
            .replace("{{name}}", name)
            .replace("{{code}}", code)
    }

    override suspend fun sendVerificationCode(email: String, name: String, code: String): Boolean {
        val emailData = EmailData(
            emailFrom = SMTPConfig.EMAIL_FROM,
            emailTo = email,
            subject = "Your Zoner Verification Code",
            message = generateVerificationEmail(name, code)
        )

        val email = EmailBuilder.startingBlank()
            .from("Zoner Support", emailData.emailFrom)
            .to(name, emailData.emailTo)
            .withSubject(emailData.subject)
            .withHTMLText(emailData.message)
            .withPlainText("""
                Email Verification Request
                Hello $name,
                
                Your verification code is: $code
                
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

}

data class EmailData(
    val emailFrom:String,
    val emailTo:String,
    val subject:String,
    val message:String
)