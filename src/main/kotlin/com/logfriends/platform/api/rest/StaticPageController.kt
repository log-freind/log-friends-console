package com.logfriends.platform.api.rest

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class StaticPageController {

    @GetMapping("/")
    fun index(): String = "redirect:/log-catalog"

    @GetMapping("/log-catalog")
    fun logCatalog(): String = "forward:/log-catalog.html"
}
