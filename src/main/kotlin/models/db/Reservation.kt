package models.db

import com.amazonaws.services.dynamodbv2.datamodeling.*
import java.time.LocalDateTime

@DynamoDBTable(tableName = "reservation")
data class Reservation(
    @DynamoDBHashKey(attributeName = "reservation_id")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    @DynamoDBAutoGeneratedKey
    var reservation_id: String? = null,

    @DynamoDBAttribute(attributeName = "user_id")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    var user_id: String,

    @DynamoDBAttribute(attributeName = "concert_id")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.N)
    var concert_id: Int,

    @DynamoDBAttribute(attributeName = "reservation_datetime")
    @DynamoDBTyped(DynamoDBMapperFieldModel.DynamoDBAttributeType.S)
    var reservation_datetime: LocalDateTime
) {
    fun toFailedReservation()
            = FailedReservation(
                user_id = user_id,
                reservation_datetime = reservation_datetime,
                concert_id = concert_id
            )
}