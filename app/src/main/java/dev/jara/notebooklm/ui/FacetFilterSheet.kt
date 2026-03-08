package dev.jara.notebooklm.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FacetFilterSheet(
    facets: Map<String, NotebookFacets>,
    currentFilter: FacetFilter,
    onFilterChange: (FacetFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Term.surface,
        contentColor = Term.text,
        dragHandle = {
            // Vlastní drag handle
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Term.textDim)
                )
                Spacer(Modifier.height(12.dp))
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filtr",
                        color = Term.white,
                        fontFamily = Term.font,
                        fontSize = Term.fontSizeXl,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!currentFilter.isEmpty) {
                        Text(
                            text = "Vymazat vše",
                            color = Term.red,
                            fontFamily = Term.font,
                            fontSize = Term.fontSize,
                            modifier = Modifier
                                .clip(RoundedCornerShape(DS.chipRadius))
                                .clickable { onFilterChange(FacetFilter()) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    ) {
        // Extrahuj distinct hodnoty z facets mapy
        val allTopics = remember(facets) { facets.values.map { it.topic }.filter { it.isNotEmpty() }.distinct().sorted() }
        val allFormats = remember(facets) { facets.values.map { it.format }.filter { it.isNotEmpty() }.distinct().sorted() }
        val allPurposes = remember(facets) { facets.values.map { it.purpose }.filter { it.isNotEmpty() }.distinct().sorted() }
        val allDomains = remember(facets) { facets.values.map { it.domain }.filter { it.isNotEmpty() }.distinct().sorted() }
        val allFreshnesses = remember(facets) { facets.values.map { it.freshness }.filter { it.isNotEmpty() }.distinct().sorted() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FacetSection(
                label = "Téma",
                values = allTopics,
                selected = currentFilter.topics,
                color = Term.green,
                onToggle = { value ->
                    onFilterChange(currentFilter.copy(topics = currentFilter.topics.toggle(value)))
                },
            )
            FacetSection(
                label = "Formát",
                values = allFormats,
                selected = currentFilter.formats,
                color = Term.cyan,
                onToggle = { value ->
                    onFilterChange(currentFilter.copy(formats = currentFilter.formats.toggle(value)))
                },
            )
            FacetSection(
                label = "Účel",
                values = allPurposes,
                selected = currentFilter.purposes,
                color = Term.orange,
                onToggle = { value ->
                    onFilterChange(currentFilter.copy(purposes = currentFilter.purposes.toggle(value)))
                },
            )
            FacetSection(
                label = "Doména",
                values = allDomains,
                selected = currentFilter.domains,
                color = Term.purple,
                onToggle = { value ->
                    onFilterChange(currentFilter.copy(domains = currentFilter.domains.toggle(value)))
                },
            )
            FacetSection(
                label = "Aktuálnost",
                values = allFreshnesses,
                selected = currentFilter.freshnesses,
                color = Term.textDim,
                onToggle = { value ->
                    onFilterChange(currentFilter.copy(freshnesses = currentFilter.freshnesses.toggle(value)))
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetSection(
    label: String,
    values: List<String>,
    selected: Set<String>,
    color: Color,
    onToggle: (String) -> Unit,
) {
    if (values.isEmpty()) return

    Column {
        Text(
            text = label,
            color = color,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (value in values) {
                FacetChip(
                    text = value,
                    selected = value in selected,
                    color = color,
                    onClick = { onToggle(value) },
                )
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
    val shape = RoundedCornerShape(DS.chipRadius)
    val bgColor = if (selected) color.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (selected) color.copy(alpha = 0.5f) else Term.border.copy(alpha = DS.borderAlpha)
    val textColor = if (selected) color else Term.text

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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
