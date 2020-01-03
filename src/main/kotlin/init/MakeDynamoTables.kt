package init

import com.amazonaws.AmazonServiceException
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import models.db.Event
import models.db.FailedReservation
import models.db.Reservation

fun main() {
    val ddbClient: AmazonDynamoDB = AmazonDynamoDBAsyncClientBuilder.standard()
        .withRegion(Regions.AP_NORTHEAST_2)
        .build()

    val ddbMapper = DynamoDBMapper(ddbClient)

    val createEventTableRequest = ddbMapper.generateCreateTableRequest(Event::class.java)
        .withProvisionedThroughput(ProvisionedThroughput(20, 20))

    try {
        ddbClient.createTable(createEventTableRequest)
        println("create event table success")
    } catch (e: AmazonServiceException) {
        println(e.errorMessage)
    }

    val createReservationTableRequest = ddbMapper.generateCreateTableRequest(Reservation::class.java)
        .withProvisionedThroughput(ProvisionedThroughput(20, 20))

    try {
        ddbClient.createTable(createReservationTableRequest)
        println("create reservation table success")
    } catch (e: AmazonServiceException) {
        println("create reservation table success")
        println(e.errorMessage)
    }

    val createFailedReservationRequest = ddbMapper.generateCreateTableRequest(FailedReservation::class.java)
        .withProvisionedThroughput(ProvisionedThroughput(20, 20))

    try {
        ddbClient.createTable(createFailedReservationRequest)
        println("create failed reservation table success")
    } catch (e: AmazonServiceException) {
        println("create failed reservation table fail")
        println(e.errorMessage)
    }
}