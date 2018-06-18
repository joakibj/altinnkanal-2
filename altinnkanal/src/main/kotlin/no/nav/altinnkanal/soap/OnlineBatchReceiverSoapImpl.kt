package no.nav.altinnkanal.soap

import mu.KotlinLogging
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.kv
import no.altinn.webservices.OnlineBatchReceiverSoap
import no.altinn.webservices.ReceiveOnlineBatchExternalAttachment as Request
import no.altinn.webservices.ReceiveOnlineBatchExternalAttachmentResponse as Response
import no.nav.altinnkanal.avro.ExternalAttachment
import no.nav.altinnkanal.services.TopicService
import no.nav.altinnkanal.Metrics
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.XMLEvent
import java.io.StringReader

enum class Status {
    OK, FAILED, FAILED_DO_NOT_RETRY
}

private val log = KotlinLogging.logger { }
private val xmlInputFactory = XMLInputFactory.newFactory()

class OnlineBatchReceiverSoapImpl (
    private val topicService: TopicService,
    private val kafkaProducer: Producer<String, ExternalAttachment>
) : OnlineBatchReceiverSoap {
    override fun receiveOnlineBatchExternalAttachment(params: Request): Response {
        val receiversReference = params.receiversReference
        val sequenceNumber = params.sequenceNumber
        val dataBatch = params.batch ?: params.batch1 ?: throw RuntimeException("Empty batch")

        val response = Response()

        var serviceCode: String? = null
        var serviceEditionCode: String? = null
        var archiveReference: String? = null
        val requestLatency = Metrics.requestTime.startTimer()
        var logDetails: MutableList<StructuredArgument>? = null

        Metrics.requestsTotal.inc()
        try {
            val externalAttachment = toAvroObject(dataBatch).also {
                serviceCode = it.getServiceCode()
                serviceEditionCode = it.getServiceEditionCode()
                archiveReference = it.getArchiveReference()
            }

            logDetails = mutableListOf(kv("SC", serviceCode), kv("SEC", serviceEditionCode),
                kv("recRef", receiversReference), kv("archRef", archiveReference), kv("seqNum", sequenceNumber))

            val topic = topicService.getTopic(serviceCode!!, serviceEditionCode!!)

            if (topic == null) {
                Metrics.requestsFailedMissing.inc()
                logDetails.add(kv("status", Status.FAILED_DO_NOT_RETRY))

                response.receiveOnlineBatchExternalAttachmentResult = receiptResponse(Status.FAILED_DO_NOT_RETRY,
                    archiveReference, logDetails)
                return response
            }

            val metadata = kafkaProducer
                .send(ProducerRecord(topic, externalAttachment))
                .get()

            val latency = requestLatency.observeDuration()
            Metrics.requestSize.observe(metadata.serializedValueSize().toDouble())
            Metrics.requestsSuccess.labels("$serviceCode", "$serviceEditionCode").inc()

            logDetails.addAll(arrayOf(
                kv("latency", "${(latency * 1000).toLong()} ms"),
                kv("size", String.format("%.2f", metadata.serializedValueSize() / 1024f) + " KB"),
                kv("topic", metadata.topic()),
                kv("partition", metadata.partition()),
                kv("offset", metadata.offset()),
                kv("status", Status.OK)
            ))

            response.receiveOnlineBatchExternalAttachmentResult = receiptResponse(Status.OK, archiveReference, logDetails)
            return response
        } catch (e: Exception) {
            Metrics.requestsFailedError.inc()
            logDetails = logDetails ?: mutableListOf(kv("SC", serviceCode), kv("SEC", serviceEditionCode),
                kv("recRef", receiversReference), kv("archRef", archiveReference), kv("seqNum", sequenceNumber))
            logDetails.add(kv("status", Status.FAILED))

            response.receiveOnlineBatchExternalAttachmentResult = receiptResponse(Status.FAILED, archiveReference,
                logDetails, e)
            return response
        }
    }

    private fun toAvroObject(dataBatch: String): ExternalAttachment {
        val xmlReader = xmlInputFactory.createXMLStreamReader(StringReader(dataBatch))
        return try {
            val builder = ExternalAttachment.newBuilder()
            while (xmlReader.hasNext() && (!builder.hasArchiveReference() || !builder.hasServiceCode() || !builder.hasServiceEditionCode())) {
                val eventType = xmlReader.next()
                if (eventType == XMLEvent.START_ELEMENT) {
                    when (xmlReader.localName) {
                        "ServiceCode" -> {
                            builder.serviceCode = xmlReader.elementText
                        }
                        "ServiceEditionCode" -> {
                            builder.serviceEditionCode = xmlReader.elementText
                        }
                        "DataUnit" -> {
                            builder.archiveReference = xmlReader.getAttributeValue(null, "archiveReference")
                        }
                    }
                }
            }
            builder.setBatch(dataBatch).build()
        } finally {
            xmlReader.close()
        }
    }

    private fun receiptResponse(
        resultCode: Status,
        archRef: String?,
        logDetails: MutableList<StructuredArgument>,
        e: Exception? = null
    ): String {
        val (logString, logArray) = logDetails.keyValueLogDetails()
        val message: String = when (resultCode) {
            Status.OK -> {
                log.info("Successfully published ROBEA request to Kafka: $logString", *logArray)
                "Message received OK (archiveReference=$archRef)"
            }
            Status.FAILED_DO_NOT_RETRY -> {
                log.warn("Denied ROBEA request due to missing/unknown codes: $logString", *logArray)
                "Invalid combination of Service Code and Service Edition Code (archiveReference=$archRef)"
            }
            Status.FAILED -> {
                log.error("Failed to send a ROBEA request to Kafka: $logString", *logArray, e)
                "An error occurred: ${e?.message} (archiveReference=$archRef)"
            }
        }
        return "&lt;OnlineBatchReceipt&gt;&lt;Result resultCode=&quot;$resultCode&quot;&gt;$message&lt;/Result&gt;&lt;/OnlineBatchReceipt&gt;"
    }

    private fun MutableList<StructuredArgument>.keyValueLogDetails(): Pair<String, Array<StructuredArgument>> {
        val first = (0..(this.size - 1)).joinToString(", ", "", "") { "{}" }
        val second = this.toTypedArray()
        return Pair(first, second)
    }
}
