package com.leeturner.mtui.domain.core.model

data class Feature(
    val name: String,
    val title: String,
    val description: String,
    val category: String,
    val preview: Boolean,
    val community: Boolean,
)

// A blank term matches every feature; otherwise the trimmed term is looked for in the name, title or description
fun Feature.matches(term: String): Boolean {
    val trimmed = term.trim()
    return name.contains(trimmed, ignoreCase = true) ||
        title.contains(trimmed, ignoreCase = true) ||
        description.contains(trimmed, ignoreCase = true)
}
