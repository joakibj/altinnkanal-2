package no.nav.altinnkanal

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.altinn.webservices.ReceiveOnlineBatchExternalAttachment as ROBEA
import java.util.concurrent.Future
import no.nav.altinnkanal.avro.ExternalAttachment
import no.nav.altinnkanal.services.TopicService
import no.nav.altinnkanal.soap.OnlineBatchReceiverSoapImpl
import no.nav.altinnkanal.soap.Status
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.RecordMetadata
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.data_driven.data
import org.jetbrains.spek.data_driven.on

object OnlineBatchReceiverSoapSpec : Spek({
    val topicService = mock<TopicService>()
    val kafkaProducer = mock<Producer<String, ExternalAttachment>>()
    val metadataFuture = mock<Future<RecordMetadata>>()
    val soapService = OnlineBatchReceiverSoapImpl(topicService, kafkaProducer)
    val simpleBatch = "/data/basic_data_batch.xml".getResource()

    whenever(metadataFuture.get()).thenReturn(mock())
    whenever(kafkaProducer.send(any())).thenReturn(metadataFuture)

    describe("receiveOnlineBatchExternalAttachment") {

        on("%s",
            data<String, String?, String>("valid topic routing", "test", expected = Status.OK.name),
            data<String, String?, String>("missing topic routing", null, expected = Status.FAILED_DO_NOT_RETRY.name)
        ) { _, mockValue: String?, expected: String ->

            whenever(topicService.getTopic(any(), any())).thenReturn(mockValue)

            it("should return $expected for batch") {
                val result = soapService.receiveOnlineBatchExternalAttachment(
                    ROBEA().apply {
                        sequenceNumber = 0
                        batch = simpleBatch
                }).receiveOnlineBatchExternalAttachmentResult.getResultCode()

                result shouldEqual expected
            }

            it("should return $expected for Batch") {
                val result = soapService.receiveOnlineBatchExternalAttachment(
                    ROBEA().apply {
                        sequenceNumber = 0
                        batch1 = simpleBatch
                }).receiveOnlineBatchExternalAttachmentResult.getResultCode()

                result shouldEqual expected
            }
        }
    }
})
