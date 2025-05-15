package com.ai.assistance.operit.ui.features.packages.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.ui.features.packages.components.PackageItem

@Composable
fun AvailablePackagesList(
        packages: Map<String, ToolPackage>,
        onPackageClick: (String) -> Unit,
        onImportClick: (String) -> Unit
) {
    LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(items = packages.entries.toList(), key = { (name, _) -> name }) { (name, pack) ->
            PackageItem(
                    name = name,
                    description = pack.description,
                    isImported = false,
                    onClick = { onPackageClick(name) },
                    onActionClick = { onImportClick(name) }
            )
        }
    }
}

@Composable
fun ImportedPackagesList(
        packages: List<String>,
        availablePackages: Map<String, ToolPackage>,
        onPackageClick: (String) -> Unit,
        onRemoveClick: (String) -> Unit
) {
    LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(items = packages, key = { name -> name }) { name ->
            PackageItem(
                    name = name,
                    description = availablePackages[name]?.description ?: "",
                    isImported = true,
                    onClick = { onPackageClick(name) },
                    onActionClick = { onRemoveClick(name) }
            )
        }
    }
}
