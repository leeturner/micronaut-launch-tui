package com.leeturner.mtui.adapters.inbound.tui

import com.leeturner.mtui.domain.core.model.Feature

data class FeatureGroup(
    val category: String,
    val features: List<Feature>,
)

// Search, highlight and selection for the feature picker, kept free of TamboUI so it can be unit tested
class FeaturePicker(
    features: List<Feature>,
    selected: List<String> = emptyList(),
) {
    private val all = features.sortedWith(compareBy({ it.category }, { it.name }))
    private val chosen = LinkedHashSet<String>()

    // Tracked by name rather than index so the highlight survives re-filtering
    private var highlightedName: String? = null

    var groups: List<FeatureGroup> = group(all)
        private set

    var query: String = ""
        set(value) {
            field = value
            val term = value.trim()
            groups = group(all.filter { it.matches(term) })
            if (visible().none { it.name == highlightedName }) highlightedName = visible().firstOrNull()?.name
        }

    val highlighted: Feature? get() = visible().firstOrNull { it.name == highlightedName }

    val selected: List<String> get() = chosen.toList()

    init {
        highlightedName = all.firstOrNull()?.name
        // A selection carried over from another feature list keeps only the features this list has
        val names = all.mapTo(HashSet()) { it.name }
        selected.filterTo(chosen) { it in names }
    }

    fun isSelected(feature: Feature): Boolean = feature.name in chosen

    fun moveDown() = move(1)

    fun moveUp() = move(-1)

    fun toggle() {
        val name = highlighted?.name ?: return
        if (!chosen.remove(name)) chosen.add(name)
    }

    fun clearSelection() = chosen.clear()

    private fun move(step: Int) {
        val visible = visible()
        val index = visible.indexOfFirst { it.name == highlightedName }
        if (index < 0) return
        highlightedName = visible[(index + step).coerceIn(visible.indices)].name
    }

    private fun visible(): List<Feature> = groups.flatMap { it.features }

    private fun group(features: List<Feature>): List<FeatureGroup> =
        features.groupBy { it.category }.map { (category, inCategory) -> FeatureGroup(category, inCategory) }

    private fun Feature.matches(term: String): Boolean =
        name.contains(term, ignoreCase = true) ||
            title.contains(term, ignoreCase = true) ||
            description.contains(term, ignoreCase = true)
}
