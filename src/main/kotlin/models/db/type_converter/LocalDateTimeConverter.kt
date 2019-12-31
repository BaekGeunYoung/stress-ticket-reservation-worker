package models.db.type_converter

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import java.time.LocalDateTime

class LocalDateTimeConverter : DynamoDBTypeConverter<String, LocalDateTime> {
    override fun unconvert(str: String?): LocalDateTime {
        return LocalDateTime.parse(str)
    }

    override fun convert(localDateTime: LocalDateTime?): String {
        return localDateTime.toString()
    }

}