package com.amos_tech_code.plugins

import com.amos_tech_code.di.mainModule
import io.ktor.server.application.Application
import org.koin.ktor.plugin.koin

fun Application.configureKoin(){
    koin {
        modules(mainModule)
    }
}