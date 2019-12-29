package models.db

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import java.time.LocalDateTime

@DynamoDBTable(tableName = "event")
data class Event(
    @DynamoDBHashKey(attributeName = "event_id")
    val event_id: String,

    @DynamoDBRangeKey(attributeName = "event_name")
    val event_name: String,

    @DynamoDBAttribute(attributeName = "last_login_datetime")
    val lastLoginDatetime: LocalDateTime? = null,

    @DynamoDBAttribute(attributeName = "last_page_view_datetime")
    val lastPageViewDatetime: LocalDateTime? = null,

    @DynamoDBAttribute(attributeName = "last_reservation_datetime")
    val lastReservationDatetime: LocalDateTime? = null
)