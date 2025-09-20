package com.amos_tech_code.application.configs

import io.github.cdimascio.dotenv.dotenv

object SupabaseConfig {

    private val env = dotenv {
        ignoreIfMissing = true
        filename = ".env"
    }

    val SUPABASE_URL = env["SUPABASE_URL"]
    val SUPABASE_KEY= env["SUPABASE_KEY"]
    val STORAGE_BUCKET: String? = env["SUPABASE_STORAGE_BUCKET"]
} 