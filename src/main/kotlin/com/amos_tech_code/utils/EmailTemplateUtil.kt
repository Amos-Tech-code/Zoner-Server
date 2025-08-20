package com.amos_tech_code.utils

object EmailTemplateUtil {

    const val ZONER_LOGO_URL = "https://issukbsivkkqzsghassb.supabase.co/storage/v1/object/public/zoner_bucket/system/logov1.png"
    const val PRIVACY_POLICY_URL = "https://zoner-server.onrender.com/privacy"
    const val TERMS_OF_SERVICE_URL = "https://zoner-server.onrender.com/terms"

    fun generateVerificationEmail(name: String, code: String): String {
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
                        <img src=${ZONER_LOGO_URL} alt="Zoner Logo" class="logo">
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
                        <p>&copy; 2025 Zoner. All rights reserved.</p>
                        <p>
                            <a href=${PRIVACY_POLICY_URL} style="color: #7f8c8d; text-decoration: none;">Privacy Policy</a> | 
                            <a href=${TERMS_OF_SERVICE_URL} style="color: #7f8c8d; text-decoration: none;">Terms of Service</a>
                        </p>
                    </div>
                </div>
            </body>
        </html>
        """.trimIndent()
            .replace("{{name}}", name)
            .replace("{{code}}", code)
    }


    fun generatePasswordResetEmail(name: String, code: String): String {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Password Reset</title>
            <style>
                body {
                    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    line-height: 1.6;
                    color: #333333;
                    background-color: #f7f7f7;
                    margin: 0;
                    padding: 0;
                }
                .email-container {
                    max-width: 600px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #ffffff;
                    border-radius: 8px;
                    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
                }
                .header {
                    text-align: center;
                    padding: 20px 0;
                    border-bottom: 1px solid #eeeeee;
                }
                .logo {
                    max-width: 150px;
                    height: auto;
                }
                .content {
                    padding: 30px 20px;
                }
                h1 {
                    color: #2c3e50;
                    font-size: 24px;
                    margin-top: 0;
                }
                .reset-code {
                    background-color: #f8f9fa;
                    border: 1px dashed #dee2e6;
                    padding: 15px;
                    text-align: center;
                    font-size: 32px;
                    font-weight: bold;
                    letter-spacing: 5px;
                    color: #d9534f;
                    margin: 25px 0;
                    border-radius: 6px;
                }
                .footer {
                    text-align: center;
                    padding: 20px;
                    font-size: 12px;
                    color: #7f8c8d;
                    border-top: 1px solid #eeeeee;
                }
                @media only screen and (max-width: 600px) {
                    .email-container {
                        width: 100%;
                        border-radius: 0;
                    }
                    .reset-code {
                        font-size: 28px;
                    }
                }
            </style>
        </head>
        <body>
            <div class="email-container">
                <div class="header">
                    <img src=${ZONER_LOGO_URL} alt="Zoner Logo" class="logo">
                </div>
                <div class="content">
                    <h1>Password Reset Request</h1>
                    
                    <p>Hello $name,</p>
                    
                    <p>We received a request to reset your account password. Use the following code to proceed with resetting your password:</p>
                    
                    <div class="reset-code">$code</div>
                    
                    <p>This code will expire in <strong>10 minutes</strong> for security purposes.</p>
                    
                    <p>If you did not request this reset, please ignore this email or contact support immediately.</p>
                    
                    <p>Best regards,<br>The Zoner Team</p>
                </div>
                
                <div class="footer">
                    <p>&copy; 2025 Zoner. All rights reserved.</p>
                    <p>
                        <a href=${PRIVACY_POLICY_URL} style="color: #7f8c8d; text-decoration: none;">Privacy Policy</a> | 
                        <a href=${TERMS_OF_SERVICE_URL} style="color: #7f8c8d; text-decoration: none;">Terms of Service</a>
                    </p>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
    }

}