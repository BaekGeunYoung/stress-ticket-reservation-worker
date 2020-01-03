package test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import models.sqs.EventCommon
import models.sqs.EventDetail
import models.sqs.ReservationInfo
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.LocalDateTime

fun main() {
    val sqs = SqsClient.builder()
        .region(Region.AP_NORTHEAST_2)
        .build()

    val jsonMapper = jacksonObjectMapper()


//    for(i in (0 until 1500)) {
//        val reservationInfo
//                = ReservationInfo(
//            event_name = "RESERVE_TICKET",
//            event_datetime = LocalDateTime.now().toString(),
//            event_common = EventCommon(event_id = i.toString()),
//            event_dic = EventDetail(user_id = "geunyoung", concert_id = 0, ticket_num = 1)
//        )
//        val messageBody = jsonMapper.writeValueAsString(reservationInfo)
//
//        val sendMsgRequest = SendMessageRequest.builder()
//            .queueUrl(Constants.sqsEndpoint)
//            .messageBody(messageBody)
//            .build()
//
//        sqs.sendMessage(sendMsgRequest)
//        println("Message sent with id: $i")
//    }

    for(i in (0 until 10)) {
        val reservationInfo
                = ReservationInfo(
            event_name = "RESERVE_TICKET",
            event_datetime = LocalDateTime.now().toString(),
            event_common = EventCommon(event_id = i.toString()),
            event_dic = EventDetail(user_id = "geunyoung", concert_id = 0, ticket_num = 1)
        )
        val messageBody = jsonMapper.writeValueAsString(reservationInfo)

        val sendMsgRequest = SendMessageRequest.builder()
            .queueUrl(Constants.sqsEndpoint)
            .messageBody(messageBody)
            .build()

        sqs.sendMessage(sendMsgRequest)
        println("Message sent with id: $i")
    }
}