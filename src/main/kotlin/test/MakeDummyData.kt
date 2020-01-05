package test

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import models.db.Event
import java.time.LocalDateTime

fun main() {
    val ddbClient: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withRegion(Regions.AP_NORTHEAST_2)
        .build()

    val ddbMapper = DynamoDBMapper(ddbClient)

    for(i in (0 until 1500)) {
        val loginEvent = Event(
            event_id = i.toString(),
            event_name = Constants.LOGIN,
            lastLoginDatetime = LocalDateTime.parse("2020-01-03T10:00:00.000")
        )
        val pageViewEvent = Event(
            event_id = i.toString(),
            event_name = Constants.PAGE_VIEW,
            lastPageViewDatetime = LocalDateTime.parse("2020-01-03T11:00:00.000"),
            pageViewConcertId = 0
        )

        ddbMapper.save(loginEvent)
        ddbMapper.save(pageViewEvent)
        println("saved id : $i")
    }
}