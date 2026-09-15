package ai.rever.boss.plugin.dynamic.connectionskills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionsSkillsContent(registry: ConnectionRegistry) {
    val statuses by registry.statuses.collectAsState()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connections & Skills", style = MaterialTheme.typography.h5)
                Text(
                    "Governed external-service connections and version-pinned agent skills.",
                    style = MaterialTheme.typography.body1,
                )
            }
        }

        items(statuses) { status ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(status.provider.displayName, style = MaterialTheme.typography.h6)
                    Text("Dependency: ${status.provider.executable}")
                    Text("State: ${status.state.name.lowercase()}")
                    Text(status.message)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status.state == ConnectionState.CONNECTED) {
                            Button(onClick = { registry.disconnect(status.provider) }) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(onClick = { registry.connect(status.provider) }) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}
