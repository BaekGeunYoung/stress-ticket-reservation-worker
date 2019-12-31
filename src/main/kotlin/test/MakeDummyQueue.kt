package test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import models.sqs.ReservationInfo
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

fun main() {
    val sqs = SqsClient.builder()
        .region(Region.AP_NORTHEAST_2)
        .build()

    val jsonMapper = jacksonObjectMapper()


    for(i in (0 until 1500)) {
        val reservationInfo = ReservationInfo(event_id = i, user_id = "geunyoung", ticket_num = 1)
        val messageBody = jsonMapper.writeValueAsString(reservationInfo)

        val sendMsgRequest = SendMessageRequest.builder()
            .queueUrl(Constants.sqsEndpoint)
            .messageBody(messageBody)
            .build()

        sqs.sendMessage(sendMsgRequest)
        println("Message sent with id: $i")
    }
}