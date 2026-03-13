package dev.jara.notebooklm.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val panelShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FacetFilterPanel(
    facets: Map<String, NotebookFacets>,
    currentFilter: FacetFilter,
    onFilterChange: (FacetFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sekce, které mají být defaultně rozbalené
    val expandedSections = remember { mutableStateSetOf("Téma") }

    // Extrahuj distinct hodnoty z facets mapy
    val allTopics = remember(facets) { facets.values.map { it.topic }.filter { it.isNotEmpty() }.distinct().sorted() }
    val allFormats = remember(facets) { facets.values.map { it.format }.filter { it.isNotEmpty() }.distinct().sorted() }
    val allPurposes = remember(facets) { facets.values.map { it.purpose }.filter { it.isNotEmpty() }.distinct().sorted() }
    val allDomains = remember(facets) { facets.values.map { it.domain }.filter { it.isNotEmpty() }.distinct().sorted() }
    val allFreshnesses = remember(facets) { facets.values.map { it.freshness }.filter { it.isNotEmpty() }.distinct().sorted() }

    val sections = listOf(
        Triple("Téma", allTopics, Term.green),
        Triple("Formát", allFormats, Term.cyan),
        Triple("Účel", allPurposes, Term.orange),
        Triple("Doména", allDomains, Term.purple),
        Triple("Aktuálnost", allFreshnesses, Term.textDim),
    )

    // Počet aktivních filtrů celkem
    val activeCount = currentFilter.topics.size + currentFilter.formats.size +
        currentFilter.purposes.size + currentFilter.domains.size + currentFilter.freshnesses.size

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.75f)
            .shadow(8.dp, panelShape)
            .clip(panelShape)
            .background(Term.surface.copy(alpha = 0.97f))
            .border(
                width = 1.dp,
                color = Term.border.copy(alpha = 0.3f),
                shape = panelShape,
            ),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // ── Header ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            tint = Term.white,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Filtr",
                            color = Term.white,
                            fontFamily = Term.font,
                            fontSize = Term.fontSizeXl,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (activeCount > 0) {
                        // Badge s počtem
                        Text(
                            text = "$activeCount",
                            color = Term.bg,
                            fontFamily = Term.font,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(DS.chipRadius))
                                .background(Term.cyan)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        DetailPill("Vymazat", Term.red) { onFilterChange(FacetFilter()) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Oddělovací linka pod headerem
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Term.border.copy(alpha = 0.2f))
                )
            }

            // ── Obsah — akordeonové sekce ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for ((label, values, color) in sections) {
                    if (values.isEmpty()) continue

                    val selected = when (label) {
                        "Téma" -> currentFilter.topics
                        "Formát" -> currentFilter.formats
                        "Účel" -> currentFilter.purposes
                        "Doména" -> currentFilter.domains
                        "Aktuálnost" -> currentFilter.freshnesses
                        else -> emptySet()
                    }

                    val isExpanded = label in expandedSections

                    AccordionSection(
                        label = label,
                        values = values,
                        selected = selected,
                        color = color,
                        expanded = isExpanded,
                        onToggleExpand = {
                            if (isExpanded) expandedSections.remove(label)
                            else expandedSections.add(label)
                        },
                        onToggleValue = { value ->
                            val newFilter = when (label) {
                                "Téma" -> currentFilter.copy(topics = currentFilter.topics.toggle(value))
                                "Formát" -> currentFilter.copy(formats = currentFilter.formats.toggle(value))
                                "Účel" -> currentFilter.copy(purposes = currentFilter.purposes.toggle(value))
                                "Doména" -> currentFilter.copy(domains = currentFilter.domains.toggle(value))
                                "Aktuálnost" -> currentFilter.copy(freshnesses = currentFilter.freshnesses.toggle(value))
                                else -> currentFilter
                            }
                            onFilterChange(newFilter)
                        },
                    )
                }
                // Extra prostor, aby se dalo scrollovat až k poslední sekci
                Spacer(Modifier.height(120.dp))
            }

            // ── Footer ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                ActionPill("Zavřít", Term.textDim) { onDismiss() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccordionSection(
    label: String,
    values: List<String>,
    selected: Set<String>,
    color: Color,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleValue: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        // Hlavička sekce
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DS.chipRadius))
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = color,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
            )
            if (selected.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${selected.size}",
                    color = Term.bg,
                    fontFamily = Term.font,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.8f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                tint = Term.textDim,
                modifier = Modifier.size(18.dp),
            )
        }

        // Obsah (chipy) — jen pokud rozbaleno
        if (expanded) {
            FlowRow(
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (value in values) {
                    FacetChip(
                        text = value,
                        selected = value in selected,
                        color = color,
                        onClick = { onToggleValue(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FacetChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(DS.chipRadius)
    val bgColor = if (selected) color.copy(alpha = 0.85f) else Color.Transparent
    val borderColor = if (selected) color else Term.border.copy(alpha = 0.2f)
    val textColor = if (selected) Term.bg else Term.text

    Text(
        text = text,
        color = textColor,
        fontFamily = Term.font,
        fontSize = Term.fontSize,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .border(DS.borderWidth, borderColor, shape)
            .background(bgColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
