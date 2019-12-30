import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
import models.db.Event
import models.sqs.ReservationInfo
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.lang.Thread.currentThread
import kotlin.coroutines.CoroutineContext

class SqsConsumer (private val sqs: SqsAsyncClient): CoroutineScope {
    /**
     * coroutine context 정의 :
     * DB IO가 주된 작업이 될 것이므로 IO dispatcher 사용.
     * coroutine의 exception 전파 방향이 아래로만 이루어지도록 supervisorJob 사용.
     */
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + supervisorJob

    /**
     * json string으로 넘어오는 queue message를 파싱하기 위한 object mapper.
     */
    private val jsonMapper = jacksonObjectMapper()

    /**
     * dynamoDB client
     */
    private val ddbClient: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withRegion(Regions.AP_NORTHEAST_2)
        .build()

    /**
     * dynamoDB mapper
     */
    private val ddbMapper = DynamoDBMapper(ddbClient)

    fun start() = runBlocking {
        val messageChannel = Channel<Message>()
        repeat(Constants.numOfProcessors) {
            launchProcessor(messageChannel)
        }
        launchMsgReceiver(messageChannel)
    }

    //MsgReceiver : SQS로 주기적으로 polling하여 큐 메세지를 받아오고, channel을 통해 processor로 큐 메세지를 넘긴다.
    private fun CoroutineScope.launchMsgReceiver(channel: SendChannel<Message>) = launch {
        repeatUntilCancelled {
            val receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(Constants.sqsEndpoint)
                .waitTimeSeconds(20)
                .maxNumberOfMessages(10)
                .build()

            val messages = sqs.receiveMessage(receiveRequest).await().messages()
            println("${currentThread().name} Retrieved ${messages.size} messages")

            messages.forEach {
                channel.send(it)
            }
        }
    }

    //큐메세지를 polling하는 작업을 무한히 반복할 수 있도록 하는 coroutine.
    private suspend fun CoroutineScope.repeatUntilCancelled(suspendFunc: suspend() -> Unit) {
        while(isActive) {
            try {
                suspendFunc()
                yield()
            } catch (ex: CancellationException) {
                println("coroutine on ${currentThread().name} cancelled")
            } catch (ex: Exception) {
                println("${currentThread().name} failed with {$ex}. Retrying...")
                ex.printStackTrace()
            }
        }

        println("coroutine on ${currentThread().name} exiting")
    }

    //Processor : 큐 메세지를 받아 실제 작업을 처리.
    private fun CoroutineScope.launchProcessor(channel: ReceiveChannel<Message>) = launch {
        repeatUntilCancelled {
            for (msg in channel) {
                try {
                    processMsg(msg)
                    deleteMessage(msg)
                } catch (ex: Exception) {
                    println("${currentThread().name} exception trying to process message ${msg.body()}")
                    ex.printStackTrace()
                    changeVisibility(msg)
                }
            }
        }
    }

    //큐 메세지를 처리하는 예시 코드
    //dynamoDB에 데이터 저장
    private fun processMsg(message: Message) {
        //mapper를 이용해 queue message를 ReservationInfo 객체로 parsing 한다.
        val reservationInfo: ReservationInfo = jsonMapper.readValue(message.body())

    }

    private suspend fun deleteMessage(message: Message) {
        sqs.deleteMessage { req ->
            req.queueUrl(Constants.sqsEndpoint)
            req.receiptHandle(message.receiptHandle())
        }.await()
        println("${currentThread().name} Message deleted: ${message.body()}")
    }

    private suspend fun changeVisibility(message: Message) {
        sqs.changeMessageVisibility { req ->
            req.queueUrl(Constants.sqsEndpoint)
            req.receiptHandle(message.receiptHandle())
            req.visibilityTimeout(10)
        }.await()
        println("${currentThread().name} Changed visibility of message: ${message.body()}")
    }
}