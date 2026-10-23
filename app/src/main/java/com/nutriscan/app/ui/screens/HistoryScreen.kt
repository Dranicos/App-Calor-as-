package com.nutriscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriscan.app.data.MealEntity
import com.nutriscan.app.domain.plan
import com.nutriscan.app.ui.MainViewModel
import com.nutriscan.app.ui.components.MacroChips
import com.nutriscan.app.ui.components.MealCard
import com.nutriscan.app.ui.components.kcal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun dayLabel(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    val today = LocalDate.now()
    return when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)).replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpenMeal: (Long) -> Unit) {
    val meals by vm.allMeals.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val target = settings?.profile?.plan()?.targetKcal ?: 0
    val byDay = meals.groupBy { it.day }.toSortedMap(compareByDescending { it })
    var toDelete by remember { mutableStateOf<MealEntity?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Historial", style = MaterialTheme.typography.headlineMedium) }
        if (byDay.isEmpty()) {
            item { Text("Todavía no hay comidas guardadas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        byDay.forEach { (day, dayMeals) ->
            item(key = "header_$day") {
                val total = dayMeals.sum()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dayLabel(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (target > 0) "${total.kcal.kcal()} / $target" else total.kcal.kcal(),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (target > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (total.kcal / target).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (total.kcal > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                drawStopIndicator = {},
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        MacroChips(total.protein, total.carbs, total.fat)
                    }
                }
            }
            items(dayMeals, key = { it.id }) { meal ->
                MealCard(meal, vm.photos, onClick = { onOpenMeal(meal.id) }, onDelete = { toDelete = meal })
            }
        }
    }

    toDelete?.let { meal ->
        ConfirmDialog(
            title = "¿Borrar «${meal.name}»?",
            text = "Se eliminará del diario.",
            confirm = "Borrar",
            onConfirm = { vm.deleteMeal(meal); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
