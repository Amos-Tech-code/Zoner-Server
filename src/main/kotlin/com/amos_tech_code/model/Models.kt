package com.amos_tech_code.model

/**
 * Enum Classes
 */
enum class RegistrationStage {
    EMAIL_SUBMITTED,     // After signup, before verification
    EMAIL_VERIFIED,      // After verification, before profile
    PROFILE_COMPLETED,   // Username/profile set
    BUSINESS_ADDED       // Business profile created (optional)
}

enum class UserRole {
    USER,               // Default (can only view posts)
    BUSINESS,           // Can post business content (verified or unverified)
    ADMIN               // Moderator/superuser
}
enum class AuthProvider { GOOGLE, FACEBOOK, PHONE, EMAIL }

enum class VerificationStatus { PENDING, APPROVED, REJECTED }

enum class MediaType { IMAGE, VIDEO }

enum class UploadFolder(val folderName: String) {
    PROFILE_PIC("profile-pics"),
    USER("users"),
    ADMIN("admins"),
    SYSTEM("system"),
    STATUS("status"),
    POST("posts"),
    OTHERS("others");

    companion object {
        fun fromType(type: String?): UploadFolder {
            return when (type?.lowercase()) {
                "profile-pics" -> PROFILE_PIC
                "user" -> USER
                "admin" -> ADMIN
                "system" -> SYSTEM
                "status" -> STATUS
                "post" -> POST
                else -> OTHERS
            }
        }
    }
}

