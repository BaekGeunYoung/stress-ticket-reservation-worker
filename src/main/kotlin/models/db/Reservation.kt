package models.db

import com.amazonaws.services.dynamodbv2.datamodeling.*
import java.time.LocalDateTime

@DynamoDBTable(tableName = "reservation")
data class Reservation(
    @DynamoDBHashKey(attributeName = "reservation_id")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    val reservation_id: String? = null,

    @DynamoDBAttribute(attributeName = "user_id")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    val user_id: String,

    @DynamoDBAttribute(attributeName = "reservation_datetime")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    val reservation_datetime: LocalDateTime
)