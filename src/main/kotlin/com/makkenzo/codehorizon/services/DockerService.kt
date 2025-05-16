package com.makkenzo.codehorizon.services

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.makkenzo.codehorizon.dtos.DockerExecutionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


@Service
class DockerService {
    private val logger = LoggerFactory.getLogger(DockerService::class.java)
    private val dockerClient: DockerClient

    private val pythonImageName = "codehorizon-python-runner:latest"
    private val baseTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "codehorizon_runner")

    private val maxConcurrentContainers = 5
    private val containerExecutionPermits = Semaphore(maxConcurrentContainers)

    init {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()
        dockerClient = DockerClientImpl.getInstance(config, httpClient)

        if (!Files.exists(baseTempDir)) {
            Files.createDirectories(baseTempDir)
        }

        buildPythonImageIfNotExists()
    }

    private fun buildPythonImageIfNotExists() {
        try {
            logger.info("--- Начало проверки/сборки образа $pythonImageName ---")
            val allImages = dockerClient.listImagesCmd().exec()
            var imageFound = false

            logger.info("Список всех образов, видимых Docker-клиентом (${allImages.size} шт.):")
            allImages.forEach { image ->
                val tags = image.repoTags?.joinToString(", ") ?: "Без тегов"
                logger.info(" - Теги: $tags, ID: ${image.id.take(12)}")
                if (image.repoTags != null && image.repoTags.contains(pythonImageName)) {
                    imageFound = true
                }
            }

            if (imageFound) {
                logger.info("Образ $pythonImageName НАЙДЕН в общем списке.")
            } else {
                logger.info("Образ $pythonImageName НЕ НАЙДЕН в общем списке. Начинаю сборку...")

                val dockerfilePath = Paths.get("docker/python-runner.Dockerfile").toAbsolutePath().parent.toString()
                val dockerfile = Paths.get(dockerfilePath, "python-runner.Dockerfile").toFile()

                if (!dockerfile.exists()) {
                    logger.error("Dockerfile не найден по пути: ${dockerfile.absolutePath}")
                    throw IllegalStateException("Dockerfile not found for Python runner.")
                }

                val buildCallbackLog = object : BuildImageResultCallback() {
                    override fun onNext(item: BuildResponseItem?) {
                        item?.stream?.let { logger.info("Build log: $it") }
                        item?.errorDetail?.let { logger.error("Build error detail: ${it.message}") }
                        super.onNext(item)
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.error("Build onError: ", throwable)
                        super.onError(throwable)
                    }
                }
                dockerClient.buildImageCmd(dockerfile)
                    .withTags(setOf(pythonImageName))
                    .exec(buildCallbackLog)
                    .awaitCompletion()

                logger.info("Сборка образа $pythonImageName должна была завершиться.")

                val imagesAfterBuild = dockerClient.listImagesCmd().exec()
                val stillNotFound =
                    imagesAfterBuild.none { it.repoTags != null && it.repoTags.contains(pythonImageName) }
                if (stillNotFound) {
                    logger.error("!!! Образ $pythonImageName не появился в списке даже после попытки сборки !!!")
                } else {
                    logger.info("Образ $pythonImageName успешно собран и теперь виден.")
                }
            }
            logger.info("--- Конец проверки/сборки образа $pythonImageName ---")
        } catch (e: Exception) {
            logger.error("Критическая ошибка при проверке или сборке Docker-образа $pythonImageName: ${e.message}", e)
            throw IllegalStateException("Failed to ensure Python runner Docker image is available", e)
        }
    }

    fun executePythonCode(
        studentCode: String,
        testRunnerScript: String,
        testCasesJsonContent: String,
        timeoutSeconds: Long = 10,
        memoryLimitMb: Long = 128
    ): DockerExecutionResult {
        var permitAcquired = false
        try {
            if (!containerExecutionPermits.tryAcquire(timeoutSeconds + 5, TimeUnit.SECONDS)) {
                logger.warn("Не удалось получить разрешение на запуск Docker-контейнера в течение ${timeoutSeconds + 5}с. Очередь переполнена или Docker занят.")
                return DockerExecutionResult(
                    "", "", -1, 0,
                    "Execution permit not acquired; server busy. Please try again later."
                )
            }

            permitAcquired = true
            logger.debug("Разрешение на запуск Docker-контейнера получено. Осталось: ${containerExecutionPermits.availablePermits()}")

            val runId = UUID.randomUUID().toString()
            val tempRunDir: Path = Files.createDirectory(baseTempDir.resolve(runId))
            val hostPath = tempRunDir.toAbsolutePath().toString()
            val containerPath = "/usr/src/app/run_dir"

            try {
                Files.writeString(
                    tempRunDir.resolve("student_code.py"),
                    studentCode,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
                Files.writeString(
                    tempRunDir.resolve("run_tests.py"),
                    testRunnerScript,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
                Files.writeString(
                    tempRunDir.resolve("test_data.json"),
                    testCasesJsonContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )

                val hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryLimitMb * 1024 * 1024)
                    .withCpuQuota(50000)
                    .withPrivileged(false)
                    .withNetworkMode("none")
                    .withBinds(Bind.parse("$hostPath:$containerPath:ro"))
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(listOf("no-new-privileges"))

                val containerResponse: CreateContainerResponse = dockerClient.createContainerCmd(pythonImageName)
                    .withHostConfig(hostConfig)
                    .withWorkingDir(containerPath)
                    .withCmd("python", "run_tests.py")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withUser("appuser")
                    .withEnv("PYTHONUNBUFFERED=1")
                    .exec()
                val containerId = containerResponse.id

                val startTime = System.currentTimeMillis()
                dockerClient.startContainerCmd(containerId).exec()

                val statusCode = dockerClient.waitContainerCmd(containerId)
                    .start()
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS)

                val executionTimeMs = System.currentTimeMillis() - startTime

                val logCallback = LogContainerCallback()
                dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTimestamps(false)
                    .exec(logCallback)
                logCallback.awaitCompletion(5, TimeUnit.SECONDS)

                dockerClient.removeContainerCmd(containerId).withForce(true).exec()

                return DockerExecutionResult(
                    stdout = logCallback.stdout.toString(),
                    stderr = logCallback.stderr.toString(),
                    exitCode = statusCode ?: -1,
                    executionTimeMs = executionTimeMs,
                    error = if (statusCode == null) "Execution timed out after $timeoutSeconds seconds." else null
                )
            } catch (e: Exception) {
                logger.error("Ошибка при выполнении кода в Docker для $runId: ${e.message}", e)
                return DockerExecutionResult("", "", -1, 0, "Docker execution failed: ${e.message}")
            } finally {
                try {
                    Files.walk(tempRunDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete)
                } catch (ioe: Exception) {
                    logger.warn("Не удалось полностью удалить временную директорию $tempRunDir: ${ioe.message}")
                }
            }
        } catch (e: InterruptedException) {
            logger.warn("Ожидание разрешения на запуск Docker-контейнера было прервано.", e)
            Thread.currentThread().interrupt()
            return DockerExecutionResult("", "", -1, 0, "Container execution permit interrupted.")
        } catch (e: Exception) {
            logger.error("Ошибка при выполнении кода в Docker: ${e.message}", e)
            return DockerExecutionResult("", "", -1, 0, "Docker execution failed: ${e.message}")
        } finally {
            if (permitAcquired) {
                containerExecutionPermits.release()
                logger.debug("Разрешение на запуск Docker-контейнера освобождено. Осталось: ${containerExecutionPermits.availablePermits()}")
            }
        }
    }
}

class LogContainerCallback : ResultCallback<Frame> {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    private var completed = false
    private val lock = Object()

    override fun onStart(closeable: Closeable?) {}

    override fun onNext(frame: Frame?) {
        frame?.let {
            when (it.streamType) {
                com.github.dockerjava.api.model.StreamType.STDOUT,
                com.github.dockerjava.api.model.StreamType.RAW -> stdout.append(
                    String(
                        it.payload,
                        StandardCharsets.UTF_8
                    )
                )

                com.github.dockerjava.api.model.StreamType.STDERR -> stderr.append(
                    String(
                        it.payload,
                        StandardCharsets.UTF_8
                    )
                )

                else -> {}
            }
        }
    }

    override fun onError(throwable: Throwable?) {
        System.err.println("Error in LogContainerCallback: ${throwable?.message}")
        onComplete()
    }

    override fun onComplete() {
        synchronized(lock) {
            completed = true
            lock.notifyAll()
        }
    }

    override fun close() {}

    fun awaitCompletion(timeout: Long, unit: TimeUnit): Boolean {
        synchronized(lock) {
            if (!completed) {
                try {
                    lock.wait(unit.toMillis(timeout))
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return completed
        }
    }
}