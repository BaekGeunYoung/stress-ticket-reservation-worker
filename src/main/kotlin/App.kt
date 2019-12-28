import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient

fun main() {
    runBlocking {
        //SQS client 생성
        val sqs = SqsAsyncClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build()

        //Worker (SQS Consumer) 구동
        val consumer = SqsConsumer(sqs)
        consumer.start()
    }
}