package org.example

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jobrunr.configuration.JobRunrPro
import org.jobrunr.jobs.filters.DefaultRetryFilter
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory
import org.jobrunr.utils.mapper.JsonMapper
import java.io.OutputStream
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.jobrunr.exposed.transaction.sql.ExposedTransactionAwareConnectionProvider
import org.jobrunr.jobs.Job
import org.jobrunr.jobs.filters.ElectStateFilter
import org.jobrunr.jobs.filters.retry.ExponentialBackoffRetryPolicy
import org.jobrunr.jobs.filters.retry.PerJobRetryPolicy
import org.jobrunr.jobs.mappers.JobMapper
import org.jobrunr.scheduling.JobBuilder
import org.jobrunr.scheduling.JobRequestScheduler
import org.jobrunr.storage.DatabaseOptions
import java.time.temporal.ChronoUnit

fun main() {
    val dataSource = getDataSource()
    val database = Database.connect(dataSource)
    val scheduler = buildJobScheduler(dataSource, System.getenv("JOBRUNR_PRO_LICENSE"))

    val id = UUID.randomUUID()

    println("Scheduling job with param $id")

    transaction(database) {
        scheduler.create(
            JobBuilder.aJob()
                .withJobRequest(TestJobRequest(id))
                .withId(id)
                .withName("TEST_JOB")
                .withAmountOfRetries(1)
                .scheduleAt(Instant.now().plus(2, ChronoUnit.SECONDS))
        ).onFailure(OnFailureJobRequest())
    }

    // waiting for job log
    while (true) { }
}

fun buildJobScheduler(dataSource: DataSource, license: String) : JobRequestScheduler {
    val mapper = getObjectMapper(
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
            findAndRegisterModules()
            setVisibility(
                visibilityChecker
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
            )
            activateDefaultTyping(
                LaissezFaireSubTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            )
            configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    )

    val storageProvider = SqlStorageProviderFactory.using(
        dataSource,
        null,
        ExposedTransactionAwareConnectionProvider(),
        DatabaseOptions(false)
    ).apply {
        setJobMapper(JobMapper(mapper))
    }

    storageProvider.saveLicense(license)

    return JobRunrPro.configure()
        .withJobFilter(DefaultRetryFilter(5, 30))
        .useJobActivator(getJobActivator())
        .useStorageProvider(storageProvider)
        .useBackgroundJobServer()
        .initialize()
        .jobRequestScheduler!!
}

fun getJobActivator() = object : JobActivator {
    override fun <T : Any> activateJob(jobClass: Class<T>): T {
        return try {
            jobClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            throw RuntimeException("Failed to activate job: ${jobClass.name}", e)
        }
    }
}

fun getObjectMapper(objectMapper: ObjectMapper): JsonMapper =
    object : JsonMapper {
        override fun serialize(`object`: Any?) =
            objectMapper.writeValueAsString(`object`)

        override fun serialize(
            outputStream: OutputStream?,
            `object`: Any?
        ) =
            objectMapper.writeValue(outputStream, `object`)

        override fun <T : Any?> deserialize(
            serializedObjectAsString: String?,
            clazz: Class<T>?
        ) =
            objectMapper.readValue(serializedObjectAsString, clazz)
    }


fun getDataSource(): DataSource {
    val config = HikariConfig().apply {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        addDataSourceProperty("user", "test")
        addDataSourceProperty("password", "test")
        addDataSourceProperty("serverName", "localhost")
        addDataSourceProperty("databaseName", "test")
        addDataSourceProperty("portNumber", 5432)
        maximumPoolSize = 10
    }

    return HikariDataSource(config)
}

class TestJobRequest(val id: UUID) : JobRequest {
    override fun getJobRequestHandler(): Class<TestJobRequestHandler> {
        return TestJobRequestHandler::class.java
    }
}


class TestJobRequestHandler : JobRequestHandler<TestJobRequest> {
    override fun run(jobRequest: TestJobRequest) {
        println("Running ${jobContext().currentRetry()}")
        val dataSource = getDataSource()
        val database = Database.connect(dataSource)
        val scheduler = buildJobScheduler(dataSource, System.getenv("JOBRUNR_PRO_LICENSE"))

        val id = UUID.randomUUID()

        transaction(database) {
            scheduler.create(
                JobBuilder.aJob()
                    .withJobRequest(TestJobRequest(id))
                    .withId(id)
                    .withAmountOfRetries(1)
                    .scheduleAt(Instant.now().plus(2, ChronoUnit.SECONDS))
            ).onFailure(OnFailureJobRequest())
        }
    }
}

class OnFailureJobRequest() : JobRequest {
    override fun getJobRequestHandler(): Class<TestJobRequestHandler> {
        return TestJobRequestHandler::class.java
    }
}

class OnFailureJobRequestHandler : JobRequestHandler<OnFailureJobRequest> {
    override fun run(jobRequest: OnFailureJobRequest) {
        println("Some job handling failures")
    }
}
