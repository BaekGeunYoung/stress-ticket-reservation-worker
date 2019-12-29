package models.db

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import java.time.LocalDateTime

@DynamoDBTable(tableName = "event")
data class Reservation(
    @DynamoDBHashKey(attributeName = "reservation_id")
    val reservation_id: String? = null,

    @DynamoDBAttribute(attributeName = "concert_id")
    val concert_id: String,

    @DynamoDBAttribute(attributeName = "user_id")
    val user_id: String,

    @DynamoDBAttribute(attributeName = "reservation_datetime")
    val reservation_datetime: LocalDateTime
)