package com.example.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.entity.ScreenshotWithEntities
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import com.example.ui.theme.StatusSuccess

@Composable
fun ScreenshotCard(
    screenshotWithEntities: ScreenshotWithEntities,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenshot = screenshotWithEntities.screenshot
    val entities = screenshotWithEntities.entities
    val category = Category.fromString(screenshot.category)

    // Select the 2-3 most useful extracted pieces as instructed in PRD Section 13
    val dateEntity = entities.find { it.type == EntityType.DATE.name }
    val timeEntity = entities.find { it.type == EntityType.TIME.name }
    val priceEntity = entities.find { it.type == EntityType.PRICE.name }
    val locationEntity = entities.find { it.type == EntityType.LOCATION.name }
    val flightEntity = entities.find { it.type == EntityType.FLIGHT.name }
    val productEntity = entities.find { it.type == EntityType.PRODUCT.name }

    val primaryAction = screenshotWithEntities.actions.firstOrNull()
    val isActionCreated = primaryAction?.status == "CREATED" || primaryAction?.status == "COMPLETED"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("screenshot_card_${screenshot.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail container
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (screenshot.imageUri.startsWith("sample://")) {
                        // Styled persona category graphic
                        Text(
                            text = category.icon,
                            fontSize = 32.sp
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(screenshot.imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = screenshot.title,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Details Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Category icon & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.icon,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = screenshot.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Extracted items (2-3 items)
                    val line1 = when (category) {
                        Category.TRAVEL -> locationEntity?.value ?: flightEntity?.value ?: ""
                        Category.RECEIPT -> listOfNotNull(productEntity?.value, priceEntity?.value).joinToString(" • ")
                        Category.PRODUCT -> listOfNotNull(productEntity?.value, priceEntity?.value).joinToString(" • ")
                        Category.EVENT -> locationEntity?.value ?: ""
                        else -> entities.firstOrNull { it.type != EntityType.DATE.name }?.value ?: ""
                    }

                    if (line1.isNotBlank()) {
                        Text(
                            text = line1,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val dateLine = listOfNotNull(dateEntity?.value, timeEntity?.value).joinToString(" • ")
                    if (dateLine.isNotBlank()) {
                        Text(
                            text = dateLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Context-dependent action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status badge
                if (isActionCreated) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Action Created",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Needs Review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action button
                val buttonText = when (category) {
                    Category.TRAVEL -> if (isActionCreated) "View Reminder" else "Add Reminder"
                    Category.EVENT, Category.STUDY, Category.MESSAGE -> if (isActionCreated) "View Reminder" else "Add Reminder"
                    Category.RECEIPT -> "Save Receipt"
                    Category.PRODUCT -> "Save Product"
                    else -> "View Details"
                }

                Button(
                    onClick = onActionClick,
                    modifier = Modifier.testTag("action_button_${screenshot.id}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActionCreated) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isActionCreated) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                }
            }
        }
    }
}
